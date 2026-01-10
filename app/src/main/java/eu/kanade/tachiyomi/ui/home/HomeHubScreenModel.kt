package eu.kanade.tachiyomi.ui.home

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HomeHubScreenModel(
    private val getAnimeHistory: GetAnimeHistory = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val setAnimeViewerFlags: SetAnimeViewerFlags = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val userProfilePreferences: UserProfilePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<HomeHubScreenModel.State>(State()) {

    data class State(
        val history: List<AnimeHistoryWithRelations> = emptyList(),
        val heroItem: AnimeHistoryWithRelations? = null,
        val heroEpisode: Episode? = null,
        val recommendations: List<LibraryAnime> = emptyList(),
        val userName: String = "Guest",
        val userAvatar: String = "",
    )

    init {
        screenModelScope.launchIO {
            // Get History
            getAnimeHistory.subscribe(query = "")
                .collectLatest { historyList ->
                    val hero = historyList.firstOrNull()
                    var heroEp: Episode? = null
                    
                    if (hero != null) {
                        // Get next episode for hero
                        val nextEpisodes = getNextEpisodes.await(hero.animeId, hero.episodeId, onlyUnseen = true)
                        heroEp = nextEpisodes.firstOrNull() ?: getNextEpisodes.await(hero.animeId, hero.episodeId, onlyUnseen = false).firstOrNull()
                    }

                    mutableState.update { 
                        it.copy(
                            history = historyList.take(6), // Limit history items
                            heroItem = hero,
                            heroEpisode = heroEp
                        )
                    }
                }
        }

        screenModelScope.launchIO {
            // Get Recommendations
            getLibraryAnime.subscribe()
                .collectLatest { libraryList ->
                    val sorted = libraryList.sortedByDescending { it.anime.lastUpdate }
                    mutableState.update { it.copy(recommendations = sorted.take(10)) }
                }
        }

        // Observe User Profile
        screenModelScope.launchIO {
            userProfilePreferences.name().changes().collectLatest { name ->
                mutableState.update { it.copy(userName = name) }
            }
        }
        screenModelScope.launchIO {
            userProfilePreferences.avatarUrl().changes().collectLatest { url ->
                mutableState.update { it.copy(userAvatar = url) }
            }
        }
    }

    fun playHeroEpisode(context: Context) {
        val state = state.value
        val anime = state.heroItem ?: return
        val episode = state.heroEpisode ?: return

        screenModelScope.launchIO {
            withIOContext {
                MainActivity.startPlayerActivity(
                    context,
                    anime.animeId,
                    episode.id,
                    false // TODO: Check preferences for external player
                )
            }
        }
    }

    fun toggleHeroFavorite() {
        // ... (existing logic)
    }

    fun updateUserName(name: String) {
        userProfilePreferences.name().set(name)
    }

    fun updateUserAvatar(uriString: String) {
        val context = Injekt.get<android.app.Application>()
        try {
            val uri = android.net.Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val file = java.io.File(context.filesDir, "user_avatar.jpg")
                val outputStream = java.io.FileOutputStream(file)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                userProfilePreferences.avatarUrl().set(file.absolutePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLastUsedAnimeSourceId(): Long {
        return sourcePreferences.lastUsedAnimeSource().get()
    }
}
