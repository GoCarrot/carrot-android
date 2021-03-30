package io.teak.sdk.core;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("WeakerAccess")
public class InstrumentableReentrantLock extends ReentrantLock {
    public static boolean interruptLongLocksAndReport = false;
    public static long interruptTimeoutMS = 2;

    @Override
    public void lock() {
        if (interruptLongLocksAndReport) {
            String lockRequestTrace = "", lockHoldTrace = "";
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
                        lockRequestTrace = backTrace.toString();
                        android.util.Log.v("Teak.Instrumentation", lockRequestTrace);
                    }

                    {
                        StackTraceElement[] trace = lockOwner.getStackTrace();
                        StackTraceElement[] trimmedTrace = new StackTraceElement[trace.length - 3];
                        System.arraycopy(trace, 2, trimmedTrace, 0, trace.length - 3);
                        StringBuilder backTrace = new StringBuilder(lockOwner + " holds this lock at:");
                        for (StackTraceElement element : trimmedTrace) {
                            backTrace.append("\n\t").append(element.toString());
                        }
                        lockHoldTrace = backTrace.toString();
                        android.util.Log.v("Teak.Instrumentation", lockHoldTrace);
                    }
                }
                if (!this.tryLock(interruptTimeoutMS, TimeUnit.MILLISECONDS)) {
                    throw new Exception("Failed to acquire lock.");
                }
            } catch (Exception e) {
                String debugMessage = "Waited longer than " + interruptTimeoutMS + "ms to acquire lock.";
                android.util.Log.e("Teak.Instrumentaiton", debugMessage, e);
                android.util.Log.e("Teak.Instrumentaiton", lockRequestTrace, e);
                android.util.Log.e("Teak.Instrumentaiton", lockHoldTrace, e);
                throw new RuntimeException(debugMessage);
            }
        } else {
            super.lock();
        }
    }
}
