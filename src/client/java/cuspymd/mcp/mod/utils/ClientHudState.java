package cuspymd.mcp.mod.utils;

import com.google.gson.JsonObject;

public final class ClientHudState {
    private static String actionbar = "";
    private static String title = "";
    private static String subtitle = "";
    private static long updatedAtMs = 0L;

    private ClientHudState() {
    }

    public static synchronized void recordActionbar(String text) {
        actionbar = text == null ? "" : text;
        updatedAtMs = System.currentTimeMillis();
    }

    public static synchronized void recordTitle(String text) {
        title = text == null ? "" : text;
        updatedAtMs = System.currentTimeMillis();
    }

    public static synchronized void recordSubtitle(String text) {
        subtitle = text == null ? "" : text;
        updatedAtMs = System.currentTimeMillis();
    }

    public static synchronized JsonObject snapshot() {
        JsonObject result = new JsonObject();
        result.addProperty("actionbar", actionbar);
        result.addProperty("title", title);
        result.addProperty("subtitle", subtitle);
        result.addProperty("updatedAtMs", updatedAtMs);
        return result;
    }
}
