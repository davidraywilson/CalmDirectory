package com.example.helloworld

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.helloworld.data.DistanceUnit
import com.example.helloworld.data.SortMode
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.slider.SliderMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterControls(
    modifier: Modifier = Modifier,
    viewModel: HomeFiltersViewModel = viewModel(),
) {
    val interactionSource = remember { MutableInteractionSource() }

    val searchRadius by viewModel.searchRadius.collectAsState()
    var sliderValue by remember(searchRadius) { mutableStateOf(searchRadius.toFloat()) }
    val distanceUnit by viewModel.distanceUnit.collectAsState()
    val openNow by viewModel.openNow.collectAsState()
    val openIn1Hour by viewModel.openIn1Hour.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val showRating by viewModel.showRating.collectAsState()
    val maxPriceLevel by viewModel.maxPriceLevel.collectAsState()

    Column(modifier = modifier) {
        Spacer(modifier = Modifier.padding(8.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val radiusText = if (distanceUnit == DistanceUnit.METRIC) {
                val km = sliderValue.toInt() * 1.60934
                "Search Radius: %.1f km".format(Locale.US, km)
            } else {
                "Search Radius: ${sliderValue.toInt()} miles"
            }

            Text(text = radiusText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            SliderMMD(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { viewModel.setSearchRadius(sliderValue.toInt()) },
                valueRange = 1f..20f,
                steps = 18,
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            HorizontalDividerMMD(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        Spacer(modifier = Modifier.padding(4.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(text = "Search filters", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.padding(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Open now", fontSize = 16.sp, modifier = Modifier.weight(1f))
                SwitchMMD(
                    checked = openNow,
                    onCheckedChange = { viewModel.setOpenNow(it) }
                )
            }
            Spacer(modifier = Modifier.padding(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Open for the next hour", fontSize = 16.sp, modifier = Modifier.weight(1f))
                SwitchMMD(
                    checked = openIn1Hour,
                    onCheckedChange = { viewModel.setOpenIn1Hour(it) }
                )
            }

            Spacer(modifier = Modifier.padding(8.dp))
            Text(text = "Sort by", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setSortMode(SortMode.RELEVANCE) }
            ) {
                RadioButtonMMD(
                    selected = sortMode == SortMode.RELEVANCE,
                    onClick = null,
                    modifier = Modifier.semantics { contentDescription = "Relevance" }
                )
                Text(text = "Relevance", modifier = Modifier.padding(start = 8.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clickable { viewModel.setSortMode(SortMode.DISTANCE) }
            ) {
                RadioButtonMMD(
                    selected = sortMode == SortMode.DISTANCE,
                    onClick = null,
                    modifier = Modifier.semantics { contentDescription = "Distance" }
                )
                Text(text = "Distance", modifier = Modifier.padding(start = 8.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clickable { viewModel.setSortMode(SortMode.RATING) }
            ) {
                RadioButtonMMD(
                    selected = sortMode == SortMode.RATING,
                    onClick = null,
                    modifier = Modifier.semantics { contentDescription = "Rating" }
                )
                Text(text = "Rating", modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(modifier = Modifier.padding(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Show ratings", fontSize = 14.sp, modifier = Modifier.weight(1f))
                SwitchMMD(
                    checked = showRating,
                    onCheckedChange = { viewModel.setShowRating(it) }
                )
            }

            Spacer(modifier = Modifier.padding(8.dp))
            Text(text = "Max price", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.padding(4.dp))
            val priceOptions: List<Pair<Int?, String>> = listOf(
                null to "Any",
                1 to "$",
                2 to "$$",
                3 to "$$$",
                4 to "$$$$",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                priceOptions.forEachIndexed { index, (level, label) ->
                    SegmentedButton(
                        selected = maxPriceLevel == level,
                        onClick = { viewModel.setMaxPriceLevel(level) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = priceOptions.size
                        ),
                        label = {
                            Text(
                                text = label,
                                color = if (maxPriceLevel == level) Color.White else Color.Black,
                            )
                        },
                        icon = {},
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Color.Black,
                            activeContentColor = Color.White,
                            inactiveContainerColor = Color.White,
                            inactiveContentColor = Color.Black,
                        ),
                        modifier = Modifier.semantics { contentDescription = "Max price $label" }
                    )
                }
            }
            Spacer(modifier = Modifier.padding(8.dp))
        }
    }
}
