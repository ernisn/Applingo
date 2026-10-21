package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.data.AppInfo

/**
 * An app's package name, led by small marks for a system app and a disabled app. A plain user app
 * has none, and an app's own language already shows as its badge.
 *
 * Monochrome glyphs in the text color rather than colored dots: they look the same with every theme
 * color and in dark mode, don't rely on telling colors apart, and red already means a language
 * problem here.
 */
@Composable
fun PackageLine(app: AppInfo, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (app.isSystem) Mark(R.drawable.ic_mark_system, R.string.app_badge_system)
        if (!app.isEnabled) Mark(R.drawable.ic_mark_disabled, R.string.app_badge_disabled)
        Text(
            text = app.packageName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Mark(icon: Int, description: Int) {
    Icon(
        painter = painterResource(icon),
        contentDescription = stringResource(description),
        modifier = Modifier.padding(end = 4.dp).size(14.dp),
    )
}
