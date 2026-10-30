package br.com.lcmtech.contentguard;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public final class Rule {
    public enum Type { KEYWORD, DOMAIN, APP }

    public final String id;
    public final Type type;
    public final String value;

    public Rule(Type type, String value) {
        this(UUID.randomUUID().toString(), type, value.trim());
    }
    private Rule(String id, Type type, String value) {
        this.id = id;
        this.type = type;
        this.value = value;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("type", type.name()).put("value", value);
    }
    static Rule fromJson(JSONObject json) throws JSONException {
        return new Rule(json.getString("id"),
                Type.valueOf(json.getString("type")), json.getString("value"));
    }
}
