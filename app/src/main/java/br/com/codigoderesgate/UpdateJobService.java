package br.com.codigoderesgate;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Verificação periódica em segundo plano (a cada ~12 h, com internet). */
public class UpdateJobService extends JobService {
    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js.getPendingJob(7) != null) return;
            js.schedule(new JobInfo.Builder(7, new ComponentName(c, UpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(12L * 60 * 60 * 1000)
                .setPersisted(true).build());
        } catch (Throwable t) { }
    }
    @Override public boolean onStartJob(final JobParameters p) {
        new Thread(new Runnable() { public void run() {
            Updater.Info inf = Updater.fetchNewer(UpdateJobService.this);
            if (inf != null) Updater.notifyOnce(UpdateJobService.this, inf);
            jobFinished(p, false);
        }}).start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters p) { return true; }
}
