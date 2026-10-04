package app.roadready

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import app.roadready.core.DriveLog
import app.roadready.core.Driver
import app.roadready.ui.RoadReadyRoot
import app.roadready.ui.theme.RoadReadyTheme
import java.io.File
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    private val store get() = (application as RoadReadyApp).store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoadReadyTheme {
                RoadReadyRoot(store = store, shareLog = ::shareLog)
            }
        }
    }

    /** Shares one kid's log as a CSV attachment with the plain-text summary as the message body. */
    private fun shareLog(driver: Driver) {
        val state = store.value
        val drives = state.drivesFor(driver.id)
        val zone = ZoneId.of(state.homeCity.zoneId)
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val safeName = driver.name.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "driver" }
        val file = File(dir, "driving-log-$safeName.csv")
        file.writeText(DriveLog.csv(driver, drives, zone))
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Driving log — ${driver.name}")
            putExtra(Intent.EXTRA_TEXT, DriveLog.summary(driver, drives))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share ${driver.name}'s driving log"))
    }
}
