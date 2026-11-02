package io.github.gumwoo.aiinterview

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gumwoo.aiinterview.audio.InterviewAudioService
import io.github.gumwoo.aiinterview.presentation.home.HomeScreen
import io.github.gumwoo.aiinterview.presentation.home.IncomingCallScreen
import io.github.gumwoo.aiinterview.presentation.interview.InterviewPhase
import io.github.gumwoo.aiinterview.presentation.interview.InterviewScreen
import io.github.gumwoo.aiinterview.presentation.interview.InterviewViewModel
import io.github.gumwoo.aiinterview.presentation.interview.SummaryScreen
import io.github.gumwoo.aiinterview.presentation.settings.SettingsScreen
import io.github.gumwoo.aiinterview.presentation.theme.CallTheme

class MainActivity : ComponentActivity() {

    private val interviewViewModel: InterviewViewModel by viewModels { factory() }
    private var micGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        micGranted = result[Manifest.permission.RECORD_AUDIO] == true
        beginInterview()
    }

    private var afterLocalNetworkRequest: (() -> Unit)? = null
    private val localNetworkLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        afterLocalNetworkRequest?.invoke()
        afterLocalNetworkRequest = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val app = application as InterviewApplication
        micGranted = hasMicPermission()
        if (savedInstanceState == null) {
            app.speechInput.prepareKoreanPack()
        }
        setContent {
            CallTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        var showSettings by rememberSaveable { mutableStateOf(false) }
                        var ringing by rememberSaveable { mutableStateOf(false) }
                        val state by interviewViewModel.state.collectAsStateWithLifecycle()
                        // 접거나 펴면 Activity가 다시 만들어지므로, 화면 켜짐은 면접 상태에서 매번 정한다.
                        LaunchedEffect(state.phase) {
                            if (state.phase == InterviewPhase.IDLE || state.phase == InterviewPhase.FINISHED) {
                                stopInterviewAudio()
                            } else {
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        }
                        when {
                            showSettings -> SettingsScreen(
                                settingsStore = app.settingsStore,
                                tokenStore = app.tokenStore,
                                api = app.api,
                                requestNetworkAccess = ::requestLocalNetworkThen,
                                onBack = { showSettings = false },
                            )

                            state.phase == InterviewPhase.FINISHED -> SummaryScreen(
                                summary = state.summary,
                                durationSeconds = state.callDurationSeconds,
                                onDone = interviewViewModel::reset,
                            )

                            state.phase != InterviewPhase.IDLE -> InterviewScreen(
                                state = state,
                                micGranted = micGranted,
                                cloudRecognition = app.speechInput.usedCloudRecognition,
                                viewModel = interviewViewModel,
                                onLeave = interviewViewModel::reset,
                            )

                            ringing -> IncomingCallScreen(
                                settings = app.settingsStore.load(),
                                onAnswer = {
                                    ringing = false
                                    requestPermissionsAndStart()
                                },
                                onDecline = { ringing = false },
                            )

                            else -> HomeScreen(
                                settings = app.settingsStore.load(),
                                canStart = canStart(app),
                                onStart = { ringing = true },
                                onOpenSettings = { showSettings = true },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // 뒤로 가기로 앱을 닫으면 ViewModel도 사라지므로 마이크 알림을 남기지 않는다.
        if (isFinishing) {
            InterviewAudioService.stop(this)
        }
        super.onDestroy()
    }

    private fun canStart(app: InterviewApplication) =
        app.settingsStore.load().serverUrl.isNotBlank() && app.tokenStore.hasToken()

    /** 마이크는 사용자가 면접을 시작할 때 요청한다. */
    private fun requestPermissionsAndStart() {
        val missing = (listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS) + localNetworkPermission())
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            micGranted = true
            beginInterview()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requestLocalNetworkThen(action: () -> Unit) {
        val missing = localNetworkPermission().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            action()
        } else {
            afterLocalNetworkRequest = action
            localNetworkLauncher.launch(missing.toTypedArray())
        }
    }

    /** API 37부터 사설 주소 접속에 필요한 권한이다. 없으면 연결이 시간 초과로 실패한다. */
    private fun localNetworkPermission(): List<String> =
        if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_SDK) listOf(Manifest.permission.ACCESS_LOCAL_NETWORK) else emptyList()

    private fun beginInterview() {
        if (hasMicPermission()) {
            InterviewAudioService.start(this)
        }
        interviewViewModel.start()
    }

    private fun stopInterviewAudio() {
        InterviewAudioService.stop(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hasMicPermission() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun factory(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = application as InterviewApplication
            InterviewViewModel(
                api = app.api,
                output = app.speechOutput,
                input = app.speechInput,
                settings = { app.settingsStore.load() },
            )
        }
    }

    private companion object {
        const val LOCAL_NETWORK_PERMISSION_SDK = 37
    }
}
