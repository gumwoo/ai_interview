package io.github.gumwoo.aiinterview.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.gumwoo.aiinterview.data.ApiException
import io.github.gumwoo.aiinterview.data.HistoryApi
import io.github.gumwoo.aiinterview.data.HistoryItem
import io.github.gumwoo.aiinterview.data.SessionDetail
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryState(
    val loading: Boolean = false,
    val items: List<HistoryItem> = emptyList(),
    val selected: HistoryItem? = null,
    val detail: SessionDetail? = null,
    val confirmingDelete: Boolean = false,
    val deleting: Boolean = false,
    val failed: Boolean = false,
)

/** 통화 기록 화면. 접고 펴도 보던 기록이 유지되도록 상태를 여기에 둔다. */
class HistoryViewModel(
    private val api: HistoryApi,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            try {
                val items = api.history(today().minusDays(HISTORY_DAYS), today())
                _state.update { it.copy(loading = false, items = items) }
            } catch (_: ApiException) {
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun open(item: HistoryItem) {
        _state.update { it.copy(selected = item, detail = null, confirmingDelete = false) }
        loadDetail(item)
    }

    fun retry() {
        val selected = _state.value.selected
        if (selected == null) load() else loadDetail(selected)
    }

    fun closeDetail() {
        _state.update { it.copy(selected = null, detail = null, confirmingDelete = false, failed = false) }
    }

    /** 되돌릴 수 없으므로 두 번 눌러야 지운다. */
    fun requestDelete() {
        val selected = _state.value.selected ?: return
        if (_state.value.deleting) return
        if (!_state.value.confirmingDelete) {
            _state.update { it.copy(confirmingDelete = true) }
            return
        }
        _state.update { it.copy(deleting = true, failed = false) }
        viewModelScope.launch {
            try {
                api.deleteSession(selected.sessionId)
                _state.update { state ->
                    state.copy(
                        items = state.items.filter { it.sessionId != selected.sessionId },
                        selected = null,
                        detail = null,
                        confirmingDelete = false,
                        deleting = false,
                    )
                }
            } catch (_: ApiException) {
                _state.update { it.copy(confirmingDelete = false, deleting = false, failed = true) }
            }
        }
    }

    private fun loadDetail(item: HistoryItem) {
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            try {
                val detail = api.session(item.sessionId)
                _state.update { it.copy(loading = false, detail = detail) }
            } catch (_: ApiException) {
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    private companion object {
        const val HISTORY_DAYS = 365L
    }
}
