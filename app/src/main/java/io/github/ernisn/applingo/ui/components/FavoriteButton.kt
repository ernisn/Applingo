package io.github.ernisn.applingo.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.ernisn.applingo.R

/** A star that adds to or removes from favorites: filled in the theme color when on, an outline when off. */
@Composable
fun FavoriteButton(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconToggleButton(checked = favorite, onCheckedChange = { onToggle() }, modifier = modifier) {
        Icon(
            painter = painterResource(if (favorite) R.drawable.ic_star else R.drawable.ic_star_border),
            contentDescription = stringResource(if (favorite) R.string.action_unfavorite else R.string.action_favorite),
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
