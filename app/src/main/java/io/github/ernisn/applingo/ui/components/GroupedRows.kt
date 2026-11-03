package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import io.github.ernisn.applingo.ui.theme.CardRadius
import io.github.ernisn.applingo.ui.theme.RowShape

/*
 * Rows of a lazy list drawn as one card per group, the same filled cards as the detail page: each row
 * paints its own slice of the card (rounded outer corners, straight joins), so a long group such as
 * All languages still composes lazily.
 */

/** Where a row sits in its group. */
enum class GroupPosition {
    Only, First, Middle, Last;

    companion object {
        fun of(index: Int, count: Int): GroupPosition = when {
            count == 1 -> Only
            index == 0 -> First
            index == count - 1 -> Last
            else -> Middle
        }
    }
}

/** The row's slice of its group's card. Goes before the row's clickable, so the ripple stays inside the card. */
@Composable
fun Modifier.groupedRow(position: GroupPosition): Modifier {
    val shape = when (position) {
        GroupPosition.Only -> RoundedCornerShape(CardRadius)
        GroupPosition.First -> RoundedCornerShape(topStart = CardRadius, topEnd = CardRadius)
        GroupPosition.Middle -> RectangleShape
        GroupPosition.Last -> RoundedCornerShape(bottomStart = CardRadius, bottomEnd = CardRadius)
    }
    return padding(horizontal = 16.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
}

/** List items on a group's card, which already paints the background. */
@Composable
fun groupedRowColors(): ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

/**
 * An app row in a list: in from the pane's edges as far as the cards and the search field, and when
 * [highlighted] (the app open beside the list, or picked) filled as a rounded card of its own rather
 * than a band across the pane. A little apart from the next row, so picked rows in a run stay
 * separate cards. Goes before the row's clickable, so its ripple and D-pad focus take the same
 * shape. The row's list item paints no background ([groupedRowColors]).
 */
@Composable
fun Modifier.appRow(highlighted: Boolean): Modifier {
    val fill = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    return padding(horizontal = 16.dp, vertical = 2.dp).clip(RowShape).background(fill)
}

/** A group's title, above its card and lined up with the text in it. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 8.dp),
    )
}
