package io.github.gumwoo.aiinterview.presentation.history

import io.github.gumwoo.aiinterview.data.ApiException
import io.github.gumwoo.aiinterview.data.HistoryApi
import io.github.gumwoo.aiinterview.data.HistoryItem
import io.github.gumwoo.aiinterview.data.SessionDetail
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val api = FakeHistoryApi()
    private val item = HistoryItem(sessionId = 1, source = "SCHEDULED", startedAt = "2026-11-03T00:00:00Z", answeredCount = 2)

    private fun viewModel() = HistoryViewModel(api, today = { LocalDate.parse("2026-11-03") })

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the last year of history`() {
        api.items = listOf(item)
        val viewModel = viewModel()

        viewModel.load()

        assertEquals(listOf(item), viewModel.state.value.items)
        assertFalse(viewModel.state.value.loading)
        assertEquals(LocalDate.parse("2025-11-03") to LocalDate.parse("2026-11-03"), api.range)
    }

    @Test
    fun `shows failure and recovers on retry`() {
        api.failure = ApiException.Network(IOException())
        val viewModel = viewModel()
        viewModel.load()
        assertTrue(viewModel.state.value.failed)

        api.failure = null
        api.items = listOf(item)
        viewModel.retry()

        assertFalse(viewModel.state.value.failed)
        assertEquals(listOf(item), viewModel.state.value.items)
    }

    @Test
    fun `opens detail and returns to list`() {
        val viewModel = viewModel()

        viewModel.open(item)
        assertEquals(item, viewModel.state.value.selected)
        assertEquals(1L, viewModel.state.value.detail?.sessionId)

        viewModel.closeDetail()
        assertNull(viewModel.state.value.selected)
        assertNull(viewModel.state.value.detail)
    }

    @Test
    fun `retries detail when it fails`() {
        val viewModel = viewModel()
        api.failure = ApiException.Network(IOException())
        viewModel.open(item)
        assertTrue(viewModel.state.value.failed)

        api.failure = null
        viewModel.retry()

        assertFalse(viewModel.state.value.failed)
        assertEquals(1L, viewModel.state.value.detail?.sessionId)
    }

    @Test
    fun `deletes only after second press`() {
        api.items = listOf(item)
        val viewModel = viewModel()
        viewModel.load()
        viewModel.open(item)

        viewModel.requestDelete()
        assertTrue(viewModel.state.value.confirmingDelete)
        assertEquals(emptyList<Long>(), api.deleted)

        viewModel.requestDelete()
        assertEquals(listOf(1L), api.deleted)
        assertNull(viewModel.state.value.selected)
        assertEquals(emptyList<HistoryItem>(), viewModel.state.value.items)
    }

    @Test
    fun `ignores press while deleting`() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        api.deleteGate = gate
        api.items = listOf(item)
        val viewModel = viewModel()
        viewModel.load()
        viewModel.open(item)
        viewModel.requestDelete()
        viewModel.requestDelete()
        assertTrue(viewModel.state.value.deleting)

        viewModel.requestDelete()
        gate.complete(Unit)

        assertEquals(listOf(1L), api.deleted)
        assertFalse(viewModel.state.value.deleting)
    }

    @Test
    fun `keeps record when delete fails`() {
        api.items = listOf(item)
        val viewModel = viewModel()
        viewModel.load()
        viewModel.open(item)
        viewModel.requestDelete()
        api.failure = ApiException.Network(IOException())

        viewModel.requestDelete()

        assertTrue(viewModel.state.value.failed)
        assertFalse(viewModel.state.value.confirmingDelete)
        assertEquals(item, viewModel.state.value.selected)
        assertEquals(listOf(item), viewModel.state.value.items)
    }

    private class FakeHistoryApi : HistoryApi {
        var items: List<HistoryItem> = emptyList()
        var failure: ApiException? = null
        var range: Pair<LocalDate, LocalDate>? = null
        val deleted = mutableListOf<Long>()
        var deleteGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        override suspend fun history(from: LocalDate, to: LocalDate): List<HistoryItem> {
            failure?.let { throw it }
            range = from to to
            return items
        }

        override suspend fun session(sessionId: Long): SessionDetail {
            failure?.let { throw it }
            return SessionDetail(sessionId)
        }

        override suspend fun deleteSession(sessionId: Long) {
            failure?.let { throw it }
            deleted += sessionId
            deleteGate?.await()
        }
    }
}
