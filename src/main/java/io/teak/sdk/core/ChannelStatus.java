package io.teak.sdk.core;

import io.teak.sdk.Teak;
import io.teak.sdk.json.JSONObject;

/**
 * Encapsulation of the state of a channel.
 */
public class ChannelStatus {
    public final Teak.Channel.State state;
    public final boolean deliveryFault;

    public static final ChannelStatus Unknown = new ChannelStatus("unknown", false);

    protected ChannelStatus(final String state, final boolean deliveryFault) {
        this.state = Teak.Channel.State.fromString(state);
        this.deliveryFault = deliveryFault;
    }

    public static ChannelStatus fromJSON(final JSONObject jsonObject) {
        return new ChannelStatus(jsonObject.optString("state", "unknown"), jsonObject.optBoolean("delivery_fault", false));
    }

    public JSONObject toJSON() {
        final JSONObject json = new JSONObject();
        json.put("state", this.state.name);
        json.put("delivery_fault", this.deliveryFault);
        return json;
    }
}
