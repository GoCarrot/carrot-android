package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREArray;
import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakNotification;
import io.teak.sdk.core.ThreadFactory;
import java.util.concurrent.Future;

public class TeakNotificationFunction implements FREFunction {
    public enum CallType {
        Schedule("NOTIFICATION_SCHEDULED"),
        ScheduleLongDistance("LONG_DISTANCE_NOTIFICATION_SCHEDULED"),
        Cancel("NOTIFICATION_CANCELED"),
        CancelAll("NOTIFICATION_CANCEL_ALL");

        private final String text;

        @Override
        public String toString() {
            return text;
        }

        CallType(final String text) {
            this.text = text;
        }
    }
    ;

    private final CallType callType;

    public TeakNotificationFunction(CallType callType) {
        this.callType = callType;
    }

    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        try {
            Future<String> tempFuture = null;
            switch (callType) {
                case Cancel: {
                    tempFuture = TeakNotification.cancelNotification(argv[0].getAsString());
                } break;
                case CancelAll: {
                    tempFuture = TeakNotification.cancelAll();
                } break;
                case Schedule: {
                    tempFuture = TeakNotification.scheduleNotification(argv[0].getAsString(), argv[1].getAsString(), (long) argv[2].getAsDouble());
                } break;
                case ScheduleLongDistance: {
                    FREArray airUserIds = (FREArray) argv[2];
                    String[] userIds = new String[(int) airUserIds.getLength()];
                    for (int i = 0; i < userIds.length; i++) {
                        userIds[i] = airUserIds.getObjectAt(i).getAsString();
                    }
                    tempFuture = TeakNotification.scheduleNotification(argv[0].getAsString(), (long) argv[1].getAsDouble(), userIds);
                } break;
            }

            final Future<String> future = tempFuture;
            if (future != null) {
                ThreadFactory.autoStart(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String json = future.get();
                            Extension.context.dispatchStatusEventAsync(callType.toString(), json);
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        }
                    }
                });
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
        return null;
    }
}
