package io.teak.sdk.core;

import io.teak.sdk.json.JSONObject;

/**
 * Encapsulation of the state of a channel.
 */
public class ChannelStatus {
    public enum State {
        OptOut("opt_out"),
        Available("available"),
        OptIn("opt_in"),
        Absent("absent"),
        Unknown("unknown");

        //public static final Integer length = 1 + Absent.ordinal();

        public final String name;

        State(String name) {
            this.name = name;
        }

        static State fromString(final String string) {
            if (OptOut.name.equalsIgnoreCase(string)) return OptOut;
            if (Available.name.equalsIgnoreCase(string)) return Available;
            if (OptIn.name.equalsIgnoreCase(string)) return OptIn;

            return Absent;
        }
    }

    public final State state;
    public final boolean deliveryFault;

    public static final ChannelStatus Unknown = new ChannelStatus("unknown", false);

    public ChannelStatus(final String state, final boolean deliveryFault) {
        this.state = State.fromString(state);
        this.deliveryFault = deliveryFault;
    }

    public ChannelStatus(final JSONObject jsonObject) {
        this(jsonObject.getString("state"), jsonObject.optBoolean("delivery_fault", false));
    }

    public JSONObject toJSON() {
        final JSONObject json = new JSONObject();
        json.put("state", this.state.name);
        json.put("delivery_fault", this.deliveryFault);
        return json;
    }
}
