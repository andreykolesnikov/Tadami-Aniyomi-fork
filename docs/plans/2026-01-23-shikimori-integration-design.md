# Shikimori Integration для Aurora Anime Cards - Design Document

**Date:** 2026-01-23
**Author:** Design Session
**Status:** Approved

---

## 1. Обзор

### 1.1 Цель

Интегрировать данные из Shikimori.one для замены заглушек (N/A) в Aurora anime cards:
- **Рейтинг** (score) - например "8.5"
- **Тип** (kind) - TV, Movie, OVA, Special, etc.
- **Постер** (cover) - высококачественные изображения из Shikimori

### 1.2 Проблема

Текущие Aurora anime cards показывают N/A вместо рейтинга и типа, что снижает информативность UI.

### 1.3 Решение

Комплексная интеграция с Shikimori API с:
- Гибридной стратегией поиска (tracking → автопоиск → manual)
- Offline-first кешированием (7 дней TTL)
- Полным контролем пользователя (2 независимых toggle)
- Graceful degradation при ошибках

---

## 2. Архитектура

### 2.1 Общая схема

```
┌─────────────────────────────────────────────────────────┐
│                   Aurora Anime Screen                    │
│  (отображает рейтинг, тип, постер из Shikimori)         │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│              GetShikimoriMetadata (Interactor)           │
│  • Координирует получение данных                         │
│  • Проверяет настройки (toggle)                         │
│  • Гибридная стратегия (tracking → автопоиск)           │
└───┬──────────────────────┬──────────────────────────────┘
    │                      │
    ▼                      ▼
┌─────────────────┐  ┌──────────────────────────────────┐
│ Tracking Store  │  │  ShikimoriMetadataCache (DB)     │
│ (существующий)  │  │  • Кеширование данных            │
│                 │  │  • TTL: 7 дней                   │
└─────────────────┘  └──────────────────────────────────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │ ShikimoriApi     │
                     │ (существующий)   │
                     └──────────────────┘
```

### 2.2 Поток данных

```
1. User opens anime screen
2. AnimeScreenModel.loadShikimoriMetadata()
3. GetShikimoriMetadata.await(anime):
   a. Check settings (if disabled → return null)
   b. Check cache (if fresh → return cached)
   c. Check tracking (if exists → fetch from API)
   d. Auto-search by title (first result)
   e. Cache result (even "not found")
4. Update UI state with data/error/loading
5. User can retry or manual search
```

---

## 3. Компоненты

### 3.1 Database Schema

**Новая таблица:** `shikimori_metadata_cache`

```sql
CREATE TABLE shikimori_metadata_cache (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    anime_id INTEGER NOT NULL UNIQUE,
    shikimori_id INTEGER,              -- null = не найдено
    score REAL,                        -- рейтинг (8.5)
    kind TEXT,                         -- тип: tv, movie, ova, etc.
    cover_url TEXT,                    -- URL постера
    search_query TEXT NOT NULL,        -- запрос для поиска
    updated_at INTEGER NOT NULL,       -- timestamp
    is_manual_match INTEGER DEFAULT 0, -- 1 = ручной выбор
    FOREIGN KEY(anime_id) REFERENCES animes(_id) ON DELETE CASCADE
);

CREATE INDEX idx_shikimori_cache_anime_id ON shikimori_metadata_cache(anime_id);
CREATE INDEX idx_shikimori_cache_updated_at ON shikimori_metadata_cache(updated_at);
```

**Ключевые особенности:**
- `anime_id UNIQUE` - один кеш на аниме
- `shikimori_id` nullable - сохраняем "не найдено"
- `is_manual_match` - защита от перезаписи ручного выбора
- `updated_at` - для TTL проверки

### 3.2 Domain Models

```kotlin
// domain/shikimori/model/ShikimoriMetadata.kt
data class ShikimoriMetadata(
    val animeId: Long,
    val shikimoriId: Long?,
    val score: Double?,
    val kind: String?,
    val coverUrl: String?,
    val searchQuery: String,
    val updatedAt: Long,
    val isManualMatch: Boolean = false,
) {
    fun isStale(currentTime: Long = System.currentTimeMillis()): Boolean {
        val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L
        return currentTime - updatedAt > sevenDaysInMillis
    }

    fun hasData(): Boolean = score != null || kind != null || coverUrl != null
}

// ui/entries/anime/AnimeScreenModel.kt
sealed interface ShikimoriError {
    object NetworkError : ShikimoriError
    object NotFound : ShikimoriError
    object Disabled : ShikimoriError
}
```

### 3.3 Repository Layer

**GetShikimoriMetadata (Interactor):**

```kotlin
class GetShikimoriMetadata(
    private val metadataCache: ShikimoriMetadataCache,
    private val shikimoriApi: ShikimoriApi,
    private val trackingRepository: TrackingRepository,
    private val preferences: UiPreferences,
) {
    suspend fun await(anime: Anime): ShikimoriMetadata? {
        // 1. Check settings
        if (!preferences.useShikimoriRating().get() &&
            !preferences.useShikimoriCovers().get()) {
            return null
        }

        // 2. Check cache
        val cached = metadataCache.get(anime.id)
        if (cached != null && !cached.isStale()) {
            return cached
        }

        // 3. Try tracking
        val fromTracking = getFromTracking(anime)
        if (fromTracking != null) {
            metadataCache.upsert(fromTracking)
            return fromTracking
        }

        // 4. Auto-search
        val fromSearch = searchAndCache(anime)
        if (fromSearch != null) {
            return fromSearch
        }

        // 5. Cache "not found"
        cacheNotFound(anime)
        return null
    }
}
```

**Стратегия поиска:**
1. **Tracking** - если пользователь добавил в Shikimori → используем tracking ID
2. **Cache** - если данные свежие (< 7 дней) → возвращаем из кеша
3. **Auto-search** - ищем по названию (`anime.title`), берём первый результат
4. **Not found** - кешируем отсутствие данных (избегаем повторных запросов)

### 3.4 UI State Management

**AnimeScreenModel.State расширение:**

```kotlin
data class Success(
    val anime: Anime,
    // ... existing fields

    // Shikimori integration
    val shikimoriMetadata: ShikimoriMetadata? = null,
    val isShikimoriLoading: Boolean = false,
    val shikimoriError: ShikimoriError? = null,
) : State
```

**Методы:**
- `loadShikimoriMetadata()` - загрузка при открытии экрана
- `retryShikimoriLoad()` - повторная попытка при ошибке
- `openShikimoriSearch()` - ручной поиск (v2)

---

## 4. Поведение при ошибках

### 4.1 Сценарий 1: API недоступен (нет интернета)

**Решение: Показывать cached данные + индикатор**

```
┌──────────────┐
│  8.5 ⚠       │  ← Cached рейтинг + warning icon
│  РЕЙТИНГ     │
└──────────────┘
```

- Если в кеше есть данные → показываем
- Добавляем иконку "⚠" для индикации устаревших данных
- Кнопка retry (⟳) для повторной попытки

### 4.2 Сценарий 2: Аниме не найдено

**Решение: N/A + кнопка manual search**

```
┌──────────────┐
│  N/A 🔍      │  ← Кнопка поиска
│  РЕЙТИНГ     │
└──────────────┘
```

- Показываем N/A
- Добавляем иконку search (🔍)
- При клике → открывается Shikimori search dialog
- Пользователь выбирает правильное аниме вручную

### 4.3 Сценарий 3: Toggle выключен

**Решение: Два независимых toggle**

```
Settings:
☑ Показывать рейтинг и тип из Shikimori
☑ Использовать постеры Shikimori
```

- Toggle 1: рейтинг + тип
- Toggle 2: постеры
- Независимые друг от друга
- При выключении → показываем N/A / оригинальные постеры

---

## 5. Интеграция постеров

### 5.1 Extension функция

```kotlin
fun Anime.getCoverUrl(
    shikimoriMetadata: ShikimoriMetadata?,
    useShikimoriCovers: Boolean,
): String {
    return when {
        !useShikimoriCovers -> thumbnailUrl
        shikimoriMetadata?.coverUrl != null -> shikimoriMetadata.coverUrl
        else -> thumbnailUrl
    }
}

@Composable
fun Anime.getDisplayCoverUrl(
    shikimoriMetadata: ShikimoriMetadata?,
): String {
    val preferences = remember { Injekt.get<UiPreferences>() }
    val useShikimoriCovers by preferences.useShikimoriCovers().collectAsState()
    return getCoverUrl(shikimoriMetadata, useShikimoriCovers)
}
```

### 5.2 Места применения

**Глобальная замена:**
- ✅ `FullscreenPosterBackground` (Aurora anime screen)
- ✅ Library grid items
- ✅ Library list items
- ✅ Episode card thumbnails
- ✅ Search results
- ✅ Cover dialog

**Паттерн замены:**

```kotlin
// Вместо:
AsyncImage(model = anime.thumbnailUrl, ...)

// Используем:
val coverUrl = anime.getDisplayCoverUrl(shikimoriMetadata)
AsyncImage(model = coverUrl, ...)
```

---

## 6. UI Components

### 6.1 AnimeInfoCard обновление

**Новые параметры:**

```kotlin
@Composable
fun AnimeInfoCard(
    // ... existing params

    shikimoriMetadata: ShikimoriMetadata? = null,
    isShikimoriLoading: Boolean = false,
    shikimoriError: ShikimoriError? = null,
    onRetryShikimori: () -> Unit = {},
    onSearchShikimori: () -> Unit = {},
)
```

**Отображение рейтинга:**

```kotlin
StatItem(
    value = when {
        !useShikimoriRating -> "N/A"
        isShikimoriLoading -> "..."
        shikimoriMetadata?.score != null -> String.format("%.1f", shikimoriMetadata.score)
        shikimoriError is NetworkError -> "N/A ⚠"
        shikimoriError is NotFound -> "N/A"
        else -> "N/A"
    },
    label = "РЕЙТИНГ",
    isStale = shikimoriError is NetworkError,
    onRetry = if (shikimoriError is NetworkError) onRetryShikimori else null,
    onSearch = if (shikimoriError is NotFound) onSearchShikimori else null,
)
```

### 6.2 StatItem обновление

**Добавлены индикаторы:**

```kotlin
@Composable
private fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    isStale: Boolean = false,      // Устаревшие данные
    onRetry: (() -> Unit)? = null, // Retry при ошибке
    onSearch: (() -> Unit)? = null, // Manual search
) {
    // Value + icons (⚠, ⟳, 🔍)
}
```

### 6.3 FullscreenPosterBackground

**Поддержка Shikimori постеров:**

```kotlin
@Composable
fun FullscreenPosterBackground(
    anime: Anime,
    scrollOffset: Int,
    firstVisibleItemIndex: Int,
    shikimoriMetadata: ShikimoriMetadata? = null,
    modifier: Modifier = Modifier,
) {
    val coverUrl = anime.getDisplayCoverUrl(shikimoriMetadata)

    // Layer 1: Thumbnail (fast)
    AsyncImage(model = anime.thumbnailUrl, ...)

    // Layer 2: Full quality (Shikimori or original)
    AsyncImage(model = coverUrl, ...)
}
```

---

## 7. Настройки

### 7.1 UI Preferences

```kotlin
// domain/ui/UiPreferences.kt

fun useShikimoriRating(): Preference<Boolean> =
    preferenceStore.getBoolean("use_shikimori_rating", defaultValue = true)

fun useShikimoriCovers(): Preference<Boolean> =
    preferenceStore.getBoolean("use_shikimori_covers", defaultValue = true)
```

### 7.2 Settings Screen

**Новая секция "Shikimori интеграция":**

```kotlin
PreferenceGroup(title = "Shikimori интеграция") {
    SwitchPreference(
        title = "Показывать рейтинг и тип из Shikimori",
        subtitle = "Отображать рейтинг и тип аниме из базы данных Shikimori",
        checked = useShikimoriRating,
        onCheckedChange = { preferences.useShikimoriRating().set(it) },
    )

    SwitchPreference(
        title = "Использовать постеры Shikimori",
        subtitle = "Заменять постеры аниме на версии из Shikimori",
        checked = useShikimoriCovers,
        onCheckedChange = { preferences.useShikimoriCovers().set(it) },
    )

    TextPreference(
        title = "Очистить кеш Shikimori",
        subtitle = "Удалить все сохранённые данные из Shikimori",
        onClick = { shikimoriMetadataCache.clearAll() },
    )

    InfoPreference(
        "Данные кешируются на 7 дней. При отсутствии tracking автоматически ищется аниме по названию."
    )
}
```

### 7.3 Строковые ресурсы

**Добавить в `i18n-aniyomi/src/commonMain/resources/MR/base/strings.xml`:**

```xml
<string name="pref_category_shikimori">Shikimori интеграция</string>
<string name="pref_shikimori_rating_title">Показывать рейтинг и тип из Shikimori</string>
<string name="pref_shikimori_rating_subtitle">Отображать рейтинг и тип аниме из базы данных Shikimori</string>
<string name="pref_shikimori_covers_title">Использовать постеры Shikimori</string>
<string name="pref_shikimori_covers_subtitle">Заменять постеры аниме на версии из Shikimori</string>
<string name="pref_shikimori_clear_cache">Очистить кеш Shikimori</string>
<string name="pref_shikimori_clear_cache_subtitle">Удалить все сохранённые данные из Shikimori</string>
<string name="pref_shikimori_info">Данные кешируются на 7 дней. При отсутствии tracking автоматически ищется аниме по названию.</string>
```

---

## 8. Производительность

### 8.1 Кеширование

**TTL (Time To Live):** 7 дней
- Баланс между свежестью и запросами к API
- Автоматическая очистка устаревших записей (> 30 дней)

**Стратегия кеша:**
- Кешируем успешные результаты (7 дней)
- Кешируем "не найдено" (7 дней) - избегаем повторных поисков
- НЕ кешируем сетевые ошибки

### 8.2 Parallel Loading

```kotlin
// AnimeScreenModel.kt
private fun observeAnime(animeId: Long) {
    // Existing anime data loading

    // Shikimori data loading in parallel
    coroutineScope.launchIO {
        loadShikimoriMetadata(animeId)
    }
}
```

- Shikimori данные загружаются параллельно с основными
- Не блокируют отображение экрана
- UI показывает loading indicator пока данные загружаются

### 8.3 Database Indexes

```sql
CREATE INDEX idx_shikimori_cache_anime_id ON shikimori_metadata_cache(anime_id);
CREATE INDEX idx_shikimori_cache_updated_at ON shikimori_metadata_cache(updated_at);
```

- Быстрый поиск по `anime_id` (основной query)
- Быстрая очистка устаревших записей

---

## 9. Безопасность и Privacy

### 9.1 API Credentials

**Используем существующие:**
- Client ID и Secret уже в `ShikimoriApi.kt`
- OAuth flow уже реализован
- Токены хранятся безопасно

### 9.2 User Data

**Что храним:**
- Кеш metadata (только публичные данные из Shikimori)
- Preferences (toggle состояния)

**Что НЕ храним:**
- Личные данные пользователя
- История поиска
- Токены (уже в существующем tracking)

### 9.3 Offline Mode

**Graceful degradation:**
- Работает offline с cached данными
- Показывает индикатор устаревших данных
- Retry кнопка для повторной попытки online

---

## 10. Тестирование

### 10.1 Unit Tests

**Coverage:**
- `GetShikimoriMetadata` - все сценарии поиска
- `ShikimoriMetadata.isStale()` - TTL логика
- `Anime.getCoverUrl()` - логика выбора постера

### 10.2 Integration Tests

**Scenarios:**
- Cache hit (fresh data)
- Cache miss (stale data)
- Tracking exists → use tracking
- Auto-search success
- Auto-search not found
- Network error → show cached

### 10.3 Manual Testing

**Checklist:**
- [ ] Toggle ON → данные загружаются
- [ ] Toggle OFF → N/A отображается
- [ ] Tracking exists → используются tracking данные
- [ ] No tracking → автопоиск работает
- [ ] Network error → cached данные + warning icon
- [ ] Not found → N/A + search icon
- [ ] Retry button работает
- [ ] Постеры заменяются глобально
- [ ] Cache работает (повторное открытие = instant)
- [ ] TTL работает (> 7 дней = новый запрос)

---

## 11. Future Enhancements (v2)

### 11.1 Manual Search Dialog

**Функционал:**
- Search bar для поиска в Shikimori
- List результатов с постерами
- Кнопка "Выбрать" → сохраняет `is_manual_match = true`

### 11.2 Batch Update

**Функционал:**
- Settings action "Обновить все данные Shikimori"
- Background job для обновления кеша всей библиотеки
- Progress notification

### 11.3 Alternative Titles Search

**Улучшение точности:**
- Если первый поиск не удался → пробуем alternative titles
- English title, Romaji title (если доступны из sources)

### 11.4 More Shikimori Data

**Дополнительные поля:**
- Studio (студия)
- Season/Year (сезон/год)
- Episodes count (количество эпизодов)
- Genres from Shikimori

---

## 12. Migration Plan

### 12.1 Database Migration

**Version:** Next database version (check current)

```kotlin
// data/src/main/java/tachiyomi/data/DatabaseHandler.kt

private fun migrationXXX() {
    db.execSQL("""
        CREATE TABLE shikimori_metadata_cache (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            anime_id INTEGER NOT NULL UNIQUE,
            shikimori_id INTEGER,
            score REAL,
            kind TEXT,
            cover_url TEXT,
            search_query TEXT NOT NULL,
            updated_at INTEGER NOT NULL,
            is_manual_match INTEGER DEFAULT 0,
            FOREIGN KEY(anime_id) REFERENCES animes(_id) ON DELETE CASCADE
        )
    """.trimIndent())

    db.execSQL("CREATE INDEX idx_shikimori_cache_anime_id ON shikimori_metadata_cache(anime_id)")
    db.execSQL("CREATE INDEX idx_shikimori_cache_updated_at ON shikimori_metadata_cache(updated_at)")
}
```

### 12.2 Backwards Compatibility

**Graceful fallback:**
- Если таблица не существует → показываем N/A (как раньше)
- Migration автоматически при обновлении
- Старые пользователи не ломаются

---

## 13. Dependencies

### 13.1 Existing Components (Reuse)

- ✅ `ShikimoriApi` - уже реализован
- ✅ `ShikimoriInterceptor` - OAuth
- ✅ `SMEntry` DTO - модель данных
- ✅ `TrackingRepository` - для проверки tracking

### 13.2 New Components (Create)

- ❌ `ShikimoriMetadata` domain model
- ❌ `ShikimoriMetadataCache` DAO
- ❌ `GetShikimoriMetadata` interactor
- ❌ `Anime.getDisplayCoverUrl()` extension
- ❌ Settings UI для toggle
- ❌ Database migration

### 13.3 External APIs

**Shikimori API:**
- Base URL: `https://shikimori.one/api`
- Endpoints used:
  - `GET /animes/{id}` - получить аниме по ID
  - `GET /animes?search={query}` - поиск аниме
- Rate limit: 5 requests/second, 90 requests/minute
- No auth required for public data (but we use auth for consistency)

---

## 14. Risks and Mitigations

### 14.1 Риск: Неправильное совпадение аниме

**Проблема:** Автопоиск может найти неправильное аниме

**Митигация:**
- Берём первый результат по популярности (обычно верный)
- Кнопка manual search для исправления
- `is_manual_match` флаг защищает от перезаписи

### 14.2 Риск: Shikimori API недоступен

**Проблема:** API может быть down или rate limit

**Митигация:**
- Offline-first с 7-дневным кешем
- Graceful error handling
- Retry механизм
- Показываем cached данные при ошибках

### 14.3 Риск: Performance на больших библиотеках

**Проблема:** Автопоиск для 1000+ аниме может быть медленным

**Митигация:**
- Загрузка только при открытии экрана (lazy)
- Не загружаем все сразу
- Кеш работает offline
- Background batch update (v2)

### 14.4 Риск: Database migration failure

**Проблема:** Migration может сломаться на некоторых устройствах

**Митигация:**
- Тщательное тестирование migration
- Fallback на N/A если таблица не создана
- Try-catch в migration коде
- Версионирование базы

---

## 15. Metrics and Success Criteria

### 15.1 Success Metrics

**Функциональность:**
- ✅ 95%+ аниме имеют рейтинг из Shikimori
- ✅ < 5% неправильных совпадений
- ✅ Cache hit rate > 80% (повторные открытия)

**Performance:**
- ✅ Первая загрузка < 2 секунды (сетевой запрос)
- ✅ Cached загрузка < 100ms
- ✅ UI не блокируется при загрузке

**UX:**
- ✅ Toggle работают корректно
- ✅ Error states понятны пользователю
- ✅ Retry mechanism работает

### 15.2 Monitoring

**Логирование:**
- Cache hit/miss rate
- Search success rate
- API errors (network, not found)
- Manual search usage

---

## 16. Glossary

**Термины:**
- **Shikimori** - российский аниме-сайт с базой данных (shikimori.one)
- **Tracking** - функция добавления аниме в список на tracker сайтах
- **TTL** - Time To Live, срок жизни кеша
- **Cache hit** - данные найдены в кеше
- **Cache miss** - данные не найдены, нужен запрос к API
- **Graceful degradation** - корректная работа при ошибках
- **Manual match** - ручной выбор пользователем правильного аниме

---

## 17. References

**Документация:**
- Shikimori API docs: https://shikimori.one/api/doc
- Существующий код: `data/track/shikimori/`
- Aurora anime cards: `presentation/entries/anime/components/aurora/`

**Related plans:**
- `2026-01-22-aurora-anime-cards.md` - Aurora anime cards implementation

---

## 18. Approval

**Design approved by:** User
**Date:** 2026-01-23
**Next steps:** Create implementation plan and git worktree

---

**End of Design Document**
