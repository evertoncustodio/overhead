package io.github.evertoncustodio.overhead.clouds

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.Executors

/**
 * Background job that refreshes the cloud map. Android's JobScheduler decides exactly
 * when it runs: only with a network connection, and batched with other apps' work to
 * save battery.
 */
class CloudUpdateJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        // Jobs start on the main thread; the download must not block it.
        background.execute {
            val result = CloudDownloader(applicationContext).download()
            // Ask to be retried later (with back-off) only if the download failed.
            jobFinished(params, result == CloudDownloader.Result.FAILED)
        }
        return true  // still working; jobFinished() is called when done
    }

    // The system stopped us early (e.g. the network went away): try again later.
    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val PERIODIC_JOB_ID = 1
        private const val ONE_OFF_JOB_ID = 2

        private val background = Executors.newSingleThreadExecutor()

        /** Makes sure the every-3-hours update is scheduled. Safe to call often. */
        fun schedulePeriodic(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            // Re-scheduling would restart the 3-hour countdown, so leave an existing job alone.
            if (scheduler.getPendingJob(PERIODIC_JOB_ID) != null) return

            val job = JobInfo.Builder(PERIODIC_JOB_ID, ComponentName(context, CloudUpdateJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(CloudStore.UPDATE_INTERVAL_MILLIS)
                .setPersisted(true)  // survives reboots
                .build()
            scheduler.schedule(job)
        }

        /** Asks for an update as soon as there is a network, e.g. when the clouds are stale. */
        fun requestNow(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(ONE_OFF_JOB_ID) != null) return

            val job = JobInfo.Builder(ONE_OFF_JOB_ID, ComponentName(context, CloudUpdateJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()
            scheduler.schedule(job)
        }
    }
}
