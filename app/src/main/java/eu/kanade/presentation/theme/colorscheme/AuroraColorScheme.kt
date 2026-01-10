package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object AuroraColorScheme : BaseColorScheme() {

    private val Primary = Color(0xFF279df1)
    private val BackgroundLight = Color(0xFFf6f7f8)
    private val BackgroundDark = Color(0xFF101b22)
    private val OnBackgroundDark = Color(0xFFFFFFFF)

    override val darkScheme = darkColorScheme(
        primary = Primary,
        onPrimary = Color.White,
        primaryContainer = Primary.copy(alpha = 0.2f),
        onPrimaryContainer = Primary,
        secondary = Primary,
        onSecondary = Color.White,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = BackgroundDark, // Glass effect will be manual
        onSurface = OnBackgroundDark,
        surfaceVariant = Color(0xFF1e293b),
        onSurfaceVariant = Color(0xFF94a3b8),
        outline = Color(0xFF334155),
    )

    override val lightScheme = lightColorScheme(
        primary = Primary,
        onPrimary = Color.White,
        background = BackgroundLight,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
    )
}
