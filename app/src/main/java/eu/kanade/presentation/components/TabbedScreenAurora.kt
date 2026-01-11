package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.theme.AuroraTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TabbedScreenAurora(
    titleRes: StringResource?,
    tabs: ImmutableList<TabContent>,
    modifier: Modifier = Modifier,
    state: PagerState = rememberPagerState { tabs.size },
    mangaSearchQuery: String? = null,
    onChangeMangaSearchQuery: (String?) -> Unit = {},
    scrollable: Boolean = false,
    animeSearchQuery: String? = null,
    onChangeAnimeSearchQuery: (String?) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val colors = AuroraTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.statusBarsPadding())

            AuroraTabHeader(
                title = titleRes?.let { stringResource(it) } ?: "",
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchClick = { isSearchActive = true },
                onSearchClose = {
                    isSearchActive = false
                    searchQuery = ""
                    onChangeMangaSearchQuery(null)
                    onChangeAnimeSearchQuery(null)
                },
                onSearchQueryChange = { query ->
                    searchQuery = query
                    if (state.currentPage % 2 == 1) {
                        onChangeMangaSearchQuery(query.ifEmpty { null })
                    } else {
                        onChangeAnimeSearchQuery(query.ifEmpty { null })
                    }
                },
                tabs = tabs,
                currentPage = state.currentPage
            )

            Spacer(modifier = Modifier.height(16.dp))

            AuroraTabRow(
                tabs = tabs,
                selectedIndex = state.currentPage,
                onTabSelected = { index ->
                    scope.launch { state.animateScrollToPage(index) }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = state,
                verticalAlignment = Alignment.Top,
            ) { page ->
                tabs[page].content(
                    PaddingValues(bottom = 100.dp),
                    snackbarHostState,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun AuroraTabHeader(
    title: String,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    tabs: ImmutableList<TabContent>,
    currentPage: Int
) {
    val colors = AuroraTheme.colors
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearchActive) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                placeholder = {
                    Text(
                        text = stringResource(MR.strings.action_search),
                        color = colors.textSecondary
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.glass,
                    unfocusedContainerColor = colors.glass,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.textSecondary
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onSearchClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = colors.textSecondary
                        )
                    }
                }
            )
        } else {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tabs.getOrNull(currentPage)?.searchEnabled == true) {
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier
                            .background(colors.glass, CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(MR.strings.action_search),
                            tint = colors.textPrimary
                        )
                    }
                }

                tabs.getOrNull(currentPage)?.actions?.forEach { appBarAction ->
                    if (appBarAction is AppBar.Action) {
                        IconButton(
                            onClick = appBarAction.onClick,
                            modifier = Modifier
                                .background(colors.glass, CircleShape)
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = appBarAction.icon,
                                contentDescription = appBarAction.title,
                                tint = colors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuroraTabRow(
    tabs: ImmutableList<TabContent>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            AuroraTabChip(
                text = stringResource(tab.titleRes),
                isSelected = isSelected,
                badgeCount = tab.badgeNumber,
                onClick = { onTabSelected(index) }
            )
        }
    }
}

@Composable
private fun AuroraTabChip(
    text: String,
    isSelected: Boolean,
    badgeCount: Int?,
    onClick: () -> Unit
) {
    val colors = AuroraTheme.colors
    
    val backgroundColor = if (isSelected) {
        colors.accent
    } else {
        colors.glass
    }

    val textColor = if (isSelected) {
        colors.textOnAccent
    } else {
        colors.textSecondary
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )

        if (badgeCount != null && badgeCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        if (isSelected) colors.textOnAccent.copy(alpha = 0.3f) else colors.accent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = colors.textOnAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
