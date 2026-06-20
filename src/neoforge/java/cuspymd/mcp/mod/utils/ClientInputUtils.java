package cuspymd.mcp.mod.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cuspymd.mcp.mod.command.ChatMessageCapture;
import cuspymd.mcp.mod.server.MCPProtocol;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class ClientInputUtils {
    private static final int MAX_HOLD_TICKS = 200;
    private static final List<KeyReleaseTask> KEY_RELEASE_TASKS = new ArrayList<>();

    private ClientInputUtils() {
    }

    public static JsonObject setHeldSlot(JsonObject params) {
        return await(runOnClient(client -> {
            requirePlayer(client);
            int slot = params.get("slot").getAsInt();
            if (slot < 0 || slot > 8) throw new IllegalArgumentException("slot must be between 0 and 8");
            client.player.getInventory().selected = slot;
            client.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
            JsonObject result = new JsonObject();
            result.addProperty("selectedSlot", slot);
            return result;
        }), "set held slot");
    }

    public static JsonObject movementInput(JsonObject params) {
        return await(runOnClient(client -> {
            boolean pressed = !params.has("pressed") || params.get("pressed").getAsBoolean();
            int ticks = clamp(getInt(params, "ticks", 1), 0, MAX_HOLD_TICKS);
            List<KeyMapping> keys = parseKeys(client, params);
            for (KeyMapping key : keys) key.setDown(pressed);
            if (pressed && ticks > 0) scheduleKeyRelease(keys, ticks);
            JsonObject result = new JsonObject();
            result.addProperty("pressed", pressed);
            result.addProperty("ticks", ticks);
            JsonArray keyNames = new JsonArray();
            for (KeyMapping key : keys) keyNames.add(key.getName());
            result.add("keys", keyNames);
            return result;
        }), "movement input");
    }

    public static JsonObject sneak(JsonObject params) {
        return await(runOnClient(client -> {
            String mode = getString(params, "mode", "press").toLowerCase(Locale.ROOT);
            int ticks = clamp(getInt(params, "ticks", 1), 0, MAX_HOLD_TICKS);
            boolean pressed = switch (mode) {
                case "release" -> false;
                case "toggle" -> !client.options.keyShift.isDown();
                case "hold", "press" -> true;
                default -> throw new IllegalArgumentException("Unsupported sneak mode: " + mode);
            };
            client.options.keyShift.setDown(pressed);
            if ("hold".equals(mode) && ticks > 0) scheduleKeyRelease(List.of(client.options.keyShift), ticks);
            JsonObject result = new JsonObject();
            result.addProperty("mode", mode);
            result.addProperty("pressed", pressed);
            result.addProperty("ticks", ticks);
            return result;
        }), "sneak");
    }

    public static JsonObject waitForChat(JsonObject params) {
        try {
            int timeoutMs = clamp(getInt(params, "timeout_ms", 5000), 0, 60000);
            String literal = params != null && params.has("text") ? params.get("text").getAsString() : null;
            Pattern regex = params != null && params.has("regex") ? Pattern.compile(params.get("regex").getAsString()) : null;
            ChatMessageCapture.CapturedMessage message = ChatMessageCapture.getInstance().waitForRecentMessage(timeoutMs, text -> {
                if (literal != null && text.contains(literal)) return true;
                return regex != null && regex.matcher(text).find();
            });
            JsonObject result = new JsonObject();
            result.addProperty("matched", message != null);
            if (message != null) {
                JsonObject found = new JsonObject();
                found.addProperty("text", message.text());
                found.addProperty("timestampMs", message.timestampMs());
                found.addProperty("source", message.source().name());
                result.add("message", found);
            }
            return MCPProtocol.createSuccessResponse(result.toString());
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Failed to wait for chat: " + e.getMessage(), null);
        }
    }

    public static void onClientTick(Minecraft client) {
        synchronized (KEY_RELEASE_TASKS) {
            Iterator<KeyReleaseTask> iterator = KEY_RELEASE_TASKS.iterator();
            while (iterator.hasNext()) {
                KeyReleaseTask task = iterator.next();
                task.remainingTicks--;
                if (task.remainingTicks <= 0) {
                    for (KeyMapping key : task.keys) key.setDown(false);
                    iterator.remove();
                }
            }
        }
    }

    private static CompletableFuture<JsonObject> runOnClient(ClientAction action) {
        Minecraft client = Minecraft.getInstance();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                future.complete(action.run(client));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static JsonObject await(CompletableFuture<JsonObject> future, String operationName) {
        try {
            return MCPProtocol.createSuccessResponse(future.get().toString());
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Failed to " + operationName + ": " + e.getMessage(), null);
        }
    }

    private static void requirePlayer(Minecraft client) {
        if (client.player == null || client.level == null) throw new IllegalStateException("Player/world not available");
    }

    private static List<KeyMapping> parseKeys(Minecraft client, JsonObject params) {
        if (params == null || !params.has("keys") || !params.get("keys").isJsonArray()) {
            throw new IllegalArgumentException("Missing required array parameter: keys");
        }
        List<KeyMapping> keys = new ArrayList<>();
        for (JsonElement element : params.getAsJsonArray("keys")) {
            String key = element.getAsString().toLowerCase(Locale.ROOT);
            keys.add(switch (key) {
                case "w", "forward" -> client.options.keyUp;
                case "s", "back", "backward" -> client.options.keyDown;
                case "a", "left" -> client.options.keyLeft;
                case "d", "right" -> client.options.keyRight;
                case "jump", "space" -> client.options.keyJump;
                case "sprint" -> client.options.keySprint;
                case "sneak", "shift" -> client.options.keyShift;
                default -> throw new IllegalArgumentException("Unsupported movement key: " + key);
            });
        }
        return keys;
    }

    private static void scheduleKeyRelease(List<KeyMapping> keys, int ticks) {
        synchronized (KEY_RELEASE_TASKS) {
            KEY_RELEASE_TASKS.add(new KeyReleaseTask(List.copyOf(keys), ticks));
        }
    }

    private static String getString(JsonObject params, String field, String fallback) {
        return params != null && params.has(field) ? params.get(field).getAsString() : fallback;
    }

    private static int getInt(JsonObject params, String field, int fallback) {
        return params != null && params.has(field) ? params.get(field).getAsInt() : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    @FunctionalInterface
    private interface ClientAction {
        JsonObject run(Minecraft client);
    }

    private static final class KeyReleaseTask {
        private final List<KeyMapping> keys;
        private int remainingTicks;

        private KeyReleaseTask(List<KeyMapping> keys, int remainingTicks) {
            this.keys = keys;
            this.remainingTicks = remainingTicks;
        }
    }
}
