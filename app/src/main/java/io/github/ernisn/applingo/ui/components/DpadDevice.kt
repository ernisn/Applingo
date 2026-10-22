package io.github.ernisn.applingo.ui.components

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the device is driven by a D-pad rather than touch: a TV (some, and the TV emulator, list a
 * touchscreen anyway) or anything without a touchscreen. There, actions that sit below a long list
 * move to the top: D-pad Down only scrolls the list and never reaches a bar under it.
 */
@Composable
fun isDpadDevice(): Boolean {
    val context = LocalContext.current
    return remember {
        val tv = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
        tv || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
}
