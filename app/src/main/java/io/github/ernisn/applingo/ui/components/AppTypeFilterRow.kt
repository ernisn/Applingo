package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
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

/**
 * Launchable / User / System / All as one row of equal-width segments. Labels shrink to fit instead
 * of wrapping: four of them share a landscape pane, and some translations are long.
 */
@Composable
fun AppTypeFilterRow(
    selected: AppTypeFilter,
    onSelect: (AppTypeFilter) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val filters = AppTypeFilter.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        filters.forEachIndexed { index, filter ->
            SegmentedButton(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, filters.size, baseShape = ControlShape),
                contentPadding = PaddingValues(horizontal = 4.dp),
                // The selected segment's fill is enough; a check mark would take a third of its width.
                icon = {},
            ) {
                Text(
                    text = stringResource(filter.labelRes()),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                )
            }
        }
    }
}

fun AppTypeFilter.labelRes(): Int = when (this) {
    AppTypeFilter.Launchable -> R.string.filter_launchable
    AppTypeFilter.User -> R.string.filter_user
    AppTypeFilter.System -> R.string.filter_system
    AppTypeFilter.All -> R.string.filter_all
}
