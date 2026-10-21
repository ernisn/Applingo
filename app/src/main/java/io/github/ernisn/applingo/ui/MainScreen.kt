package io.github.ernisn.applingo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.ui.applist.AppListPane
import io.github.ernisn.applingo.ui.detail.AppDetailPane
import io.github.ernisn.applingo.ui.detail.EmptyDetailPane
import io.github.ernisn.applingo.ui.managed.ManagedPane
import io.github.ernisn.applingo.ui.picker.LocalePickerPane
import io.github.ernisn.applingo.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class Destination(val label: Int) { Apps(R.string.nav_apps), Managed(R.string.nav_managed), Settings(R.string.nav_settings) }

/** From this width on (landscape phones, unfolded foldables, tablets) navigation moves to a side rail. */
private const val RAIL_MIN_WIDTH_DP = 600

/**
 * Top-level navigation: a bottom bar on narrow screens, a side rail on wider ones. Everything is drawn
 * edge to edge; each piece of content keeps clear of system bars, the display cutout and the keyboard
 * only where it actually overlaps them (see [safeArea]), so the background shows through instead of
 * black bars. Each destination keeps its own state (scroll position, open app) while switching.
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.Apps) }
    val stateHolder = rememberSaveableStateHolder()
    val snackbarHostState = remember { SnackbarHostState() }
    val restartLabel = stringResource(R.string.action_restart_app)
    val useRail = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp() >= RAIL_MIN_WIDTH_DP.dp
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.restartPackage?.let { restartLabel },
                withDismissAction = message.restartPackage == null,
                duration = if (message.restartPackage != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) message.restartPackage?.let(viewModel::restartApp)
        }
    }

    // Back from another destination returns to the app list before leaving Applingo.
    BackHandler(enabled = destination != Destination.Apps) { destination = Destination.Apps }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            stateHolder.SaveableStateProvider(destination.name) {
                when (destination) {
                    Destination.Apps -> AppsListDetail(viewModel) { selected, onOpen ->
                        AppListPane(viewModel = viewModel, selectedPackage = selected, onOpenApp = onOpen)
                    }
                    Destination.Managed -> AppsListDetail(viewModel) { selected, onOpen ->
                        ManagedPane(viewModel = viewModel, selectedPackage = selected, onOpenApp = onOpen)
                    }
                    Destination.Settings -> SettingsScreen(viewModel)
                }
            }
            // With the rail the content reaches the end and bottom edges; with the bottom bar only the sides.
            val snackbarInsets = if (useRail) {
                WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom)
            } else {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(snackbarInsets),
            ) { data ->
                // The default inverse colors put a light snackbar on a dark theme.
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                    dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (useRail) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)) {
                    Spacer(Modifier.weight(1f))
                    Destination.entries.forEach { item ->
                        NavigationRailItem(
                            selected = item == destination,
                            onClick = { destination = item },
                            icon = { DestinationIcon(item) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                content(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                content(Modifier.weight(1f).fillMaxWidth())
                NavigationBar(windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)) {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == destination,
                            onClick = { destination = item },
                            icon = { DestinationIcon(item) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Keeps content inside the safe drawing area (system bars, display cutout, keyboard), but only on the
 * sides where this element actually overlaps them: a pane in the middle of the screen gets no padding.
 */
fun Modifier.safeArea(): Modifier = fitInside(WindowInsetsRulers.SafeDrawing.current)

@Composable
private fun DestinationIcon(destination: Destination) = when (destination) {
    Destination.Apps -> Icon(painterResource(R.drawable.ic_apps), contentDescription = null)
    Destination.Managed -> Icon(painterResource(R.drawable.ic_translate), contentDescription = null)
    Destination.Settings -> Icon(Icons.Filled.Settings, contentDescription = null)
}

/**
 * List | app detail | language picker. One pane at a time on phones, list + detail side by side from
 * medium widths (landscape, tablets, unfolded foldables); back and predictive back are handled by the
 * scaffold navigator.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AppsListDetail(
    viewModel: MainViewModel,
    listPane: @Composable (selected: String?, onOpen: (String) -> Unit) -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

    // Keep showing the last app while panes animate away, instead of flashing an empty pane.
    var lastPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = navigator.currentDestination?.contentKey
    if (selected != null && selected != lastPackage) lastPackage = selected

    val listHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden
    val detailHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Hidden

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                listPane(selected) { packageName ->
                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, packageName) }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val packageName = lastPackage
                if (packageName == null) {
                    EmptyDetailPane()
                } else {
                    AppDetailPane(
                        viewModel = viewModel,
                        packageName = packageName,
                        showBack = listHidden,
                        onBack = { scope.launch { navigator.navigateBack() } },
                        onEditLanguages = {
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Extra, packageName) }
                        },
                    )
                }
            }
        },
        extraPane = {
            AnimatedPane {
                lastPackage?.let { packageName ->
                    LocalePickerPane(
                        viewModel = viewModel,
                        packageName = packageName,
                        closeAsBack = detailHidden,
                        onClose = { scope.launch { navigator.navigateBack() } },
                    )
                }
            }
        },
    )
}
