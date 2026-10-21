package io.github.ernisn.applingo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.ui.applist.AppListPane
import io.github.ernisn.applingo.ui.detail.AppDetailPane
import io.github.ernisn.applingo.ui.detail.EmptyDetailPane
import io.github.ernisn.applingo.ui.managed.ManagedPane
import io.github.ernisn.applingo.ui.picker.LocalePickerPane
import io.github.ernisn.applingo.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class Destination(val label: Int) { Apps(R.string.nav_apps), Managed(R.string.nav_managed), Settings(R.string.nav_settings) }

/**
 * Top-level navigation: a bottom bar on phones, a rail on wider windows. Each destination keeps its
 * own state (scroll position, open app) while you switch between them.
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.Apps) }
    val stateHolder = rememberSaveableStateHolder()
    val snackbarHostState = remember { SnackbarHostState() }
    val restartLabel = stringResource(R.string.action_restart_app)
    val layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())

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

    Box(Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            layoutType = layoutType,
            navigationSuiteItems = {
                Destination.entries.forEach { item ->
                    item(
                        selected = item == destination,
                        onClick = { destination = item },
                        icon = {
                            when (item) {
                                Destination.Apps -> Icon(painterResource(R.drawable.ic_apps), contentDescription = null)
                                Destination.Managed -> Icon(painterResource(R.drawable.ic_translate), contentDescription = null)
                                Destination.Settings -> Icon(Icons.Filled.Settings, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            },
        ) {
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
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = if (layoutType == NavigationSuiteType.NavigationBar) 80.dp else 0.dp),
        )
    }
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
