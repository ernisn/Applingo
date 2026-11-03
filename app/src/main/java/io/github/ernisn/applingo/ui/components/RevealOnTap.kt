package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Scrolls a list row that is cut off at the top or bottom of its list just far enough to show it
 * whole, for rows that stay on screen when tapped: the highlighted app next to its detail, a row
 * ticked while selecting. D-pad focus does this by itself.
 */
@OptIn(ExperimentalFoundationApi::class)
class Revealer internal constructor(internal val requester: BringIntoViewRequester, private val scope: CoroutineScope) {
    fun reveal() {
        scope.launch { requester.bringIntoView() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberRevealer(): Revealer {
    val scope = rememberCoroutineScope()
    return remember { Revealer(BringIntoViewRequester(), scope) }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.revealable(revealer: Revealer): Modifier = bringIntoViewRequester(revealer.requester)
