package io.github.kolod.updater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
                UpdaterScreen(state = state, onCheck = viewModel::check, onCancel = viewModel::cancel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Checks only run while the app is visible; a rotation is not leaving the app.
        if (!isChangingConfigurations) viewModel.cancel()
    }
}
