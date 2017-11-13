/* Teak -- Copyright (C) 2017 GoCarrot Inc.
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
package io.teak.sdk.core;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("WeakerAccess")
public class InstrumentableReentrantLock extends ReentrantLock {
    public static boolean interruptLongLocksAndReport = true; // HAX FALSE
    public static long interruptTimeoutMS = 2;

    @Override
    public void lock() {
        if (interruptLongLocksAndReport) {
            try {
                Thread lockOwner = this.getOwner();
                if (lockOwner != null && lockOwner != Thread.currentThread()) {
                    {
                        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
                        StackTraceElement[] trimmedTrace = new StackTraceElement[trace.length - 3];
                        System.arraycopy(trace, 2, trimmedTrace, 0, trace.length - 3);
                        StringBuilder backTrace = new StringBuilder(Thread.currentThread() + " requesting this lock from:");
                        for (StackTraceElement element : trimmedTrace) {
                            backTrace.append("\n\t").append(element.toString());
                        }
                        android.util.Log.v("Teak.Instrumentation", backTrace.toString());
                    }

                    {
                        StackTraceElement[] trace = lockOwner.getStackTrace();
                        StackTraceElement[] trimmedTrace = new StackTraceElement[trace.length - 3];
                        System.arraycopy(trace, 2, trimmedTrace, 0, trace.length - 3);
                        StringBuilder backTrace = new StringBuilder(lockOwner + " holds this lock at:");
                        for (StackTraceElement element : trimmedTrace) {
                            backTrace.append("\n\t").append(element.toString());
                        }
                        android.util.Log.v("Teak.Instrumentation", backTrace.toString());
                    }
                }
                if (!this.tryLock(interruptTimeoutMS, TimeUnit.MILLISECONDS)) {
                    throw new Exception("Failed to acquire lock.");
                }
            } catch (Exception e) {
                String debugMessage = "Waited longer than " + interruptTimeoutMS + "ms to acquire lock.";
                android.util.Log.e("Teak.Instrumentaiton", debugMessage, e);
                throw new RuntimeException(debugMessage);
            }
        } else {
            super.lock();
        }
    }
}
