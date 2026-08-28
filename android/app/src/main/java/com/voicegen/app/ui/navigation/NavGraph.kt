package com.voicegen.app.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.voicegen.app.VoiceGenApp
import com.voicegen.app.data.repository.ApiResult
import com.voicegen.app.di.LambdaViewModelFactory
import com.voicegen.app.player.AudioPlayerManager
import com.voicegen.app.ui.generation.GenerationScreen
import com.voicegen.app.ui.generation.GenerationViewModel
import com.voicegen.app.ui.home.HomeScreen
import com.voicegen.app.ui.home.HomeViewModel
import com.voicegen.app.ui.library.LibraryScreen
import com.voicegen.app.ui.library.LibraryViewModel
import com.voicegen.app.ui.record.RecordVoiceScreen
import com.voicegen.app.ui.record.RecordVoiceViewModel
import com.voicegen.app.ui.settings.SettingsScreen
import com.voicegen.app.ui.settings.SettingsViewModel
import com.voicegen.app.ui.voiceready.VoiceReadyScreen
import com.voicegen.app.ui.voices.VoiceLibraryScreen
import com.voicegen.app.ui.voices.VoiceLibraryViewModel
import com.voicegen.app.ui.voicesetup.VoiceSetupScreen
import com.voicegen.app.ui.voicesetup.VoiceSetupViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun VoiceGenNavGraph() {
    val context = LocalContext.current
    val app = context.applicationContext as VoiceGenApp
    val container = app.container
    val navController = rememberNavController()
    val player = remember { AudioPlayerManager(context) }

    // Spec section 11: "the user should NOT have to upload the voice again"
    // — if one is already saved, skip straight past Voice Setup to Home.
    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val selectedId = container.preferences.selectedVoiceIdFlow.first()
        startDestination = if (selectedId != null) Routes.Home.route else Routes.VoiceSetup.route
    }

    val resolvedStart = startDestination
    if (resolvedStart == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(navController = navController, startDestination = resolvedStart) {

        composable(Routes.VoiceSetup.route) {
            val vm: VoiceSetupViewModel = viewModel(factory = LambdaViewModelFactory {
                VoiceSetupViewModel(container.voiceRepository, container.appContext)
            })
            val uiState by vm.uiState.collectAsState()

            LaunchedEffect(uiState.createdVoice) {
                uiState.createdVoice?.let { voice ->
                    navController.navigate(Routes.VoiceReady.build(voice.id)) {
                        popUpTo(Routes.VoiceSetup.route) { inclusive = true }
                    }
                }
            }

            VoiceSetupScreen(
                uiState = uiState,
                onUploadFile = { uri, name -> vm.uploadFromUri(uri, name) },
                onRecordVoiceClick = { navController.navigate(Routes.RecordVoice.route) },
                onDismissError = { vm.clearError() },
            )
        }

        composable(Routes.RecordVoice.route) {
            val vm: RecordVoiceViewModel = viewModel(factory = LambdaViewModelFactory {
                RecordVoiceViewModel(container.appContext)
            })
            val setupVm: VoiceSetupViewModel = viewModel(factory = LambdaViewModelFactory {
                VoiceSetupViewModel(container.voiceRepository, container.appContext)
            })
            val uiState by vm.uiState.collectAsState()
            val setupState by setupVm.uiState.collectAsState()

            LaunchedEffect(setupState.createdVoice) {
                setupState.createdVoice?.let { voice ->
                    navController.navigate(Routes.VoiceReady.build(voice.id)) {
                        popUpTo(Routes.VoiceSetup.route) { inclusive = true }
                    }
                }
            }

            RecordVoiceScreen(
                uiState = uiState,
                onStart = { vm.startRecording() },
                onStop = { vm.stopRecording() },
                onPlay = { vm.playRecording() },
                onStopPlayback = { vm.stopPlayback() },
                onRecordAgain = { vm.recordAgain() },
                onUseRecording = { name ->
                    uiState.recordedFile?.let { file -> setupVm.uploadRecordedFile(file, name) }
                },
            )
        }

        composable(
            route = Routes.VoiceReady.route,
            arguments = listOf(navArgument("voiceId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("voiceId") ?: ""
            var voiceName by remember { mutableStateOf("My Voice") }
            val scope = rememberCoroutineScope()

            LaunchedEffect(id) {
                when (val result = container.voiceRepository.getVoice(id)) {
                    is ApiResult.Success -> voiceName = result.data.name
                    is ApiResult.Error -> Unit
                }
            }

            VoiceReadyScreen(
                voiceName = voiceName,
                onPlaySample = {
                    scope.launch {
                        val file = container.voiceRepository.downloadSampleToCache(id)
                        file?.let { player.playFile(it) }
                    }
                },
                onContinue = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.VoiceSetup.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Home.route) {
            val vm: HomeViewModel = viewModel(factory = LambdaViewModelFactory {
                HomeViewModel(container.voiceRepository, container.generationRepository, container.preferences, container.historyStore)
            })
            val uiState by vm.uiState.collectAsState()

            LaunchedEffect(Unit) { vm.loadSelectedVoice() }

            LaunchedEffect(uiState.noVoicesRemain) {
                // Spec section 21: "If no voice remains, return the user to Voice Setup."
                if (uiState.noVoicesRemain) {
                    navController.navigate(Routes.VoiceSetup.route) {
                        popUpTo(Routes.Home.route) { inclusive = true }
                    }
                }
            }

            LaunchedEffect(uiState.startedJobId) {
                uiState.startedJobId?.let { jobId ->
                    navController.navigate(Routes.Generation.build(jobId, uiState.outputFormat))
                    vm.consumeStartedJob()
                }
            }

            HomeScreen(
                uiState = uiState,
                onTextChanged = vm::onTextChanged,
                onImportText = vm::onImportedText,
                onSpeedChanged = vm::onSpeedChanged,
                onFormatChanged = vm::onOutputFormatChanged,
                onLanguageChanged = vm::onLanguageChanged,
                onGenerate = vm::generate,
                onDismissError = vm::clearError,
                onChangeVoice = { navController.navigate(Routes.VoiceLibrary.route) },
                onOpenLibrary = { navController.navigate(Routes.Library.route) },
                onOpenVoiceLibrary = { navController.navigate(Routes.VoiceLibrary.route) },
                onOpenSettings = { navController.navigate(Routes.Settings.route) },
            )
        }

        composable(
            route = Routes.Generation.route,
            arguments = listOf(
                navArgument("jobId") { type = NavType.StringType },
                navArgument("outputFormat") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            val outputFormat = backStackEntry.arguments?.getString("outputFormat") ?: "mp3"
            val vm: GenerationViewModel = viewModel(factory = LambdaViewModelFactory {
                GenerationViewModel(container.generationRepository)
            })
            val uiState by vm.uiState.collectAsState()

            LaunchedEffect(jobId) { vm.start(jobId, outputFormat) }

            GenerationScreen(
                uiState = uiState,
                onCancel = { vm.cancel(jobId) },
                onDone = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Home.route) { inclusive = true }
                    }
                },
                onPlay = { uiState.downloadedFile?.let { player.playFile(it) } },
                onShare = { uiState.downloadedFile?.let { shareAudioFile(context, it) } },
                onSave = { /* Already saved under app-local storage; nothing further needed for MVP. */ },
            )
        }

        composable(Routes.Library.route) {
            val vm: LibraryViewModel = viewModel(factory = LambdaViewModelFactory {
                LibraryViewModel(container.historyStore, container.generationRepository)
            })
            val uiState by vm.uiState.collectAsState()
            LaunchedEffect(Unit) { vm.load() }

            LibraryScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onPlay = { jobId, format -> vm.downloadAndPlay(jobId, format) { file -> player.playFile(file) } },
                onShare = { jobId, format -> vm.downloadAndPlay(jobId, format) { file -> shareAudioFile(context, file) } },
                onDelete = { jobId -> vm.delete(jobId) },
            )
        }

        composable(Routes.VoiceLibrary.route) {
            val vm: VoiceLibraryViewModel = viewModel(factory = LambdaViewModelFactory {
                VoiceLibraryViewModel(container.voiceRepository, container.preferences)
            })
            val uiState by vm.uiState.collectAsState()
            LaunchedEffect(Unit) { vm.load() }

            VoiceLibraryScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onAddVoice = { navController.navigate(Routes.VoiceSetup.route) },
                onSelect = { id -> vm.selectVoice(id); navController.popBackStack() },
                onDelete = { id -> vm.deleteVoice(id) },
                onDismissError = { vm.clearError() },
            )
        }

        composable(Routes.Settings.route) {
            val vm: SettingsViewModel = viewModel(factory = LambdaViewModelFactory {
                SettingsViewModel(container.preferences)
            })
            val uiState by vm.uiState.collectAsState()
            LaunchedEffect(Unit) { vm.load() }

            SettingsScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onBackendUrlChanged = vm::onBackendUrlChanged,
                onApiKeyChanged = vm::onApiKeyChanged,
                onTestConnection = vm::testConnection,
                onSave = vm::save,
            )
        }
    }
}

private fun shareAudioFile(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share generated audio"))
}
