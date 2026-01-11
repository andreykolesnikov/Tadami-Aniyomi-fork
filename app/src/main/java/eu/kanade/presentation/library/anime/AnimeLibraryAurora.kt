package eu.kanade.presentation.library.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.theme.AuroraTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AnimeLibraryAuroraContent(
    items: List<LibraryAnime>,
    onAnimeClicked: (Long) -> Unit,
    contentPadding: PaddingValues
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        text = stringResource(AYMR.strings.aurora_library),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(items) { item ->
                AuroraAnimeCard(item, onAnimeClicked)
            }
        }
    }
}

@Composable
fun AuroraAnimeCard(
    item: LibraryAnime,
    onClick: (Long) -> Unit
) {
    val colors = AuroraTheme.colors
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item.anime.id) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardBackground)
        ) {
            AsyncImage(
                model = item.anime.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Unseen episodes badge
            if (item.unseenCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(colors.accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.unseenCount.toString(),
                        color = colors.textOnAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Episode Counts Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${item.seenCount} / ${item.totalCount}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Play button overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .background(colors.accent, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = colors.textOnAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Text(
            text = item.anime.title,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 2,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
