package eu.kanade.presentation.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.ui.text.font.FontWeight
import tachiyomi.i18n.aniyomi.AYMR
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.anime.AnimeSourceUiModel
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin

@Composable
fun BrowseScreenAurora(
    animeSources: ImmutableList<AnimeSourceUiModel>,
    onAnimeSourceClick: (AnimeSource) -> Unit,
    onAnimeSourceLongClick: (AnimeSource) -> Unit,
    onGlobalSearchClick: () -> Unit,
    onExtensionsClick: () -> Unit,
    onMigrateClick: () -> Unit,
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1e1b4b),
            Color(0xFF101b22)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                Spacer(modifier = Modifier.statusBarsPadding())
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                BrowseAuroraHeader(
                    onSearchClick = onGlobalSearchClick
                )
            }

            item {
                QuickActionsSection(
                    onGlobalSearchClick = onGlobalSearchClick,
                    onExtensionsClick = onExtensionsClick,
                    onMigrateClick = onMigrateClick
                )
            }

            val pinnedSources = animeSources.filterIsInstance<AnimeSourceUiModel.Item>()
                .filter { Pin.Actual in it.source.pin }
            
            if (pinnedSources.isNotEmpty()) {
                item {
                    SourcesSectionHeader(title = stringResource(AYMR.strings.aurora_pinned_sources))
                }
                item {
                    PinnedSourcesRow(
                        sources = pinnedSources.map { it.source },
                        onSourceClick = onAnimeSourceClick,
                        onSourceLongClick = onAnimeSourceLongClick
                    )
                }
            }

            val pinnedSourceIds = pinnedSources.map { it.source.id }.toSet()
            
            animeSources.forEach { item ->
                when (item) {
                    is AnimeSourceUiModel.Header -> {
                        item(key = "header_${item.language}") {
                            SourcesSectionHeader(
                                title = getLanguageDisplayNameComposable(item.language),
                                showDivider = true
                            )
                        }
                    }
                    is AnimeSourceUiModel.Item -> {
                        if (item.source.id !in pinnedSourceIds) {
                            item(key = "source_${item.source.id}") {
                                SourceCard(
                                    source = item.source,
                                    onClick = { onAnimeSourceClick(item.source) },
                                    onPinClick = { onAnimeSourceLongClick(item.source) }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun BrowseAuroraHeader(
    onSearchClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(AYMR.strings.aurora_browse),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(AYMR.strings.aurora_discover_sources),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        IconButton(
            onClick = onSearchClick,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(AYMR.strings.aurora_global_search),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun QuickActionsSection(
    onGlobalSearchClick: () -> Unit,
    onExtensionsClick: () -> Unit,
    onMigrateClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionCard(
            icon = Icons.Outlined.Explore,
            title = stringResource(AYMR.strings.aurora_global_search),
            modifier = Modifier.weight(1f),
            onClick = onGlobalSearchClick
        )
        QuickActionCard(
            icon = Icons.Filled.Extension,
            title = stringResource(AYMR.strings.aurora_extensions),
            modifier = Modifier.weight(1f),
            onClick = onExtensionsClick
        )
        QuickActionCard(
            icon = Icons.Filled.SwapHoriz,
            title = stringResource(AYMR.strings.aurora_migrate),
            modifier = Modifier.weight(1f),
            onClick = onMigrateClick
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF279df1),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SourcesSectionHeader(title: String, showDivider: Boolean = false) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        if (showDivider) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(3.dp)
                .background(Color(0xFF279df1), RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun PinnedSourcesRow(
    sources: List<AnimeSource>,
    onSourceClick: (AnimeSource) -> Unit,
    onSourceLongClick: (AnimeSource) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sources, key = { it.id }) { source ->
            PinnedSourceCard(
                source = source,
                onClick = { onSourceClick(source) },
                onLongClick = { onSourceLongClick(source) }
            )
        }
    }
}

@Composable
private fun PinnedSourceCard(
    source: AnimeSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF279df1).copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = source.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = source.lang.uppercase(),
                color = Color(0xFF279df1),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SourceCard(
    source: AnimeSource,
    onClick: () -> Unit,
    onPinClick: () -> Unit
) {
    val isPinned = Pin.Actual in source.pin
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF279df1).copy(alpha = 0.3f),
                                Color(0xFF1e1b4b).copy(alpha = 0.5f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = source.name.take(2).uppercase(),
                    color = Color(0xFF279df1),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF279df1).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = source.lang.uppercase(),
                            color = Color(0xFF279df1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isPinned) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF22c55e).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(AYMR.strings.aurora_pinned_badge),
                                color = Color(0xFF22c55e),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onPinClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = stringResource(AYMR.strings.aurora_pinned_badge),
                    tint = if (isPinned) Color(0xFF22c55e) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun getLanguageDisplayNameComposable(code: String): String {
    return when (code) {
        "last_used" -> stringResource(AYMR.strings.aurora_source_last_used)
        "pinned" -> stringResource(AYMR.strings.aurora_source_pinned)
        "all" -> stringResource(AYMR.strings.aurora_source_all)
        "other" -> stringResource(AYMR.strings.aurora_source_other)
        "en" -> "English"
        "ja" -> "日本語"
        "zh" -> "中文"
        "ko" -> "한국어"
        "ru" -> "Русский"
        "es" -> "Español"
        "fr" -> "Français"
        "de" -> "Deutsch"
        "pt" -> "Português"
        "it" -> "Italiano"
        "ar" -> "العربية"
        "tr" -> "Türkçe"
        "pl" -> "Polski"
        "vi" -> "Tiếng Việt"
        "th" -> "ไทย"
        "id" -> "Indonesia"
        "hi" -> "हिन्दी"
        else -> code.uppercase()
    }
}
