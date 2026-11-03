package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.data.AppTypeFilter
import io.github.ernisn.applingo.ui.theme.ControlShape
import io.github.ernisn.applingo.ui.theme.TRANSLUCENT_FILL_ALPHA

/** All / User / System / Favorites, the app list's tabs (and the default one in settings). */
@Composable
fun AppTypeFilterRow(
    selected: AppTypeFilter,
    onSelect: (AppTypeFilter) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val filters = AppTypeFilter.entries
    SegmentedTabRow(
        labels = filters.map { stringResource(it.labelRes()) },
        selected = filters.indexOf(selected),
        onSelect = { onSelect(filters[it]) },
        modifier = modifier,
        enabled = enabled,
    )
}

/**
 * Tabs as one row of equal-width segments, the same on every page. Labels shrink to fit instead of
 * wrapping: four of them share a landscape pane, and some translations are long.
 */
@Composable
fun SegmentedTabRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, labels.size, baseShape = ControlShape),
                colors = segmentedColors(),
                contentPadding = PaddingValues(horizontal = 4.dp),
                // The selected segment's fill is enough; a check mark would take a third of its width.
                icon = {},
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                )
            }
        }
    }
}

/**
 * Every segmented row's colours: the selected segment translucent, like the island's indicator, so
 * what scrolls on behind a frosted header shows through it as it does through the others.
 */
@Composable
fun segmentedColors(): SegmentedButtonColors {
    val fill = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = TRANSLUCENT_FILL_ALPHA)
    return SegmentedButtonDefaults.colors(activeContainerColor = fill, disabledActiveContainerColor = fill)
}

fun AppTypeFilter.labelRes(): Int = when (this) {
    AppTypeFilter.All -> R.string.filter_all
    AppTypeFilter.User -> R.string.filter_user
    AppTypeFilter.System -> R.string.filter_system
    AppTypeFilter.Favorites -> R.string.filter_favorites
}
