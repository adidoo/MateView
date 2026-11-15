// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5E),
    secondary = Color(0xFF4A635D),
    tertiary = Color(0xFF436278),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD6C2),
    secondary = Color(0xFFB1CCC4),
    tertiary = Color(0xFFABCAE4),
)

/** App theme: wallpaper-based colours on Android 12+, a fixed teal palette below. */
@Composable
fun MateViewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
