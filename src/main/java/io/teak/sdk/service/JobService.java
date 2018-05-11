/* Teak -- Copyright (C) 2018 GoCarrot Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.teak.sdk.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;

import io.teak.sdk.IntegrationChecker;
import io.teak.sdk.Teak;
import io.teak.sdk.configuration.AppConfiguration;
import io.teak.sdk.io.DefaultAndroidResources;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public class JobService extends android.app.job.JobService {
    private static final String LOG_TAG = "Teak.JobService";
    public static class Exception extends java.lang.Exception {
        public Exception(@NonNull String message) {
            super(message);
        }

        public Exception(@NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
        }
    }

    static boolean HAX = false;
    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        // We are running on the main thread
        android.util.Log.i(LOG_TAG, "onStartJob [" + jobParameters.getJobId() + "]: " + jobParameters.toString());
        // Do the thing...
        if (!HAX) {
            try {
                scheduleDeviceStateJob(this);
            } catch (Exception ignored) {}
            HAX = true;
        }
        jobFinished(jobParameters, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        // This will be invoked only if we return 'true' from onStartJob, or onStartJob runs for too long
        android.util.Log.i(LOG_TAG, "onStopJob[" + jobParameters.getJobId() + "]: " + jobParameters.toString());
        return false;
    }

    public static void scheduleDeviceStateJob(@NonNull Context context) throws JobService.Exception {
        // Job ids must be unique per Linux user ID (android:sharedUserId in the manifest)
        // If a customer is having a job id conflict, they need to either add:
        //
        //    <string name="io_teak_job_id">[unique integer id]</string>
        //
        // to their Teak resources XML, or:
        //
        //    <meta-data android:name="io_teak_job_id" android:value="teak[unique integer id]" />
        //
        // to their Adobe Air <manifestAdditions> section
        int teakJobId = Teak.JOB_ID;
        try {
            final AppConfiguration tempAppConfiguration = new AppConfiguration(context, new DefaultAndroidResources(context));
            teakJobId = tempAppConfiguration.jobId;
        } catch (IntegrationChecker.InvalidConfigurationException ignored) {
        }

        PersistableBundle extras = new PersistableBundle();

        // Future-Pat:
        //             setPeriodic - Minimum interval of 15 minutes with a 5 minute drift
        //   setRequiresDeviceIdle - I have not gotten this to run a job even after 20+ minutes of idle
        //       setMinimumLatency -
        final JobInfo job = new JobInfo.Builder(teakJobId, new ComponentName(context, JobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(false)
                .setMinimumLatency(5000)
                .setExtras(extras)
                .build();

        final JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler == null) {
            throw new JobService.Exception("Could not get Context.JOB_SCHEDULER_SERVICE");
        }

        android.util.Log.i(LOG_TAG, "Scheduling job: " + job.toString());
        jobScheduler.schedule(job);
    }
}
