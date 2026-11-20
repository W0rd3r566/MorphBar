// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration and branding.
package dev.w0rd3r.morphbar.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive with the wallpaper palette. minSdk is 31, so dynamic
 * colour is always available and there is no static fallback scheme to drift
 * out of sync with Android's.
 */
@Composable
fun IsletTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme: ColorScheme = if (dark) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/**
 * The island always uses the dark scheme regardless of system theme: it is a
 * black object sitting on the black of the panel, and a light card around a
 * camera hole looks like a bug rather than a feature.
 */
@Composable
fun IslandTheme(
    context: Context = LocalContext.current,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = dynamicDarkColorScheme(context),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
