package eu.kanade.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import eu.kanade.presentation.theme.AuroraTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun MoreScreenAurora(
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    onDownloadClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onStatsClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(80.dp))
                Text(
                    text = stringResource(AYMR.strings.aurora_more),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            item {
                AuroraToggleItem(
                    title = stringResource(AYMR.strings.aurora_downloaded_only),
                    icon = Icons.Filled.CloudOff,
                    checked = downloadedOnly,
                    onCheckedChange = onDownloadedOnlyChange
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                AuroraToggleItem(
                    title = stringResource(AYMR.strings.aurora_incognito_mode),
                    icon = Icons.Outlined.VisibilityOff,
                    checked = incognitoMode,
                    onCheckedChange = onIncognitoModeChange
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_downloads),
                    icon = Icons.Filled.Download,
                    onClick = onDownloadClick
                )
                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_settings),
                    icon = Icons.Filled.Settings,
                    onClick = onSettingsClick
                )
                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_statistics),
                    icon = Icons.Filled.QueryStats,
                    onClick = onStatsClick
                )
                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_about),
                    icon = Icons.Filled.Info,
                    onClick = onAboutClick
                )
                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_help),
                    icon = Icons.Filled.Help,
                    onClick = onHelpClick
                )
            }
        }
    }
}

@Composable
fun AuroraSettingItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val colors = AuroraTheme.colors
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.glass)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AuroraToggleItem(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = AuroraTheme.colors
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.glass)
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = colors.accent,
                checkedTrackColor = colors.accent.copy(alpha = 0.5f)
            )
        )
    }
}
