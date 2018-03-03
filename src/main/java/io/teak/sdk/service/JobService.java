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

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class JobService extends android.app.job.JobService {
    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        android.util.Log.i("Teak.JobService", "Start: " + jobParameters.toString());
        jobFinished(jobParameters, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        android.util.Log.i("Teak.JobService", "Stop: " + jobParameters.toString());
        return false;
    }
}
