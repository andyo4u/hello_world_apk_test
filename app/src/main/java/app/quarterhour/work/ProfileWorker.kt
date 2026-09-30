package app.quarterhour.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.quarterhour.QuarterHourApp
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Nightly job: rewrites user_profile.md and prepares tomorrow's feed so the
 * first minutes of the day aren't spent waiting. Uses no reading time.
 */
class ProfileWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as QuarterHourApp).container
        container.news.writeProfile()
        container.news.refresh()
        return Result.success()
    }

    companion object {
        private const val NAME = "nightly-profile"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.with(LocalTime.of(4, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ProfileWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
