/* Teak -- Copyright (C) 2016 GoCarrot Inc.
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
package io.teak.sdk;

import android.util.Log;

class Uncaught implements Thread.UncaughtExceptionHandler {
    private static final String LOG_TAG = "Teak.Uncaught";

    private Thread.UncaughtExceptionHandler previousUncaughtExceptionHandler;
    private Thread createdOnThread;

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        if (!createdOnThread.equals(thread)) {
            Log.d(LOG_TAG, "TeakExceptionHandler created on " + createdOnThread.toString() + " getting exception from " + thread.toString());
        }

        if (Teak.Instance != null && Teak.Instance.sdkRaven != null) {
            Teak.Instance.sdkRaven.reportException(ex, null);
        }
    }

    public static synchronized Uncaught begin() {
        return new Uncaught();
    }

    private Uncaught() {
        createdOnThread = Thread.currentThread();
        previousUncaughtExceptionHandler = createdOnThread.getUncaughtExceptionHandler();
        createdOnThread.setUncaughtExceptionHandler(this);
    }

    public synchronized void end() {
        createdOnThread.setUncaughtExceptionHandler(previousUncaughtExceptionHandler);
    }
}
