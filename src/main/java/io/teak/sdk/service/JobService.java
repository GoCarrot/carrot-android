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

import android.os.Bundle;
import android.support.annotation.NonNull;

import com.firebase.jobdispatcher.JobParameters;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import io.teak.sdk.Helpers;
import io.teak.sdk.Teak;
import io.teak.sdk.Unobfuscable;
import io.teak.sdk.core.DeviceScreenState;
import io.teak.sdk.io.IAndroidNotification;
import io.teak.sdk.raven.Raven;
import io.teak.sdk.raven.Sender;

public class JobService extends com.firebase.jobdispatcher.JobService implements Unobfuscable {
    public static final String JOB_TYPE_KEY = "JobService.JobType";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Map<String, JobTask> activeTasks = new HashMap<>();

    ///// com.firebase.jobdispatcher.JobService

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        // This is run on the main thread.
        // Return true if there is still work going on, and if so, must call jobFinished()

        final Bundle jobBundle = jobParameters.getExtras();
        if (jobBundle == null) {
            return false;
        }

        Callable<Boolean> jobCallable = null;
        try {
            if (Raven.JOB_TYPE.equals(jobBundle.getString(JOB_TYPE_KEY))) {
                jobCallable = new Sender(jobBundle);
            } else if (IAndroidNotification.ANIMATED_NOTIFICATION_JOB_TYPE.equals(jobBundle.getString(JOB_TYPE_KEY))) {
                DeviceScreenState.scheduleScreenStateJob(jobParameters);
            } else if (DeviceScreenState.SCREEN_STATE_JOB_TAG.equals(jobBundle.getString(JOB_TYPE_KEY))) {
                jobCallable = DeviceScreenState.isDeviceScreenOn(this.getApplicationContext(), jobParameters);
            }
        } catch (Exception e) {
            Teak.log.exception(e, false);
        }

        if (jobCallable != null) {
            final JobTask jobTask = new JobTask(jobCallable, jobParameters);
            Teak.log.i("job_service", Helpers.mm.h("tag", jobParameters.getTag()));
            this.activeTasks.put(jobParameters.getTag(), jobTask);
            this.executor.execute(jobTask);
            return true;
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        final JobTask jobTask = this.activeTasks.get(jobParameters.getTag());
        if (jobTask != null) {
            this.activeTasks.remove(jobParameters.getTag());
            jobTask.cancel(true);
        }
        return false;
    }

    ///// jobTaskCompleted - Called by the FutureTask wrapper

    void jobTaskCompleted(@NonNull JobParameters jobParameters, boolean needsReschedule) {
        this.jobFinished(jobParameters, needsReschedule);

        if (!needsReschedule) {
            this.activeTasks.remove(jobParameters.getTag());
        }
    }

    ///// FutureTask wrapper

    class JobTask extends FutureTask<Boolean> {
        final JobParameters jobParameters;

        JobTask(@NonNull Callable<Boolean> callable, @NonNull JobParameters jobParameters) {
            super(callable);
            this.jobParameters = jobParameters;
        }

        @Override
        protected void done() {
            super.done();

            // If the Callable returned false, it wants to be rescheduled.
            boolean reschedule = this.isCancelled();
            try {
                reschedule |= !this.get();
            } catch (Exception ignored) {
            }
            JobService.this.jobTaskCompleted(this.jobParameters, reschedule);
        }
    }
}
