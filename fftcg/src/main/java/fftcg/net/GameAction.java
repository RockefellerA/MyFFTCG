package fftcg.net;

import org.json.JSONObject;

/**
 * Immutable value type representing a single game action sent over the network.
 * Serializes to/from a single-line JSON string for newline-delimited transport.
 */
public record GameAction(ActionType type, JSONObject payload) {

    public static GameAction of(ActionType type) {
        return new GameAction(type, new JSONObject());
    }

    public static GameAction of(ActionType type, JSONObject payload) {
        return new GameAction(type, payload);
    }

    public String serialize() {
        JSONObject obj = new JSONObject();
        obj.put("type", type.name());
        obj.put("payload", payload != null ? payload : new JSONObject());
        return obj.toString();
    }

    public static GameAction deserialize(String json) {
        JSONObject obj = new JSONObject(json);
        ActionType type = ActionType.valueOf(obj.getString("type"));
        JSONObject payload = obj.optJSONObject("payload");
        return new GameAction(type, payload != null ? payload : new JSONObject());
    }
}
