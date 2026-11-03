package io.github.gumwoo.aiinterview

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gumwoo.aiinterview.alarm.AlarmScheduler
import io.github.gumwoo.aiinterview.audio.InterviewAudioService
import io.github.gumwoo.aiinterview.presentation.history.HistoryScreen
import io.github.gumwoo.aiinterview.presentation.history.HistoryViewModel
import io.github.gumwoo.aiinterview.presentation.home.HomeScreen
import io.github.gumwoo.aiinterview.presentation.home.IncomingCallScreen
import io.github.gumwoo.aiinterview.presentation.interview.IncomingCall
import io.github.gumwoo.aiinterview.presentation.interview.InterviewPhase
import io.github.gumwoo.aiinterview.presentation.interview.InterviewScreen
import io.github.gumwoo.aiinterview.presentation.interview.InterviewViewModel
import io.github.gumwoo.aiinterview.presentation.interview.SummaryScreen
import io.github.gumwoo.aiinterview.presentation.schedule.ScheduleScreen
import io.github.gumwoo.aiinterview.presentation.settings.SettingsScreen
import io.github.gumwoo.aiinterview.presentation.theme.CallTheme
import java.time.LocalDate

open class MainActivity : ComponentActivity() {

    private val interviewViewModel: InterviewViewModel by viewModels { factory() }
    private val historyViewModel: HistoryViewModel by viewModels { historyFactory() }
    private var micGranted by mutableStateOf(false)

    /** 전화 전용 화면인지. 전화 전용 화면은 전화가 끝나면 닫힌다. */
    protected open val isCallSurface = false

    /** 알림의 받기를 누른 경우. 화면이 보인 뒤에 Foreground Service를 시작해야 한다. */
    private var answerOnResume = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        micGranted = result[Manifest.permission.RECORD_AUDIO] == true
        beginInterview()
    }

    private var afterPermissionRequest: (() -> Unit)? = null
    private val followUpLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        micGranted = hasMicPermission()
        afterPermissionRequest?.invoke()
        afterPermissionRequest = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val app = application as InterviewApplication
        micGranted = hasMicPermission()
        val launchedByCall = handleCallIntent(intent)
        if (savedInstanceState == null) {
            // 음성 팩 내려받기 창이 뜰 수 있으므로 전화로 켜졌을 때는 묻지 않는다.
            if (!launchedByCall) app.speechInput.prepareKoreanPack()
            app.alarmScheduler.reschedule()
        }
        setContent {
            CallTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        var showSettings by rememberSaveable { mutableStateOf(false) }
                        var showSchedule by rememberSaveable { mutableStateOf(false) }
                        var showHistory by rememberSaveable { mutableStateOf(false) }
                        val state by interviewViewModel.state.collectAsStateWithLifecycle()
                        // 접거나 펴면 Activity가 다시 만들어지므로, 화면 상태는 면접 상태에서 매번 정한다.
                        LaunchedEffect(state.phase, state.incoming) {
                            val inCall = state.phase != InterviewPhase.IDLE && state.phase != InterviewPhase.FINISHED
                            app.callActive = inCall
                            when {
                                inCall || state.incoming != null ->
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                                else -> stopInterviewAudio()
                            }
                            if (isCallSurface && state.phase == InterviewPhase.IDLE && state.incoming == null) {
                                finishAndRemoveTask()
                            }
                        }
                        val incoming = state.incoming
                        when {
                            // 전화와 통화는 설정 화면이 열려 있어도 먼저 보여 준다.
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

                            incoming != null -> IncomingCallScreen(
                                settings = app.settingsStore.load(),
                                onAnswer = ::answerIncoming,
                                onDecline = {
                                    stopRinging()
                                    interviewViewModel.dismissIncoming()
                                },
                                onSnooze = {
                                    stopRinging()
                                    app.alarmScheduler.snooze(incoming.scheduledDate)
                                    interviewViewModel.dismissIncoming()
                                },
                                // 부재중 알림은 AlarmReceiver가 띄운다.
                                onTimeout = interviewViewModel::dismissIncoming,
                            )

                            showSettings -> SettingsScreen(
                                settingsStore = app.settingsStore,
                                tokenStore = app.tokenStore,
                                api = app.api,
                                requestNetworkAccess = { action -> requestPermissionsThen(localNetworkPermission(), action) },
                                onBack = { showSettings = false },
                            )

                            showSchedule -> ScheduleScreen(
                                store = app.scheduleStore,
                                scheduler = app.alarmScheduler,
                                requestCallPermissions = { action -> requestPermissionsThen(callPermissions(), action) },
                                onBack = { showSchedule = false },
                            )

                            showHistory -> {
                                LaunchedEffect(Unit) { historyViewModel.load() }
                                val history by historyViewModel.state.collectAsStateWithLifecycle()
                                HistoryScreen(
                                    state = history,
                                    onOpen = historyViewModel::open,
                                    onCloseDetail = historyViewModel::closeDetail,
                                    onDelete = historyViewModel::requestDelete,
                                    onRetry = historyViewModel::retry,
                                    onBack = { showHistory = false },
                                )
                            }

                            else -> HomeScreen(
                                settings = app.settingsStore.load(),
                                schedule = app.scheduleStore.load(),
                                canStart = canStart(app),
                                onStart = ::requestPermissionsAndStart,
                                onOpenSchedule = { showSchedule = true },
                                onOpenSettings = { showSettings = true },
                                onOpenHistory = { showHistory = true },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 전화 화면에서 통화 중이면 앱 아이콘으로 들어와도 그 통화로 돌아간다.
        val app = application as InterviewApplication
        if (!isCallSurface && app.callActive && app.callActivity == CallActivity::class.java) {
            startActivity(Intent(this, CallActivity::class.java))
            finish()
            return
        }
        if (answerOnResume) {
            answerOnResume = false
            answerIncoming()
        }
    }

    override fun onDestroy() {
        // 뒤로 가기로 앱을 닫으면 ViewModel도 사라지므로 마이크 알림을 남기지 않는다.
        if (isFinishing) {
            InterviewAudioService.stop(this)
            (application as InterviewApplication).callActive = false
        }
        super.onDestroy()
    }

    /** 알람에서 온 Intent를 처리하고, 다시 만들어져도 되풀이되지 않게 비운다. */
    private fun handleCallIntent(intent: Intent?): Boolean {
        val action = intent?.action
        if (action != ACTION_RING && action != ACTION_ANSWER) return false
        val date = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_DATE)?.let(LocalDate::parse)
        setIntent(Intent(this, MainActivity::class.java))
        val inCall = interviewViewModel.state.value.phase != InterviewPhase.IDLE
        if (inCall) return true
        interviewViewModel.showIncoming(IncomingCall(scheduledDate = date))
        answerOnResume = action == ACTION_ANSWER
        return true
    }

    private fun answerIncoming() {
        stopRinging()
        requestPermissionsAndStart()
    }

    private fun stopRinging() {
        val app = application as InterviewApplication
        app.incomingCallNotifier.stopRinging()
        app.alarmScheduler.cancelMissedTimer()
    }

    private fun canStart(app: InterviewApplication) =
        app.settingsStore.load().serverUrl.isNotBlank() && app.tokenStore.hasToken()

    private fun callPermissions(): List<String> =
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS) + localNetworkPermission()

    /** 마이크는 사용자가 면접을 시작하거나 일정을 켤 때 요청한다. */
    private fun requestPermissionsAndStart() {
        val missing = callPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            micGranted = true
            beginInterview()
        } else {
            // 권한 창은 잠금 화면 위에 뜨지 않으므로 잠금을 먼저 풀게 한다.
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requestPermissionsThen(permissions: List<String>, action: () -> Unit) {
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            action()
        } else {
            afterPermissionRequest = action
            followUpLauncher.launch(missing.toTypedArray())
        }
    }

    /** API 37부터 사설 주소 접속에 필요한 권한이다. 없으면 연결이 시간 초과로 실패한다. */
    private fun localNetworkPermission(): List<String> =
        if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_SDK) listOf(Manifest.permission.ACCESS_LOCAL_NETWORK) else emptyList()

    private fun beginInterview() {
        (application as InterviewApplication).callActivity = javaClass
        if (hasMicPermission()) {
            InterviewAudioService.start(this)
        }
        interviewViewModel.answer()
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

    private fun historyFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { HistoryViewModel((application as InterviewApplication).api) }
    }

    companion object {
        const val ACTION_RING = "io.github.gumwoo.aiinterview.action.RING"
        const val ACTION_ANSWER = "io.github.gumwoo.aiinterview.action.ANSWER"
        private const val LOCAL_NETWORK_PERMISSION_SDK = 37
    }
}
