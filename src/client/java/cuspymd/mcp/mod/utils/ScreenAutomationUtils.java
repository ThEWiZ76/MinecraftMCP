package cuspymd.mcp.mod.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ScreenAutomationUtils {
    private ScreenAutomationUtils() {
    }

    public static CompletableFuture<JsonObject> inspectCurrentScreen() {
        return runOnClientThread(ScreenAutomationUtils::inspectCurrentScreenNow);
    }

    public static CompletableFuture<JsonObject> clickScreenSlot(JsonObject params) {
        return runOnClientThread(client -> clickScreenSlotNow(client, params));
    }

    public static CompletableFuture<JsonObject> closeCurrentScreen() {
        return runOnClientThread(ScreenAutomationUtils::closeCurrentScreenNow);
    }

    static SlotActionType parseActionType(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SlotActionType.PICKUP;
        }
        return SlotActionType.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
    }

    private static CompletableFuture<JsonObject> runOnClientThread(ClientAction action) {
        MinecraftClient client = MinecraftClient.getInstance();
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

    private static JsonObject inspectCurrentScreenNow(MinecraftClient client) {
        JsonObject result = new JsonObject();
        Screen screen = client.currentScreen;
        result.addProperty("hasScreen", screen != null);

        if (screen == null) {
            return result;
        }

        result.addProperty("screenClass", screen.getClass().getName());
        result.addProperty("title", screen.getTitle().getString());
        result.addProperty("isHandledScreen", screen instanceof HandledScreen<?>);

        if (!(screen instanceof HandledScreen<?> handledScreen)) {
            return result;
        }

        ScreenHandler handler = handledScreen.getScreenHandler();
        result.addProperty("syncId", handler.syncId);
        result.add("cursorStack", serializeStack(handler.getCursorStack()));

        JsonArray slots = new JsonArray();
        for (Slot slot : handler.slots) {
            JsonObject slotJson = new JsonObject();
            slotJson.addProperty("id", slot.id);
            slotJson.addProperty("index", slot.getIndex());
            slotJson.addProperty("x", slot.x);
            slotJson.addProperty("y", slot.y);
            slotJson.addProperty("hasStack", slot.hasStack());
            slotJson.add("stack", serializeStack(slot.getStack()));
            slots.add(slotJson);
        }
        result.add("slots", slots);
        return result;
    }

    private static JsonObject clickScreenSlotNow(MinecraftClient client, JsonObject params) {
        if (params == null || !params.has("slot")) {
            throw new IllegalArgumentException("Missing required parameter: slot");
        }
        if (client.player == null) {
            throw new IllegalStateException("Player not found. Make sure you are in a world.");
        }
        if (client.interactionManager == null) {
            throw new IllegalStateException("Interaction manager not available.");
        }
        if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            throw new IllegalStateException("Current screen is not a handled inventory screen.");
        }

        ScreenHandler handler = handledScreen.getScreenHandler();
        int slotId = params.get("slot").getAsInt();
        if (slotId < 0 || slotId >= handler.slots.size()) {
            throw new IllegalArgumentException("Slot " + slotId + " is outside the current screen slot range 0-" + (handler.slots.size() - 1));
        }

        int button = params.has("button") ? params.get("button").getAsInt() : 0;
        SlotActionType actionType = parseActionType(params.has("action") ? params.get("action").getAsString() : null);

        client.interactionManager.clickSlot(handler.syncId, slotId, button, actionType, client.player);

        JsonObject result = new JsonObject();
        result.addProperty("clicked", true);
        result.addProperty("slot", slotId);
        result.addProperty("button", button);
        result.addProperty("action", actionType.name());
        result.add("screen", inspectCurrentScreenNow(client));
        return result;
    }

    private static JsonObject closeCurrentScreenNow(MinecraftClient client) {
        JsonObject result = new JsonObject();
        Screen screen = client.currentScreen;
        result.addProperty("hadScreen", screen != null);

        if (screen == null) {
            result.addProperty("closed", false);
            return result;
        }

        if (client.player != null && screen instanceof HandledScreen<?>) {
            client.player.closeHandledScreen();
        } else {
            client.setScreen(null);
        }

        result.addProperty("closed", true);
        return result;
    }

    private static JsonObject serializeStack(ItemStack stack) {
        JsonObject result = new JsonObject();
        boolean present = stack != null && !stack.isEmpty();
        result.addProperty("present", present);
        if (!present) {
            return result;
        }

        result.addProperty("itemId", Registries.ITEM.getId(stack.getItem()).toString());
        result.addProperty("count", stack.getCount());
        result.addProperty("displayName", stack.getName().getString());
        return result;
    }

    @FunctionalInterface
    private interface ClientAction {
        JsonObject run(MinecraftClient client);
    }
}
