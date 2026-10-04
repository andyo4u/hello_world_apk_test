package app.roadready

import android.app.Application
import app.roadready.data.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File

class RoadReadyApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())
    val store: StateStore by lazy { StateStore(File(filesDir, "roadready.json"), appScope) }
}
