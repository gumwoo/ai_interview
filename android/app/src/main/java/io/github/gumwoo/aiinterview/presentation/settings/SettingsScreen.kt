package io.github.gumwoo.aiinterview.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.gumwoo.aiinterview.R
import io.github.gumwoo.aiinterview.data.ApiException
import io.github.gumwoo.aiinterview.data.InterviewApi
import io.github.gumwoo.aiinterview.data.InterviewSettings
import io.github.gumwoo.aiinterview.data.InterviewSettingsStore
import io.github.gumwoo.aiinterview.presentation.theme.CallColors
import io.github.gumwoo.aiinterview.presentation.theme.PillButton
import io.github.gumwoo.aiinterview.security.ApiTokenStore
import kotlinx.coroutines.launch

private const val MIN_TOKEN_LENGTH = 32
private val DURATIONS = listOf(10, 15, 20, 30)
private val DIFFICULTIES = listOf("intermediate", "advanced")

@Composable
fun SettingsScreen(
    settingsStore: InterviewSettingsStore,
    tokenStore: ApiTokenStore,
    api: InterviewApi,
    requestNetworkAccess: (() -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val saved = remember { settingsStore.load() }
    var serverUrl by rememberSaveable { mutableStateOf(saved.serverUrl) }
    var token by remember { mutableStateOf("") }
    var topics by rememberSaveable { mutableStateOf(saved.topics) }
    var difficulty by rememberSaveable { mutableStateOf(saved.difficulty) }
    var duration by rememberSaveable { mutableStateOf(saved.durationMinutes) }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    var checking by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    fun save(): Boolean {
        val error = when {
            !InterviewSettings.isValidServerUrl(serverUrl) -> R.string.settings_invalid_url
            topics.isEmpty() -> R.string.settings_invalid_topics
            token.isNotEmpty() && token.length < MIN_TOKEN_LENGTH -> R.string.settings_invalid_token
            token.isEmpty() && !tokenStore.hasToken() -> R.string.settings_invalid_token
            else -> null
        }
        if (error != null) {
            message = error
            return false
        }
        settingsStore.save(InterviewSettings(serverUrl.trim(), topics, difficulty, duration))
        if (token.isNotEmpty()) {
            tokenStore.save(token)
            token = ""
        }
        message = R.string.settings_saved
        return true
    }

    fun checkConnection() {
        if (checking || !save()) return
        checking = true
        requestNetworkAccess {
            scope.launch {
                message = try {
                    api.checkConnection()
                    R.string.settings_check_ok
                } catch (ex: ApiException) {
                    if (ex is ApiException.Unauthorized) R.string.error_unauthorized else R.string.error_network
                }
                checking = false
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)
                TextAction(stringResource(R.string.settings_close), onBack)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SectionTitle(stringResource(R.string.settings_section_server))
                LineField(
                    label = stringResource(R.string.settings_server_url),
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    placeholder = stringResource(R.string.settings_server_url_hint),
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(20.dp))
                LineField(
                    label = stringResource(R.string.settings_api_token),
                    value = token,
                    onValueChange = { token = it },
                    placeholder = stringResource(
                        if (tokenStore.hasToken()) R.string.settings_token_saved else R.string.settings_token_hint,
                    ),
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        message?.let { stringResource(it) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = CallColors.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    TextAction(stringResource(R.string.settings_check), ::checkConnection)
                }

                SectionTitle(stringResource(R.string.settings_section_interview))
                TagGroup(stringResource(R.string.settings_topics)) {
                    InterviewSettings.DEFAULT_TOPICS.forEach { topic ->
                        Tag(
                            text = stringResource(topicLabel(topic)),
                            selected = topic in topics,
                            onClick = { topics = if (topic in topics) topics - topic else topics + topic },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                TagGroup(stringResource(R.string.settings_difficulty)) {
                    DIFFICULTIES.forEach { value ->
                        Tag(stringResource(difficultyLabel(value)), difficulty == value, { difficulty = value })
                    }
                }
                Spacer(Modifier.height(24.dp))
                TagGroup(stringResource(R.string.settings_duration)) {
                    DURATIONS.forEach { minutes ->
                        Tag(stringResource(R.string.home_duration_value, minutes), duration == minutes, { duration = minutes })
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
            Spacer(Modifier.height(8.dp))
            PillButton(stringResource(R.string.settings_save), { save() })
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(28.dp))
    Text(text, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = CallColors.Text,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
    )
}

@Composable
private fun LineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = CallColors.Muted)
    Spacer(Modifier.height(6.dp))
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = CallColors.Text),
        cursorBrush = SolidColor(CallColors.Text),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { field ->
            Column {
                Box(Modifier.padding(vertical = 8.dp)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = CallColors.Dim)
                    }
                    field()
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(CallColors.Control))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(label: String, content: @Composable () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = CallColors.Muted)
    Spacer(Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun Tag(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) CallColors.Text else CallColors.Control)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) CallColors.Background else CallColors.Text,
        )
    }
}

internal fun difficultyLabel(difficulty: String): Int = when (difficulty) {
    "advanced" -> R.string.difficulty_advanced
    else -> R.string.difficulty_intermediate
}

internal fun topicLabel(topic: String): Int = when (topic) {
    "Concurrency" -> R.string.topic_concurrency
    "Transaction_Payment" -> R.string.topic_transaction_payment
    "Redis_Queue" -> R.string.topic_redis_queue
    "Event_Outbox" -> R.string.topic_event_outbox
    "Spring_JPA" -> R.string.topic_spring_jpa
    "Auth_Security" -> R.string.topic_auth_security
    "Kubernetes" -> R.string.topic_kubernetes
    "Deployment_GitOps" -> R.string.topic_deployment_gitops
    "AWS" -> R.string.topic_aws
    "Docker_CI" -> R.string.topic_docker_ci
    "LoadTest_Observability" -> R.string.topic_loadtest_observability
    else -> R.string.topic_unknown
}
