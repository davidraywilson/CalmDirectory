package com.example.helloworld

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helloworld.data.DistanceUnit
import com.example.helloworld.util.decomposeStars
import com.example.helloworld.util.formatKilometers
import com.example.helloworld.util.formatMiles
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD

@Composable
fun SearchScreen(
    searchResults: List<Poi>,
    showRating: Boolean,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
    onPoiSelected: (Poi) -> Unit
) {
    LazyColumnMMD(
        modifier = modifier
    ) {
        items(searchResults) { poi ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPoiSelected(poi) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = poi.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val priceText = poi.priceLevel?.takeIf { it in 1..4 }?.let { "$".repeat(it) }
                    val distanceText = poi.distanceMeters?.let {
                        if (distanceUnit == DistanceUnit.METRIC) formatKilometers(it) else formatMiles(it)
                    }
                    val showStars = showRating && poi.rating != null
                    val tail = listOfNotNull(distanceText, priceText).joinToString(" · ")

                    if (showStars || tail.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (showStars) {
                                val stars = decomposeStars(poi.rating!!)
                                repeat(stars.full) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                if (stars.half) {
                                    Icon(
                                        imageVector = Icons.Filled.StarHalf,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                repeat(stars.empty) {
                                    Icon(
                                        imageVector = Icons.Outlined.StarBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                if (poi.userRatingCount != null) {
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(
                                        text = "(${"%,d".format(poi.userRatingCount)})",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (showStars && tail.isNotEmpty()) {
                                Text(
                                    text = " · ",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (tail.isNotEmpty()) {
                                Text(
                                    text = tail,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = poi.address.street,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${poi.address.city}, ${poi.address.state} ${poi.address.zip}, ${poi.address.country}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null
                )
            }

            if (poi != searchResults.last()) {
                DashedDivider(
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}
