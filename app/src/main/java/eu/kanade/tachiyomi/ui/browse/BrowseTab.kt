package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.browse.BrowseScreenAurora
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.anime.migration.sources.migrateAnimeSourceTab
import eu.kanade.tachiyomi.ui.browse.anime.source.AnimeSourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.extension.mangaExtensionsTab
import eu.kanade.tachiyomi.ui.browse.manga.migration.sources.migrateMangaSourceTab
import eu.kanade.tachiyomi.ui.browse.manga.source.mangaSourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState as preferenceCollectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object BrowseTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current is BrowseTab
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    // TODO: Find a way to let it open Global Anime/Manga Search depending on what Tab(e.g. Anime/Manga Source Tab) is open
    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalAnimeSearchScreen())
    }

    private val switchToTabNumberChannel = Channel<Int>(1, BufferOverflow.DROP_OLDEST)

    fun showExtension() {
        switchToTabNumberChannel.trySend(3) // Manga extensions: tab no. 3
    }

    fun showAnimeExtension() {
        switchToTabNumberChannel.trySend(2) // Anime extensions: tab no. 2
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        
        val uiPreferences = Injekt.get<UiPreferences>()
        val theme by uiPreferences.appTheme().preferenceCollectAsState()

        if (theme == AppTheme.AURORA) {
            val animeSourcesScreenModel = rememberScreenModel { AnimeSourcesScreenModel() }
            val animeSourcesState by animeSourcesScreenModel.state.collectAsState()
            
            val animeExtensionsScreenModel = rememberScreenModel { AnimeExtensionsScreenModel() }
            val tabs = persistentListOf(
                animeSourcesTab(),
                mangaSourcesTab(),
                animeExtensionsTab(animeExtensionsScreenModel),
                mangaExtensionsTab(rememberScreenModel { MangaExtensionsScreenModel() }),
                migrateAnimeSourceTab(),
                migrateMangaSourceTab(),
            )
            
            var selectedTabIndex by remember { mutableIntStateOf(-1) }
            val pagerState = rememberPagerState(initialPage = if (selectedTabIndex >= 0) selectedTabIndex else 0) { tabs.size }
            
            LaunchedEffect(Unit) {
                switchToTabNumberChannel.receiveAsFlow()
                    .collectLatest { tabIndex ->
                        selectedTabIndex = tabIndex
                    }
            }
            
            LaunchedEffect(selectedTabIndex) {
                if (selectedTabIndex >= 0) {
                    pagerState.scrollToPage(selectedTabIndex)
                }
            }
            
            if (selectedTabIndex < 0) {
                BrowseScreenAurora(
                    animeSources = animeSourcesState.items,
                    onAnimeSourceClick = { source ->
                        navigator.push(BrowseAnimeSourceScreen(source.id, null))
                    },
                    onAnimeSourceLongClick = { source ->
                        animeSourcesScreenModel.togglePin(source)
                    },
                    onGlobalSearchClick = { navigator.push(GlobalAnimeSearchScreen()) },
                    onExtensionsClick = { selectedTabIndex = 2 },
                    onMigrateClick = { selectedTabIndex = 4 }
                )
            } else {
                TabbedScreen(
                    titleRes = MR.strings.browse,
                    tabs = tabs,
                    state = pagerState,
                    mangaSearchQuery = null,
                    onChangeMangaSearchQuery = {},
                    animeSearchQuery = animeExtensionsScreenModel.state.collectAsState().value.searchQuery,
                    onChangeAnimeSearchQuery = animeExtensionsScreenModel::search,
                    scrollable = true,
                )
            }
            
            LaunchedEffect(Unit) {
                (context as? MainActivity)?.ready = true
            }
        } else {
            val mangaExtensionsScreenModel = rememberScreenModel { MangaExtensionsScreenModel() }
            val mangaExtensionsState by mangaExtensionsScreenModel.state.collectAsState()

            val animeExtensionsScreenModel = rememberScreenModel { AnimeExtensionsScreenModel() }
            val animeExtensionsState by animeExtensionsScreenModel.state.collectAsState()

            val tabs = persistentListOf(
                animeSourcesTab(),
                mangaSourcesTab(),
                animeExtensionsTab(animeExtensionsScreenModel),
                mangaExtensionsTab(mangaExtensionsScreenModel),
                migrateAnimeSourceTab(),
                migrateMangaSourceTab(),
            )

            val state = rememberPagerState { tabs.size }

            TabbedScreen(
                titleRes = MR.strings.browse,
                tabs = tabs,
                state = state,
                mangaSearchQuery = mangaExtensionsState.searchQuery,
                onChangeMangaSearchQuery = mangaExtensionsScreenModel::search,
                animeSearchQuery = animeExtensionsState.searchQuery,
                onChangeAnimeSearchQuery = animeExtensionsScreenModel::search,
                scrollable = true,
            )
            LaunchedEffect(Unit) {
                switchToTabNumberChannel.receiveAsFlow()
                    .collectLatest { state.scrollToPage(it) }
            }

            LaunchedEffect(Unit) {
                (context as? MainActivity)?.ready = true
            }
        }
    }
}
