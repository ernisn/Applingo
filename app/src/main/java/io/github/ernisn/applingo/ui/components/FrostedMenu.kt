package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.ernisn.applingo.ui.theme.FieldShape

/**
 * A menu dropping down from a search field, frosted like the island and the header it opens from:
 * the page shows through it, blurred ([frostedFill]), and a hairline marks its edge instead of a
 * shadow. Its own window, so it frosts itself with the main window's recording.
 */
@Composable
fun FrostedDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        // Applied inside the menu's clip, to the column of items filling it.
        modifier = Modifier.frostedFill(MaterialTheme.colorScheme.surfaceContainer),
        shape = FieldShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = frostedBorder(),
        content = content,
    )
}
