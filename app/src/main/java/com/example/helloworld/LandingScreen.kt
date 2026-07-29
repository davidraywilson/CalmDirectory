package com.example.helloworld

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.helloworld.data.QuickLocation
import com.mudita.mmd.components.chips.AssistChipMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD

@Composable
fun LandingScreen(
    modifier: Modifier = Modifier,
    quickLocations: List<QuickLocation>,
    onAddQuickLocation: () -> Unit,
    onQuickLocationClicked: (QuickLocation) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clip(CircleShape)
                        .clickable { onAddQuickLocation() }
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Quick Location",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            items(quickLocations) { location ->
                AssistChipMMD(
                    onClick = { onQuickLocationClicked(location) },
                    label = {
                        Text(
                            text = location.label,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        val icon = when (location.label.lowercase()) {
                            "home" -> Icons.Outlined.Home
                            "work" -> Icons.Outlined.WorkOutline
                            else -> Icons.Outlined.Place
                        }
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
        }

        HorizontalDividerMMD(
            thickness = 1.dp,
            modifier = Modifier.padding(start = 16.dp)
        )

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .verticalScrollbar(scrollState)
        ) {
            SearchFilterControls(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    width: Dp = 4.dp,
    color: Color = Color.Black,
): Modifier = drawWithContent {
    drawContent()
    val maxScroll = scrollState.maxValue
    if (maxScroll <= 0) return@drawWithContent

    val viewportHeight = size.height
    val totalHeight = viewportHeight + maxScroll
    val thumbHeight = (viewportHeight * (viewportHeight / totalHeight)).coerceAtLeast(24f)
    val scrollFraction = scrollState.value.toFloat() / maxScroll
    val thumbTop = (viewportHeight - thumbHeight) * scrollFraction
    val widthPx = width.toPx()
    drawRect(
        color = color,
        topLeft = Offset(size.width - widthPx, thumbTop),
        size = Size(widthPx, thumbHeight),
    )
}
