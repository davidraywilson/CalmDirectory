package com.example.helloworld.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.eInkColorScheme
import com.mudita.mmd.eInkTypography

/**
 * CalmDirectory application theme using Mudita Mindful Design (MMD) principles
 * optimized for E Ink displays.
 *
 * This theme:
 * - Uses the MMD E Ink color scheme (monochromatic)
 * - Uses E Ink optimized typography
 * - Disables ripple effects
 * - Minimizes animations
 * - Increases contrast for better E Ink readability
 */
@Composable
fun CalmDirectoryTheme(
    content: @Composable () -> Unit
) {
    // Apply MMD theme with E Ink optimizations
    ThemeMMD(
        // You can override default MMD settings here if needed
        colorScheme = eInkColorScheme,
        typography = eInkTypography.copy(
            // Customize typography if needed
            headlineLarge = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            ),
            bodyLarge = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black
            ),
            labelLarge = TextStyle(
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        ),
        // MMD disables ripple effects by default for E Ink
        content = content
    )
}