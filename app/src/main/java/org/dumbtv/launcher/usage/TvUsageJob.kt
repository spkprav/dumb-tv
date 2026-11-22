package org.dumbtv.launcher.usage

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Saves usage while another app is on screen and across TV reboots. */
internal class TvUsageJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = CoroutineScope(Dispatchers.Main).launch {
            val hasAccess = withContext(Dispatchers.IO) {
                try {
                    TvUsage.read(applicationContext).hasAccess
                } catch (_: SecurityException) {
                    false
                }
            }
            jobFinished(params, false)
            if (!hasAccess) schedule(applicationContext, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    override fun onDestroy() {
        running?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 103
        private const val INTERVAL_MILLIS = 15 * 60_000L

        fun schedule(context: Context, enabled: Boolean) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val pending = scheduler.getPendingJob(JOB_ID)
            if (enabled && pending == null) {
                scheduler.schedule(
                    JobInfo.Builder(JOB_ID, ComponentName(context, TvUsageJob::class.java))
                        .setPeriodic(INTERVAL_MILLIS)
                        .setPersisted(true)
                        .build(),
                )
            } else if (!enabled && pending != null) {
                scheduler.cancel(JOB_ID)
            }
        }
    }
}
