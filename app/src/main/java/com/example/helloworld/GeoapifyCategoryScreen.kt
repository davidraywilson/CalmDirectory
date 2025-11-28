package com.example.helloworld

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD

private data class GeoapifyTopLevelCategory(
    val key: String,
    val label: String
)

// Top-level Geoapify categories we support for scoping free-text searches.
private val geoapifyTopLevelCategories = listOf(
    GeoapifyTopLevelCategory("catering", "Food & drinks"),
    GeoapifyTopLevelCategory("commercial", "Shopping & commerce"),
    GeoapifyTopLevelCategory("service", "Services"),
    GeoapifyTopLevelCategory("entertainment", "Entertainment"),
    GeoapifyTopLevelCategory("leisure", "Leisure & recreation"),
    GeoapifyTopLevelCategory("accommodation", "Accommodation"),
    GeoapifyTopLevelCategory("amenity", "Amenities")
)

@Composable
fun GeoapifyCategoryScreen(
    modifier: Modifier = Modifier,
    onCategorySelected: (String) -> Unit
) {
    LazyColumnMMD(
        modifier = modifier
    ) {
        items(geoapifyTopLevelCategories) { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategorySelected(category.key) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = category.label, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null
                )
            }

            if (category != geoapifyTopLevelCategories.last()) {
                HorizontalDividerMMD(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp
                )
            }
        }
    }
}
