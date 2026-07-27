package com.paperapps.papermaps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperapps.papermaps.data.QuickLocation
import com.mudita.mmd.components.chips.AssistChipMMD
import com.paperapps.paperui.components.DashedDivider
import com.paperapps.paperui.components.PaperLazyColumn

val poiCategories = listOf(
    "Gas Stations",
    "Restaurants",
    "Entertainment",
    "Coffee",
    "Shopping",
    "Hotels"
)

@Composable
fun LandingFavorites(
    modifier: Modifier = Modifier,
    quickLocations: List<QuickLocation>,
    onQuickLocationClicked: (QuickLocation) -> Unit
) {
    PaperLazyColumn(
        modifier = modifier.padding(end = 16.dp)
    ) {
        items(quickLocations) { location ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clickable { onQuickLocationClicked(location) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = location.label,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = location.address,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LandingCategories(
    modifier: Modifier = Modifier,
    onCategorySelected: (String) -> Unit
) {
    PaperLazyColumn(
        modifier = modifier.fillMaxSize().padding(end = 16.dp)
    ) {
        items(poiCategories.size) { index ->
            val category = poiCategories[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clickable { onCategorySelected(category) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getIconForCategory(category),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Text(category, modifier = Modifier.weight(1f))
            }
        }
    }
}

fun getIconForCategory(category: String): ImageVector {
    return when (category) {
        "Gas Stations" -> Icons.Outlined.LocalGasStation
        "Restaurants" -> Icons.Outlined.Restaurant
        "Entertainment" -> Icons.Outlined.Movie
        "Coffee" -> Icons.Outlined.LocalCafe
        "Shopping" -> Icons.Outlined.ShoppingCart
        "Hotels" -> Icons.Outlined.Hotel
        else -> throw IllegalArgumentException("Unknown category: $category")
    }
}