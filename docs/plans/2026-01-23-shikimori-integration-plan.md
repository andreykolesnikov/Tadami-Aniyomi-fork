# Shikimori Integration - Implementation Plan

**Date:** 2026-01-23
**Design Doc:** `2026-01-23-shikimori-integration-design.md`
**Estimated Time:** 3-4 days
**Target:** Aurora anime cards with Shikimori data

---

## Overview

Implement Shikimori integration for Aurora anime cards in 4 phases:
1. **Phase 1:** Database & Models (Foundation)
2. **Phase 2:** Business Logic (Core)
3. **Phase 3:** UI Integration (Display)
4. **Phase 4:** Settings & Polish (UX)

**Architecture:** Use superpowers:subagent-driven-development for parallel task execution.

---

## Phase 1: Database & Models (Foundation)

### Task 1.1: Create ShikimoriMetadata domain model

**File:** `domain/src/main/java/tachiyomi/domain/shikimori/model/ShikimoriMetadata.kt`

**Implementation:**
```kotlin
package tachiyomi.domain.shikimori.model

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
```

**Verification:**
```bash
# Build compiles
./gradlew :domain:compileDebugKotlin
```

**Commit:**
```bash
git add domain/src/main/java/tachiyomi/domain/shikimori/model/ShikimoriMetadata.kt
git commit -m "feat(shikimori): add ShikimoriMetadata domain model

- Add data class with anime metadata from Shikimori
- Include isStale() for 7-day TTL check
- Include hasData() helper for null checks"
```

---

### Task 1.2: Add SQLDelight schema for cache table

**File:** `data/src/main/sqldelight/tachiyomi/data/shikimori_metadata_cache.sq`

**Implementation:**
```sql
CREATE TABLE shikimori_metadata_cache (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    anime_id INTEGER NOT NULL UNIQUE,
    shikimori_id INTEGER,
    score REAL,
    kind TEXT,
    cover_url TEXT,
    search_query TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    is_manual_match INTEGER AS Boolean DEFAULT 0,
    FOREIGN KEY(anime_id) REFERENCES animes(_id) ON DELETE CASCADE
);

CREATE INDEX idx_shikimori_cache_anime_id ON shikimori_metadata_cache(anime_id);
CREATE INDEX idx_shikimori_cache_updated_at ON shikimori_metadata_cache(updated_at);

getByAnimeId:
SELECT * FROM shikimori_metadata_cache
WHERE anime_id = :animeId;

upsert:
INSERT INTO shikimori_metadata_cache(
    anime_id, shikimori_id, score, kind, cover_url,
    search_query, updated_at, is_manual_match
)
VALUES (?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(anime_id)
DO UPDATE SET
    shikimori_id = excluded.shikimori_id,
    score = excluded.score,
    kind = excluded.kind,
    cover_url = excluded.cover_url,
    search_query = excluded.search_query,
    updated_at = excluded.updated_at,
    is_manual_match = excluded.is_manual_match
WHERE is_manual_match = 0;

deleteStaleEntries:
DELETE FROM shikimori_metadata_cache
WHERE updated_at < :threshold AND is_manual_match = 0;

deleteByAnimeId:
DELETE FROM shikimori_metadata_cache
WHERE anime_id = :animeId;

clearAll:
DELETE FROM shikimori_metadata_cache;
```

**Verification:**
```bash
# SQLDelight generates Kotlin code
./gradlew :data:generateDebugDatabaseInterface
```

**Commit:**
```bash
git add data/src/main/sqldelight/tachiyomi/data/shikimori_metadata_cache.sq
git commit -m "feat(shikimori): add SQLDelight schema for metadata cache

- Create shikimori_metadata_cache table
- Add indexes for anime_id and updated_at
- Implement CRUD queries (get, upsert, delete, clear)
- ON CONFLICT protects manual matches from auto-overwrite"
```

---

### Task 1.3: Create database migration

**File:** `data/src/main/java/tachiyomi/data/DatabaseHandler.kt`

**Implementation:**
Find current database version and add migration:

```kotlin
// In DatabaseHandler.kt, find the migrate() function

private fun migrate(driver: SqlDriver, oldVersion: Int, newVersion: Int) {
    // ... existing migrations

    if (oldVersion < NEW_VERSION) { // Replace NEW_VERSION with actual next version
        logcat { "Upgrading database from v$oldVersion to v$newVersion (add shikimori_metadata_cache)" }
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS shikimori_metadata_cache (
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
        """.trimIndent(), 0)

        driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_shikimori_cache_anime_id ON shikimori_metadata_cache(anime_id)", 0)
        driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_shikimori_cache_updated_at ON shikimori_metadata_cache(updated_at)", 0)
    }
}
```

**Also update:** Database version constant

**Verification:**
```bash
# Build and install on test device
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Check database with adb shell
adb shell "run-as com.tadami.aurora.dev sqlite3 /data/data/com.tadami.aurora.dev/databases/tachiyomi.db '.schema shikimori_metadata_cache'"
```

**Commit:**
```bash
git add data/src/main/java/tachiyomi/data/DatabaseHandler.kt
git commit -m "feat(shikimori): add database migration for cache table

- Bump database version to [VERSION]
- Create shikimori_metadata_cache table in migration
- Add indexes for performance
- Use IF NOT EXISTS for safety"
```

---

### Task 1.4: Implement ShikimoriMetadataCache DAO

**File:** `data/src/main/java/tachiyomi/data/shikimori/ShikimoriMetadataCache.kt`

**Implementation:**
```kotlin
package tachiyomi.data.shikimori

import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.Database
import tachiyomi.domain.shikimori.model.ShikimoriMetadata

class ShikimoriMetadataCache(
    private val database: Database,
) {

    fun get(animeId: Long): ShikimoriMetadata? {
        return try {
            database.shikimori_metadata_cacheQueries
                .getByAnimeId(animeId)
                .executeAsOneOrNull()
                ?.toShikimoriMetadata()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to get Shikimori cache for anime $animeId: ${e.message}" }
            null
        }
    }

    fun upsert(metadata: ShikimoriMetadata) {
        try {
            database.shikimori_metadata_cacheQueries.upsert(
                anime_id = metadata.animeId,
                shikimori_id = metadata.shikimoriId,
                score = metadata.score,
                kind = metadata.kind,
                cover_url = metadata.coverUrl,
                search_query = metadata.searchQuery,
                updated_at = metadata.updatedAt,
                is_manual_match = metadata.isManualMatch,
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to upsert Shikimori cache: ${e.message}" }
        }
    }

    fun delete(animeId: Long) {
        try {
            database.shikimori_metadata_cacheQueries.deleteByAnimeId(animeId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to delete Shikimori cache: ${e.message}" }
        }
    }

    fun clearAll() {
        try {
            database.shikimori_metadata_cacheQueries.clearAll()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to clear Shikimori cache: ${e.message}" }
        }
    }

    fun deleteStaleEntries() {
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            database.shikimori_metadata_cacheQueries.deleteStaleEntries(thirtyDaysAgo)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to delete stale entries: ${e.message}" }
        }
    }
}

// Extension function to convert SQLDelight model to domain model
private fun Shikimori_metadata_cache.toShikimoriMetadata(): ShikimoriMetadata {
    return ShikimoriMetadata(
        animeId = anime_id,
        shikimoriId = shikimori_id,
        score = score,
        kind = kind,
        coverUrl = cover_url,
        searchQuery = search_query,
        updatedAt = updated_at,
        isManualMatch = is_manual_match,
    )
}
```

**Verification:**
```bash
# Build compiles
./gradlew :data:compileDebugKotlin
```

**Commit:**
```bash
git add data/src/main/java/tachiyomi/data/shikimori/ShikimoriMetadataCache.kt
git commit -m "feat(shikimori): implement ShikimoriMetadataCache DAO

- Add CRUD operations for metadata cache
- Include deleteStaleEntries for cleanup (30+ days)
- Add toShikimoriMetadata() extension for SQLDelight model conversion
- Error handling with logcat"
```

---

## Phase 2: Business Logic (Core)

### Task 2.1: Add ShikimoriApi.getAnimeById() method

**File:** `app/src/main/java/eu/kanade/tachiyomi/data/track/shikimori/ShikimoriApi.kt`

**Implementation:**
Add new method to existing `ShikimoriApi` class:

```kotlin
suspend fun getAnimeById(id: Long): SMEntry {
    return withIOContext {
        val url = "$API_URL/animes/$id"
        with(json) {
            authClient.newCall(GET(url))
                .awaitSuccess()
                .parseAs()
        }
    }
}
```

**Verification:**
```bash
# Build compiles
./gradlew :app:compileDebugKotlin
```

**Commit:**
```bash
git add app/src/main/java/eu/kanade/tachiyomi/data/track/shikimori/ShikimoriApi.kt
git commit -m "feat(shikimori): add getAnimeById API method

- Fetch anime details by Shikimori ID
- Returns SMEntry with score, kind, cover_url
- Used for tracking-based metadata retrieval"
```

---

### Task 2.2: Add UI preferences for toggle switches

**File:** `domain/src/main/java/tachiyomi/domain/ui/UiPreferences.kt`

**Implementation:**
Add to `UiPreferences` interface:

```kotlin
fun useShikimoriRating(): Preference<Boolean>
fun useShikimoriCovers(): Preference<Boolean>
```

Add to implementation class:

```kotlin
fun useShikimoriRating() = preferenceStore.getBoolean(
    "use_shikimori_rating",
    defaultValue = true,
)

fun useShikimoriCovers() = preferenceStore.getBoolean(
    "use_shikimori_covers",
    defaultValue = true,
)
```

**Verification:**
```bash
# Build compiles
./gradlew :domain:compileDebugKotlin
```

**Commit:**
```bash
git add domain/src/main/java/tachiyomi/domain/ui/UiPreferences.kt
git commit -m "feat(shikimori): add UI preferences for Shikimori toggle

- Add useShikimoriRating preference (default: true)
- Add useShikimoriCovers preference (default: true)
- Two independent toggles for user control"
```

---

### Task 2.3: Implement GetShikimoriMetadata interactor

**File:** `domain/src/main/java/tachiyomi/domain/shikimori/interactor/GetShikimoriMetadata.kt`

**Implementation:**
```kotlin
package tachiyomi.domain.shikimori.interactor

import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.shikimori.ShikimoriMetadataCache
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.shikimori.model.ShikimoriMetadata
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.ui.UiPreferences

class GetShikimoriMetadata(
    private val metadataCache: ShikimoriMetadataCache,
    private val shikimori: Shikimori,
    private val getAnimeTracks: GetAnimeTracks,
    private val preferences: UiPreferences,
) {

    suspend fun await(anime: Anime): ShikimoriMetadata? {
        // Check if disabled via settings
        if (!preferences.useShikimoriRating().get() &&
            !preferences.useShikimoriCovers().get()) {
            return null
        }

        // Check cache first
        val cached = metadataCache.get(anime.id)
        if (cached != null && !cached.isStale()) {
            return cached
        }

        // Try to get from tracking
        val fromTracking = getFromTracking(anime)
        if (fromTracking != null) {
            metadataCache.upsert(fromTracking)
            return fromTracking
        }

        // Auto-search by title
        val fromSearch = searchAndCache(anime)
        if (fromSearch != null) {
            return fromSearch
        }

        // Cache "not found" to avoid re-searching
        cacheNotFound(anime)
        return null
    }

    private suspend fun getFromTracking(anime: Anime): ShikimoriMetadata? {
        return withIOContext {
            try {
                // Get Shikimori track for this anime
                val track = getAnimeTracks.await(anime.id)
                    .find { it.trackerId == shikimori.id }
                    ?: return@withIOContext null

                // Fetch full anime data from Shikimori
                val entry = shikimori.api.getAnimeById(track.remoteId)

                ShikimoriMetadata(
                    animeId = anime.id,
                    shikimoriId = entry.id,
                    score = entry.score,
                    kind = entry.kind,
                    coverUrl = ShikimoriApi.BASE_URL + entry.image.preview,
                    searchQuery = "tracking:${track.remoteId}",
                    updatedAt = System.currentTimeMillis(),
                    isManualMatch = true,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to get Shikimori data from tracking: ${e.message}" }
                null
            }
        }
    }

    private suspend fun searchAndCache(anime: Anime): ShikimoriMetadata? {
        return withIOContext {
            try {
                val results = shikimori.api.searchAnime(anime.title)
                val firstResult = results.firstOrNull() ?: return@withIOContext null

                val metadata = ShikimoriMetadata(
                    animeId = anime.id,
                    shikimoriId = firstResult.remote_id,
                    score = firstResult.score,
                    kind = firstResult.publishing_type,
                    coverUrl = firstResult.cover_url,
                    searchQuery = anime.title,
                    updatedAt = System.currentTimeMillis(),
                    isManualMatch = false,
                )

                metadataCache.upsert(metadata)
                metadata
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to search Shikimori: ${e.message}" }
                null
            }
        }
    }

    private fun cacheNotFound(anime: Anime) {
        val notFound = ShikimoriMetadata(
            animeId = anime.id,
            shikimoriId = null,
            score = null,
            kind = null,
            coverUrl = null,
            searchQuery = anime.title,
            updatedAt = System.currentTimeMillis(),
            isManualMatch = false,
        )
        metadataCache.upsert(notFound)
    }
}
```

**Verification:**
```bash
# Build compiles
./gradlew :domain:compileDebugKotlin
```

**Commit:**
```bash
git add domain/src/main/java/tachiyomi/domain/shikimori/interactor/GetShikimoriMetadata.kt
git commit -m "feat(shikimori): implement GetShikimoriMetadata interactor

- Hybrid strategy: tracking → cache → auto-search
- Cache 'not found' to avoid repeated searches
- Check settings toggle before making requests
- Error handling with fallback to null"
```

---

### Task 2.4: Create Anime.getDisplayCoverUrl() extension

**File:** `domain/src/main/java/tachiyomi/domain/entries/anime/model/AnimeExtensions.kt`

**Implementation:**
```kotlin
package tachiyomi.domain.entries.anime.model

import tachiyomi.domain.shikimori.model.ShikimoriMetadata

/**
 * Get the appropriate cover URL based on Shikimori metadata and user preferences.
 */
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
```

**Verification:**
```bash
# Build compiles
./gradlew :domain:compileDebugKotlin
```

**Commit:**
```bash
git add domain/src/main/java/tachiyomi/domain/entries/anime/model/AnimeExtensions.kt
git commit -m "feat(shikimori): add Anime.getCoverUrl() extension

- Select correct cover URL based on settings and metadata
- Fallback to original thumbnailUrl if Shikimori not available
- Used globally for poster replacement"
```

---

## Phase 3: UI Integration (Display)

### Task 3.1: Add ShikimoriError sealed interface

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreenModel.kt`

**Implementation:**
Add near the top of the file:

```kotlin
sealed interface ShikimoriError {
    data object NetworkError : ShikimoriError
    data object NotFound : ShikimoriError
    data object Disabled : ShikimoriError
}
```

**Verification:**
```bash
# Build compiles
./gradlew :app:compileDebugKotlin
```

**Commit:**
```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreenModel.kt
git commit -m "feat(shikimori): add ShikimoriError sealed interface

- NetworkError: API unavailable or no internet
- NotFound: Anime not found in Shikimori
- Disabled: User disabled via toggle"
```

---

### Task 3.2: Extend AnimeScreenModel.State.Success

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreenModel.kt`

**Implementation:**
Add to `State.Success` data class:

```kotlin
data class Success(
    val anime: Anime,
    // ... existing fields

    // Shikimori metadata
    val shikimoriMetadata: ShikimoriMetadata? = null,
    val isShikimoriLoading: Boolean = false,
    val shikimoriError: ShikimoriError? = null,
) : State
```

**Verification:**
```bash
# Build compiles (may need to fix usages)
./gradlew :app:compileDebugKotlin
```

**Commit:**
```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreenModel.kt
git commit -m "feat(shikimori): extend AnimeScreenModel state with Shikimori fields

- Add shikimoriMetadata to State.Success
- Add isShikimoriLoading for loading indicator
- Add shikimoriError for error handling"
```

---

### Task 3.3: Implement Shikimori data loading in AnimeScreenModel

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreenModel.kt`

**Implementation:**

1. Add dependency in constructor:
```kotlin
class AnimeScreenModel(
    // ... existing dependencies
    private val getShikimoriMetadata: GetShikimoriMetadata,
    private val shikimoriMetadataCache: ShikimoriMetadataCache,
) : StateScreenModel<AnimeScreenModel.State>(State.Loading) {
```

2. Add loading method:
```kotlin
private suspend fun loadShikimoriMetadata(animeId: Long) {
    val currentState = state.value as? State.Success ?: return

    // Show loading
    mutableState.update {
        currentState.copy(isShikimoriLoading = true)
    }

    try {
        val metadata = getShikimoriMetadata.await(currentState.anime)

        mutableState.update {
            currentState.copy(
                shikimoriMetadata = metadata,
                isShikimoriLoading = false,
                shikimoriError = if (metadata == null) ShikimoriError.NotFound else null,
            )
        }
    } catch (e: Exception) {
        logcat(LogPriority.ERROR) { "Failed to load Shikimori metadata: ${e.message}" }

        // Try to show cached data on error
        val cached = shikimoriMetadataCache.get(animeId)

        mutableState.update {
            currentState.copy(
                shikimoriMetadata = cached,
                isShikimoriLoading = false,
                shikimoriError = ShikimoriError.NetworkError,
            )
        }
    }
}

fun retryShikimoriLoad() {
    val currentState = state.value as? State.Success ?: return
    coroutineScope.launchIO {
        loadShikimoriMetadata(currentState.anime.id)
    }
}
```

3. Call in `init` or when anime loads:
```kotlin
// In the function that observes anime data, add:
coroutineScope.launchIO {
    loadShikimoriMetadata(animeId)
}
```

**Verification:**
```bash
# Build compiles
./gradlew :app:compileDebugKotlin
```

**Commit:**
```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreenModel.kt
git commit -m "feat(shikimori): implement metadata loading in AnimeScreenModel

- Add loadShikimoriMetadata() method
- Load in parallel with anime data
- Error handling with cached fallback
- Add retryShikimoriLoad() for manual retry"
```

---

### Task 3.4: Update AnimeInfoCard with Shikimori params

**File:** `app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/AnimeInfoCard.kt`

**Implementation:**

1. Add parameters to `AnimeInfoCard`:
```kotlin
@Composable
fun AnimeInfoCard(
    anime: Anime,
    episodeCount: Int,
    nextUpdate: Instant?,
    onTagSearch: (String) -> Unit,
    descriptionExpanded: Boolean,
    genresExpanded: Boolean,
    onToggleDescription: () -> Unit,
    onToggleGenres: () -> Unit,

    // NEW: Shikimori params
    shikimoriMetadata: ShikimoriMetadata? = null,
    isShikimoriLoading: Boolean = false,
    shikimoriError: ShikimoriError? = null,
    onRetryShikimori: () -> Unit = {},
    onSearchShikimori: () -> Unit = {},

    modifier: Modifier = Modifier,
) {
```

2. Update Rating StatItem:
```kotlin
val preferences = remember { Injekt.get<UiPreferences>() }
val useShikimoriRating by preferences.useShikimoriRating().collectAsState()

// Rating
StatItem(
    value = when {
        !useShikimoriRating -> stringResource(MR.strings.not_applicable)
        isShikimoriLoading -> "..."
        shikimoriMetadata?.score != null -> String.format("%.1f", shikimoriMetadata.score)
        shikimoriError is ShikimoriError.NetworkError -> "N/A ⚠"
        shikimoriError is ShikimoriError.NotFound -> "N/A"
        else -> stringResource(MR.strings.not_applicable)
    },
    label = "РЕЙТИНГ",
    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
    isStale = shikimoriError is ShikimoriError.NetworkError,
    onRetry = if (shikimoriError is ShikimoriError.NetworkError) onRetryShikimori else null,
    onSearch = if (shikimoriError is ShikimoriError.NotFound) onSearchShikimori else null,
)
```

3. Update Type StatItem:
```kotlin
// Type
StatItem(
    value = when {
        !useShikimoriRating -> stringResource(MR.strings.not_applicable)
        isShikimoriLoading -> "..."
        shikimoriMetadata?.kind != null -> formatAnimeKind(shikimoriMetadata.kind)
        shikimoriError is ShikimoriError.NetworkError -> "N/A ⚠"
        shikimoriError is ShikimoriError.NotFound -> "N/A"
        else -> stringResource(MR.strings.not_applicable)
    },
    label = "ТИП",
    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
    isStale = shikimoriError is ShikimoriError.NetworkError,
)
```

4. Add helper function:
```kotlin
private fun formatAnimeKind(kind: String): String {
    return when (kind.lowercase()) {
        "tv" -> "TV"
        "movie" -> "Фильм"
        "ova" -> "OVA"
        "ona" -> "ONA"
        "special" -> "Спешл"
        "music" -> "Музыка"
        else -> kind.uppercase()
    }
}
```

**Verification:**
```bash
# Build compiles
./gradlew :app:compileDebugKotlin
```

**Commit:**
```bash
git add app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/AnimeInfoCard.kt
git commit -m "feat(shikimori): update AnimeInfoCard with Shikimori data

- Add shikimoriMetadata, loading, error params
- Display score from Shikimori (format: 8.5)
- Display kind from Shikimori (localized)
- Show loading state (...) while fetching
- Show error states (N/A ⚠ for network, N/A for not found)"
```

---

### Task 3.5: Update StatItem with retry/search icons

**File:** `app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/AnimeInfoCard.kt`

**Implementation:**
Update `StatItem` composable:

```kotlin
@Composable
private fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    isStale: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isStale) {
                    colors.textPrimary.copy(alpha = 0.6f)
                } else {
                    colors.textPrimary
                },
            )

            // Retry icon for network errors
            if (onRetry != null) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Retry",
                    tint = colors.accent.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onRetry() },
                )
            }

            // Search icon for not found
            if (onSearch != null) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "Search manually",
                    tint = colors.accent.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onSearch() },
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

Add imports:
```kotlin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
```

**Verification:**
```bash
# Build compiles
./gradlew :app:compileDebugKotlin
```

**Commit:**
```bash
git add app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/AnimeInfoCard.kt
git commit -m "feat(shikimori): add retry and search icons to StatItem

- Show Refresh icon (⟳) when network error
- Show Search icon (🔍) when not found
- Click handlers for retry and manual search
- Stale data shown with reduced opacity"
```

---

### Task 3.6: Update FullscreenPosterBackground with Shikimori cover

**File:** `app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/FullscreenPosterBackground.kt`

**Implementation:**

1. Add parameter:
```kotlin
@Composable
fun FullscreenPosterBackground(
    anime: Anime,
    scrollOffset: Int,
    firstVisibleItemIndex: Int,
    shikimoriMetadata: ShikimoriMetadata? = null,
    modifier: Modifier = Modifier,
) {
```

2. Get display cover URL:
```kotlin
val context = LocalContext.current
val preferences = remember { Injekt.get<UiPreferences>() }
val useShikimoriCovers by preferences.useShikimoriCovers().collectAsState()
val coverUrl = anime.getCoverUrl(shikimoriMetadata, useShikimoriCovers)
```

3. Update Layer 2 (full quality):
```kotlin
// Layer 2: Full quality poster (Shikimori or original)
AsyncImage(
    model = remember(coverUrl, anime.coverLastModified) {
        ImageRequest.Builder(context)
            .data(coverUrl)
            .crossfade(true)
            .build()
    },
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier
        .fillMaxSize()
        .blur(blurAmount),
)
```

Add imports:
```kotlin
import tachiyomi.domain.shikimori.model.ShikimoriMetadata
import tachiyomi.domain.entries.anime.model.getCoverUrl
import tachiyomi.domain.ui.UiPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
```

**Verification:**
```bash
# Build compiles
./gradlew :app:compileDebugKotlin
```

**Commit:**
```bash
git add app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/FullscreenPosterBackground.kt
git commit -m "feat(shikimori): integrate Shikimori covers in background

- Add shikimoriMetadata parameter
- Use Anime.getCoverUrl() to select poster
- Respect useShikimoriCovers toggle
- Fallback to original thumbnailUrl if unavailable"
```

---

### Task 3.7: Update AnimeScreenAurora to pass Shikimori data

**File:** `app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreenAurora.kt`

**Implementation:**

1. Extract shikimoriMetadata from state:
```kotlin
@Composable
fun AnimeScreenAuroraImpl(
    state: AnimeScreenModel.State.Success,
    // ... other params
) {
    val anime = state.anime
    val episodes = state.episodeListItems
    val shikimoriMetadata = state.shikimoriMetadata
    // ...
```

2. Pass to FullscreenPosterBackground:
```kotlin
FullscreenPosterBackground(
    anime = anime,
    scrollOffset = scrollOffset,
    firstVisibleItemIndex = firstVisibleItemIndex,
    shikimoriMetadata = shikimoriMetadata,
)
```

3. Pass to AnimeInfoCard (inside the item block):
```kotlin
AnimeInfoCard(
    anime = anime,
    episodeCount = episodes.size,
    nextUpdate = nextUpdate,
    onTagSearch = onTagSearch,
    descriptionExpanded = descriptionExpanded,
    genresExpanded = genresExpanded,
    onToggleDescription = { descriptionExpanded = !descriptionExpanded },
    onToggleGenres = { genresExpanded = !genresExpanded },

    // Shikimori params
    shikimoriMetadata = shikimoriMetadata,
    isShikimoriLoading = state.isShikimoriLoading,
    shikimoriError = state.shikimoriError,
    onRetryShikimori = { /* TODO: get screenModel reference */ },
    onSearchShikimori = { /* TODO: implement manual search */ },

    modifier = Modifier
        .fillMaxWidth()
        .animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            alignment = Alignment.TopStart,
        ),
)
```

**Verification:**
```bash
# Build compiles
./gradlew :app:compileDebugKotlin
```

**Commit:**
```bash
git add app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreenAurora.kt
git commit -m "feat(shikimori): integrate Shikimori data in AnimeScreenAurora

- Extract shikimoriMetadata from state
- Pass to FullscreenPosterBackground for covers
- Pass to AnimeInfoCard for rating/type display
- Add TODO for retry/search callbacks"
```

---

## Phase 4: Settings & Polish (UX)

### Task 4.1: Add Shikimori settings section

**File:** Find appropriate settings screen (e.g., `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt` or create new section)

**Implementation:**
```kotlin
@Composable
fun ShikimoriSettingsGroup() {
    val preferences = remember { Injekt.get<UiPreferences>() }
    val useShikimoriRating by preferences.useShikimoriRating().collectAsState()
    val useShikimoriCovers by preferences.useShikimoriCovers().collectAsState()
    val metadataCache = remember { Injekt.get<ShikimoriMetadataCache>() }

    PreferenceGroup(
        title = stringResource(AYMR.strings.pref_category_shikimori),
    ) {
        SwitchPreference(
            title = stringResource(AYMR.strings.pref_shikimori_rating_title),
            subtitle = stringResource(AYMR.strings.pref_shikimori_rating_subtitle),
            checked = useShikimoriRating,
            onCheckedChange = {
                preferences.useShikimoriRating().set(it)
            },
        )

        SwitchPreference(
            title = stringResource(AYMR.strings.pref_shikimori_covers_title),
            subtitle = stringResource(AYMR.strings.pref_shikimori_covers_subtitle),
            checked = useShikimoriCovers,
            onCheckedChange = {
                preferences.useShikimoriCovers().set(it)
            },
        )

        TextPreference(
            title = stringResource(AYMR.strings.pref_shikimori_clear_cache),
            subtitle = stringResource(AYMR.strings.pref_shikimori_clear_cache_subtitle),
            onClick = {
                metadataCache.clearAll()
                // TODO: Show toast "Cache cleared"
            },
        )

        InfoPreference(
            stringResource(AYMR.strings.pref_shikimori_info),
        )
    }
}
```

Add to appropriate settings screen composable.

**Verification:**
```bash
# Build and check settings screen
./gradlew assembleDebug
```

**Commit:**
```bash
git add app/src/main/java/eu/kanade/presentation/more/settings/screen/[SettingsFile].kt
git commit -m "feat(shikimori): add Shikimori settings section

- Add toggle for rating/type from Shikimori
- Add toggle for Shikimori covers
- Add clear cache button
- Add info text about caching behavior"
```

---

### Task 4.2: Add string resources

**File:** `i18n-aniyomi/src/commonMain/resources/MR/base/strings.xml`

**Implementation:**
```xml
<!-- Shikimori Integration -->
<string name="pref_category_shikimori">Shikimori интеграция</string>
<string name="pref_shikimori_rating_title">Показывать рейтинг и тип из Shikimori</string>
<string name="pref_shikimori_rating_subtitle">Отображать рейтинг и тип аниме из базы данных Shikimori</string>
<string name="pref_shikimori_covers_title">Использовать постеры Shikimori</string>
<string name="pref_shikimori_covers_subtitle">Заменять постеры аниме на версии из Shikimori</string>
<string name="pref_shikimori_clear_cache">Очистить кеш Shikimori</string>
<string name="pref_shikimori_clear_cache_subtitle">Удалить все сохранённые данные из Shikimori</string>
<string name="pref_shikimori_info">Данные кешируются на 7 дней. При отсутствии tracking автоматически ищется аниме по названию.</string>
```

**Verification:**
```bash
# Build and regenerate resources
./gradlew :i18n-aniyomi:generateMRcommonMain
```

**Commit:**
```bash
git add i18n-aniyomi/src/commonMain/resources/MR/base/strings.xml
git commit -m "feat(shikimori): add string resources for settings

- Add category, titles, subtitles for toggles
- Add clear cache strings
- Add info text about caching"
```

---

### Task 4.3: Wire up retry callback in AnimeScreen

**File:** `app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreen.kt`

**Implementation:**
Find where `AnimeScreenAuroraImpl` is called and pass screenModel:

```kotlin
AnimeScreenAuroraImpl(
    state = successState,
    // ... other params

    // Add callback that calls screenModel
    onRetryShikimori = { screenModel.retryShikimoriLoad() },
    onSearchShikimori = { /* TODO: implement manual search in v2 */ },
)
```

You may need to add these parameters to `AnimeScreenAuroraImpl` signature if not already there.

**Verification:**
```bash
# Build compiles
./gradlew :app:compileDebugKotlin
```

**Commit:**
```bash
git add app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreen.kt
git commit -m "feat(shikimori): wire up retry callback in AnimeScreen

- Connect onRetryShikimori to screenModel.retryShikimoriLoad()
- Add placeholder for onSearchShikimori (v2 feature)
- Retry button now functional"
```

---

### Task 4.4: Register dependencies in DI

**File:** `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` (or appropriate DI module)

**Implementation:**
Add to Koin module:

```kotlin
// Shikimori metadata cache
single { ShikimoriMetadataCache(get()) }

// Shikimori interactor
factory {
    GetShikimoriMetadata(
        metadataCache = get(),
        shikimori = get(), // Shikimori tracker
        getAnimeTracks = get(),
        preferences = get(),
    )
}
```

Update `AnimeScreenModel` factory to include dependency:

```kotlin
factory { params ->
    AnimeScreenModel(
        // ... existing params
        getShikimoriMetadata = get(),
        shikimoriMetadataCache = get(),
    )
}
```

**Verification:**
```bash
# Build and run - check DI doesn't crash
./gradlew assembleDebug
```

**Commit:**
```bash
git add app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt
git commit -m "feat(shikimori): register dependencies in DI

- Add ShikimoriMetadataCache singleton
- Add GetShikimoriMetadata factory
- Inject into AnimeScreenModel"
```

---

### Task 4.5: Apply covers globally (library, search, etc.)

**Files:** Various (library grid/list items, search results, episode cards)

**Implementation:**
For each place that displays `anime.thumbnailUrl`, update to use Shikimori cover:

Example pattern:
```kotlin
// BEFORE:
AsyncImage(
    model = anime.thumbnailUrl,
    // ...
)

// AFTER:
val preferences = remember { Injekt.get<UiPreferences>() }
val useShikimoriCovers by preferences.useShikimoriCovers().collectAsState()

// You'll need shikimoriMetadata - either:
// 1. Load it in the parent composable/ViewModel
// 2. Or just use anime.thumbnailUrl for non-Aurora screens (simpler)

// For simplicity, only apply in Aurora anime screen for v1
// Global application can be v2
```

**Key locations to update (v2):**
- Library anime grid items
- Library anime list items
- Search results
- Episode card thumbnails (if they use anime cover)

**For v1 (current plan):** Only Aurora anime screen uses Shikimori covers.

**Verification:**
```bash
# Build and visually check
./gradlew assembleDebug
```

**Commit:**
```bash
git add [modified files]
git commit -m "feat(shikimori): apply Shikimori covers in Aurora screen

- v1: Only Aurora anime screen uses Shikimori covers
- Other screens continue using original thumbnailUrl
- Global application planned for v2"
```

---

### Task 4.6: Final testing and validation

**Manual Testing Checklist:**

```bash
# Build and install
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Test scenarios:**
- [ ] Open anime with Shikimori tracking → shows tracking data
- [ ] Open anime without tracking → auto-search works
- [ ] Toggle "Show rating/type" OFF → shows N/A
- [ ] Toggle "Use Shikimori covers" OFF → shows original poster
- [ ] Disconnect network → shows cached data with ⚠ icon
- [ ] Click retry icon → reloads data
- [ ] Open anime not in Shikimori → shows "N/A" with 🔍
- [ ] Close and reopen anime → data loads from cache instantly
- [ ] Wait 8 days → data refreshes automatically
- [ ] Settings: Clear cache → removes all cached data
- [ ] Anime with tracking: correct score/kind/cover
- [ ] Anime with auto-search: reasonable match (first result)

**Commit:**
```bash
git add .
git commit -m "test(shikimori): complete manual testing

All test scenarios passed:
- Tracking integration works
- Auto-search functional
- Toggles control behavior
- Cache TTL working (7 days)
- Error handling graceful
- Retry mechanism functional"
```

---

## Verification Commands

**Build checks:**
```bash
# Clean build
./gradlew clean

# Compile all modules
./gradlew compileDebugKotlin

# Run lint
./gradlew lintDebug

# Full build
./gradlew assembleDebug
```

**Database checks:**
```bash
# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Check database schema
adb shell "run-as com.tadami.aurora.dev sqlite3 /data/data/com.tadami.aurora.dev/databases/tachiyomi.db '.schema shikimori_metadata_cache'"

# Check cache contents
adb shell "run-as com.tadami.aurora.dev sqlite3 /data/data/com.tadami.aurora.dev/databases/tachiyomi.db 'SELECT * FROM shikimori_metadata_cache LIMIT 5'"
```

---

## Git Strategy

**Branch:**
```bash
git checkout -b feat/shikimori-integration
```

**Commit message format:**
```
feat(shikimori): <short description>

<detailed description>
- Bullet point 1
- Bullet point 2

[optional] Closes #issue
```

**Final PR:**
```bash
# Push branch
git push origin feat/shikimori-integration

# Create PR with link to design doc
gh pr create --title "feat: Shikimori integration for Aurora anime cards" \
  --body "Implements Shikimori metadata integration as designed in docs/plans/2026-01-23-shikimori-integration-design.md"
```

---

## Estimated Timeline

**Phase 1 (Foundation):** 4-6 hours
- Database schema, migrations, models
- DAO implementation

**Phase 2 (Core Logic):** 6-8 hours
- API integration
- Repository/Interactor
- Preferences

**Phase 3 (UI):** 8-10 hours
- State management
- AnimeInfoCard updates
- Background poster integration
- AnimeScreenAurora wiring

**Phase 4 (Polish):** 4-6 hours
- Settings UI
- String resources
- DI registration
- Testing

**Total:** 22-30 hours (~3-4 days)

---

## Success Criteria

**Functional:**
- ✅ Rating and Type display from Shikimori
- ✅ Covers replaced with Shikimori versions
- ✅ Tracking-based metadata works
- ✅ Auto-search works for non-tracked anime
- ✅ 7-day cache reduces API calls
- ✅ Both toggles control behavior independently

**Technical:**
- ✅ Database migration succeeds
- ✅ No crashes or errors
- ✅ Build passes without warnings
- ✅ DI graph resolves correctly

**UX:**
- ✅ Loading states clear
- ✅ Error states actionable (retry/search)
- ✅ Cached data shown offline
- ✅ Settings intuitive

---

## Future Enhancements (v2)

**Not in this plan:**
- Manual search dialog (when clicking 🔍)
- Batch cache update for entire library
- Alternative title search fallback
- Global cover replacement (library, search)
- More Shikimori fields (studio, season, etc.)

**Track as separate tasks.**

---

**End of Implementation Plan**
