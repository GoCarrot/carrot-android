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
