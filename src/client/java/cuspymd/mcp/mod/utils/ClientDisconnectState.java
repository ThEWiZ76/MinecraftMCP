package cuspymd.mcp.mod.utils;

import com.google.gson.JsonObject;

public final class ClientDisconnectState {
    private static JsonObject lastDisconnect = new JsonObject();

    private ClientDisconnectState() {
    }

    public static synchronized void record(String title, String reason, Throwable throwable) {
        JsonObject result = new JsonObject();
        result.addProperty("timestampMs", System.currentTimeMillis());
        result.addProperty("title", title == null ? "" : title);
        result.addProperty("reason", reason == null ? "" : reason);
        if (throwable != null) {
            result.addProperty("exceptionClass", throwable.getClass().getName());
            result.addProperty("exceptionMessage", throwable.getMessage());
        }
        lastDisconnect = result;
    }

    public static synchronized JsonObject snapshot() {
        return lastDisconnect.deepCopy();
    }
}
