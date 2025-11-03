package com.example.helloworld

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD

val poiCategories = listOf(
    "Gas Stations",
    "Restaurants",
    "Entertainment",
    "Coffee",
    "Shopping",
    "Hotels"
)

@Composable
fun LandingScreen(
    modifier: Modifier = Modifier,
    onCategorySelected: (String) -> Unit
) {
    LazyColumnMMD(
        modifier = modifier
    ) {
        items(poiCategories) { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategorySelected(category) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getIconForCategory(category),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Text(category, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null
                )
            }
            HorizontalDividerMMD(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp
            )
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
