package io.teak.sdk.core;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("WeakerAccess")
public class InstrumentableReentrantLock extends ReentrantLock {
    public static boolean interruptLongLocksAndReport = true; // HAX, false
    @Override
    public void lock() {
        if (interruptLongLocksAndReport) {
            final long msTimeout = 2000;
            try {
                tryLock(msTimeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interruptedException) {
                String debugMessage = "Waited longer than " + msTimeout + "ms to acquire lock.";
                android.util.Log.e("Teak.Instrumentaiton", debugMessage, interruptedException);
                throw new RuntimeException(debugMessage);
            }
        } else {
            super.lock();
        }
    }
}
