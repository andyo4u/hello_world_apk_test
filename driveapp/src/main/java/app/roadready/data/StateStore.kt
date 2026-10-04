package app.roadready.data

import androidx.core.util.AtomicFile
import app.roadready.core.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The whole [AppState] as one JSON file, exposed as a [StateFlow]. The state
 * changes immediately; the write happens in the background, atomically
 * (write-then-rename) so a crash never leaves a half-written file.
 */
class StateStore(file: File, private val scope: CoroutineScope) {
    private val atomic = AtomicFile(file)
    private val writeLock = Mutex()
    private val state = MutableStateFlow(read())

    val flow: StateFlow<AppState> = state.asStateFlow()
    val value: AppState get() = state.value

    fun edit(transform: (AppState) -> AppState) {
        val before = state.value
        // updateAndGet retries on a race, so transform must stay pure.
        val after = state.updateAndGet(transform)
        // Each write saves the latest state, so writes queued behind the lock never go backwards.
        if (after != before) scope.launch(Dispatchers.IO) { writeLock.withLock { write(state.value) } }
    }

    private fun read(): AppState = runCatching {
        if (!atomic.baseFile.exists()) AppState()
        else json.decodeFromString(AppState.serializer(), atomic.readFully().decodeToString())
    }.getOrElse { AppState() }

    private fun write(value: AppState) {
        atomic.baseFile.parentFile?.mkdirs()
        val out = atomic.startWrite()
        try {
            out.write(json.encodeToString(AppState.serializer(), value).encodeToByteArray())
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
        }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
