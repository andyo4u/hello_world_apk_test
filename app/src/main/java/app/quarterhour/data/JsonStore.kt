package app.quarterhour.data

import androidx.core.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A single JSON document on disk, exposed as a [StateFlow]. Writes are atomic
 * (write-then-rename) so a crash never leaves a half-written file.
 */
class JsonStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
) {
    private val atomic = AtomicFile(file)
    private val mutex = Mutex()
    private val state = MutableStateFlow(read())

    val flow: StateFlow<T> = state.asStateFlow()
    val value: T get() = state.value

    suspend fun update(transform: (T) -> T): T = mutex.withLock {
        val next = transform(state.value)
        if (next != state.value) {
            state.value = next
            withContext(Dispatchers.IO) { write(next) }
        }
        next
    }

    suspend fun set(value: T) {
        update { value }
    }

    private fun read(): T = runCatching {
        if (!file.exists()) return default()
        json.decodeFromString(serializer, atomic.readFully().decodeToString())
    }.getOrElse { default() }

    private fun write(value: T) {
        file.parentFile?.mkdirs()
        val out = atomic.startWrite()
        try {
            out.write(json.encodeToString(serializer, value).encodeToByteArray())
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
