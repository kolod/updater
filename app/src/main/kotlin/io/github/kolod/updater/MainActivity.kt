package io.github.kolod.updater

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kolod.updater.ui.UpdaterScreen
import io.github.kolod.updater.ui.UpdaterTheme

class MainActivity : ComponentActivity() {
    private val viewModel: UpdaterViewModel by viewModels { UpdaterViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A fresh launch is the only automatic trigger (constitution, Principle III); rotation and
        // process restoration come with a saved state and do not check again.
        if (savedInstanceState == null) viewModel.check()
        setContent {
            UpdaterTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    viewModel.onInstallPermissionResult()
                }
                // Send the user to the "install unknown apps" page once; the result resumes the queue.
                LaunchedEffect(state.permissionRequested) {
                    if (state.permissionRequested) {
                        viewModel.permissionPromptShown()
                        settings.launch(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
                        )
                    }
                }
                UpdaterScreen(
                    state = state,
                    onCheck = viewModel::check,
                    onCancelCheck = viewModel::cancel,
                    onInstall = viewModel::install,
                    onUpdateAll = viewModel::updateAll,
                    onCancelInstall = viewModel::cancelInstall,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onEnteredApp()
    }

    override fun onStop() {
        super.onStop()
        // Checks and downloads only run while the app is visible; a rotation is not leaving the app.
        if (!isChangingConfigurations) viewModel.onLeftApp()
    }
}
