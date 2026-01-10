package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.browse.anime.GlobalAnimeSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class GlobalAnimeSearchScreen(
    val searchQuery: String = "",
    private val extensionFilter: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            GlobalAnimeSearchScreenModel(
                initialQuery = searchQuery,
                initialExtensionFilter = extensionFilter,
            )
        }
        val state by screenModel.state.collectAsState()
        
        val uiPreferences = Injekt.get<eu.kanade.domain.ui.UiPreferences>()
        val theme by uiPreferences.appTheme().collectAsState()

        var showSingleLoadingScreen by remember {
            mutableStateOf(
                searchQuery.isNotEmpty() && !extensionFilter.isNullOrEmpty() && state.total == 1,
            )
        }

        if (showSingleLoadingScreen) {
            LoadingScreen()

            LaunchedEffect(state.items) {
                when (val result = state.items.values.singleOrNull()) {
                    AnimeSearchItemResult.Loading -> return@LaunchedEffect
                    is AnimeSearchItemResult.Success -> {
                        val anime = result.result.singleOrNull()
                        if (anime != null) {
                            navigator.replace(AnimeScreen(anime.id, true))
                        } else {
                            // Backoff to result screen
                            showSingleLoadingScreen = false
                        }
                    }
                    else -> showSingleLoadingScreen = false
                }
            }
        } else {
            if (theme == eu.kanade.domain.ui.model.AppTheme.AURORA) {
                eu.kanade.presentation.browse.anime.GlobalAnimeSearchAuroraContent(
                    items = state.items.values.filterIsInstance<AnimeSearchItemResult.Success>().map { it.result },
                    onAnimeClicked = { navigator.push(AnimeScreen(it, true)) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 16.dp)
                )
            } else {
                GlobalAnimeSearchScreen(
                    state = state,
                    navigateUp = navigator::pop,
                    onChangeSearchQuery = screenModel::updateSearchQuery,
                    onSearch = { screenModel.search() },
                    getAnime = { screenModel.getAnime(it) },
                    onChangeSearchFilter = screenModel::setSourceFilter,
                    onToggleResults = screenModel::toggleFilterResults,
                    onClickSource = {
                        navigator.push(BrowseAnimeSourceScreen(it.id, state.searchQuery ?: ""))
                    },
                    onClickItem = { navigator.push(AnimeScreen(it.id, true)) },
                    onLongClickItem = { navigator.push(AnimeScreen(it.id, true)) },
                )
            }
        }
    }
}
