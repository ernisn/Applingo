package io.github.ernisn.applingo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.ui.applist.AppListPane
import io.github.ernisn.applingo.ui.detail.AppDetailPane
import io.github.ernisn.applingo.ui.detail.EmptyDetailPane
import io.github.ernisn.applingo.ui.picker.LocalePickerPane
import kotlinx.coroutines.launch

/**
 * App list | app detail | language picker. One pane at a time on phones, list + detail side by side
 * from medium widths (landscape, tablets, unfolded foldables); back and predictive back are handled
 * by the scaffold navigator.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val restartLabel = stringResource(R.string.action_restart_app)

    // Keep showing the last app while panes animate away, instead of flashing an empty pane.
    var lastPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = navigator.currentDestination?.contentKey
    if (selected != null && selected != lastPackage) lastPackage = selected

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

    val listHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden
    val detailHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Hidden

    Box(Modifier.fillMaxSize()) {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    AppListPane(
                        viewModel = viewModel,
                        selectedPackage = selected,
                        onOpenApp = { packageName ->
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, packageName) }
                        },
                    )
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding(),
        )
    }
}
