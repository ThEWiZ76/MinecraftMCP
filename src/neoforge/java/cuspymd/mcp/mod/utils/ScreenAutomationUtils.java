package cuspymd.mcp.mod.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cuspymd.mcp.mod.server.MCPProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class ScreenAutomationUtils {
    private static final int DEFAULT_WAIT_TIMEOUT_MS = 5000;
    private static final int POLL_INTERVAL_MS = 50;

    private ScreenAutomationUtils() {
    }

    public static JsonObject inspectCurrentScreen() {
        return await(runOnClient(ScreenAutomationUtils::inspectCurrentScreenNow), "inspect current screen");
    }

    public static JsonObject clickScreenSlot(JsonObject params) {
        return await(runOnClient(client -> clickScreenSlotNow(client, params)), "click screen slot");
    }

    public static JsonObject clickScreenButton(JsonObject params) {
        return await(runOnClient(client -> clickScreenButtonNow(client, params)), "click screen button");
    }

    public static JsonObject clickScreenEntry(JsonObject params) {
        return await(runOnClient(client -> clickScreenEntryNow(client, params)), "click screen entry");
    }

    public static JsonObject clickScreenXy(JsonObject params) {
        return await(runOnClient(client -> clickScreenXyNow(client, params)), "click screen xy");
    }

    public static JsonObject typeText(JsonObject params) {
        return await(runOnClient(client -> typeTextNow(client, params)), "type text");
    }

    public static JsonObject waitForScreen(JsonObject params) {
        try {
            return MCPProtocol.createSuccessResponse(waitForScreenNow(params != null ? params : new JsonObject()).toString());
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Failed to wait for screen: " + e.getMessage(), null);
        }
    }

    public static JsonObject closeCurrentScreen() {
        return await(runOnClient(ScreenAutomationUtils::closeCurrentScreenNow), "close current screen");
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

    private static JsonObject inspectCurrentScreenNow(Minecraft client) {
        JsonObject result = new JsonObject();
        Screen screen = client.screen;
        result.addProperty("hasScreen", screen != null);
        if (screen == null) {
            return result;
        }

        result.addProperty("screenClass", screen.getClass().getName());
        result.addProperty("title", screen.getTitle().getString());
        result.addProperty("width", screen.width);
        result.addProperty("height", screen.height);
        result.addProperty("isHandledScreen", screen instanceof AbstractContainerScreen<?>);
        result.add("buttons", serializeButtons(screen));
        result.add("entries", serializeListEntries(screen));

        if (!(screen instanceof AbstractContainerScreen<?> handledScreen)) {
            return result;
        }

        AbstractContainerMenu menu = handledScreen.getMenu();
        result.addProperty("syncId", menu.containerId);
        result.add("cursorStack", serializeStack(menu.getCarried()));

        JsonArray slots = new JsonArray();
        for (Slot slot : menu.slots) {
            JsonObject slotJson = new JsonObject();
            slotJson.addProperty("id", slot.index);
            slotJson.addProperty("index", slot.getContainerSlot());
            slotJson.addProperty("x", slot.x);
            slotJson.addProperty("y", slot.y);
            slotJson.addProperty("hasStack", slot.hasItem());
            slotJson.add("stack", serializeStack(slot.getItem()));
            slots.add(slotJson);
        }
        result.add("slots", slots);
        return result;
    }

    private static JsonObject clickScreenSlotNow(Minecraft client, JsonObject params) {
        if (params == null || !params.has("slot")) {
            throw new IllegalArgumentException("Missing required parameter: slot");
        }
        if (client.player == null || client.gameMode == null) {
            throw new IllegalStateException("Player/game mode not available.");
        }
        if (!(client.screen instanceof AbstractContainerScreen<?> handledScreen)) {
            throw new IllegalStateException("Current screen is not a handled inventory screen.");
        }
        AbstractContainerMenu menu = handledScreen.getMenu();
        int slotId = params.get("slot").getAsInt();
        if (slotId < 0 || slotId >= menu.slots.size()) {
            throw new IllegalArgumentException("Slot " + slotId + " is outside range 0-" + (menu.slots.size() - 1));
        }
        int button = getInt(params, "button", 0);
        ClickType actionType = parseClickType(params != null && params.has("action") ? params.get("action").getAsString() : null);
        client.gameMode.handleInventoryMouseClick(menu.containerId, slotId, button, actionType, client.player);

        JsonObject result = new JsonObject();
        result.addProperty("clicked", true);
        result.addProperty("slot", slotId);
        result.addProperty("button", button);
        result.addProperty("action", actionType.name());
        result.add("screen", inspectCurrentScreenNow(client));
        return result;
    }

    private static JsonObject clickScreenButtonNow(Minecraft client, JsonObject params) {
        Screen screen = requireScreen(client);
        List<AbstractWidget> buttons = collectButtons(screen);
        int index = findButtonIndex(buttons, params);
        AbstractWidget widget = buttons.get(index);
        int button = getInt(params, "button", 0);
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        clickAt(screen, x, y, button, getBoolean(params, "doubleClick", false));

        JsonObject result = new JsonObject();
        result.addProperty("clicked", true);
        result.addProperty("buttonIndex", index);
        result.addProperty("buttonText", widget.getMessage().getString());
        result.addProperty("x", x);
        result.addProperty("y", y);
        result.add("screen", inspectCurrentScreenNow(client));
        return result;
    }

    private static JsonObject clickScreenEntryNow(Minecraft client, JsonObject params) {
        Screen screen = requireScreen(client);
        List<EntryInfo> entries = collectListEntries(screen);
        if (entries.isEmpty()) {
            throw new IllegalStateException("Current screen has no detectable list entries.");
        }
        int index = findEntryIndex(entries, params);
        EntryInfo entry = entries.get(index);
        int button = getInt(params, "button", 0);
        clickAt(screen, entry.centerX(), entry.centerY(), button, getBoolean(params, "doubleClick", false));

        JsonObject result = new JsonObject();
        result.addProperty("clicked", true);
        result.addProperty("entryIndex", index);
        result.addProperty("entryText", entry.text());
        result.addProperty("x", entry.centerX());
        result.addProperty("y", entry.centerY());
        result.add("screen", inspectCurrentScreenNow(client));
        return result;
    }

    private static JsonObject clickScreenXyNow(Minecraft client, JsonObject params) {
        Screen screen = requireScreen(client);
        if (params == null || !params.has("x") || !params.has("y")) {
            throw new IllegalArgumentException("Missing required parameters: x and y");
        }
        double x = params.get("x").getAsDouble();
        double y = params.get("y").getAsDouble();
        int button = getInt(params, "button", 0);
        clickAt(screen, x, y, button, getBoolean(params, "doubleClick", false));

        JsonObject result = new JsonObject();
        result.addProperty("clicked", true);
        result.addProperty("x", x);
        result.addProperty("y", y);
        result.addProperty("button", button);
        result.add("screen", inspectCurrentScreenNow(client));
        return result;
    }

    private static JsonObject typeTextNow(Minecraft client, JsonObject params) {
        Screen screen = requireScreen(client);
        if (params == null || !params.has("text")) {
            throw new IllegalArgumentException("Missing required parameter: text");
        }
        String text = params.get("text").getAsString();
        boolean submit = getBoolean(params, "submit", false);
        int typedCharacters = 0;
        int enterPresses = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\r') {
                continue;
            }
            if (codePoint == '\n') {
                pressKey(screen, GLFW.GLFW_KEY_ENTER);
                enterPresses++;
                continue;
            }
            if (!Character.isBmpCodePoint(codePoint)) {
                throw new IllegalArgumentException("Supplementary Unicode input is not supported by Minecraft Screen.charTyped.");
            }
            char character = (char) codePoint;
            int keyCode = keyCodeForCharacter(character);
            if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                screen.keyPressed(keyCode, 0, 0);
            }
            screen.charTyped(character, 0);
            if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                screen.keyReleased(keyCode, 0, 0);
            }
            typedCharacters++;
        }
        if (submit) {
            pressKey(screen, GLFW.GLFW_KEY_ENTER);
            enterPresses++;
        }

        JsonObject result = new JsonObject();
        result.addProperty("typedCharacters", typedCharacters);
        result.addProperty("enterPresses", enterPresses);
        result.add("screen", inspectCurrentScreenNow(client));
        return result;
    }

    private static JsonObject closeCurrentScreenNow(Minecraft client) {
        JsonObject result = new JsonObject();
        Screen screen = client.screen;
        result.addProperty("hadScreen", screen != null);
        if (screen == null) {
            result.addProperty("closed", false);
            return result;
        }
        screen.onClose();
        result.addProperty("closed", true);
        return result;
    }

    private static void pressKey(Screen screen, int keyCode) {
        screen.keyPressed(keyCode, 0, 0);
        screen.keyReleased(keyCode, 0, 0);
    }

    private static JsonObject waitForScreenNow(JsonObject params) {
        long timeoutMs = getInt(params, "timeout_ms", DEFAULT_WAIT_TIMEOUT_MS);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        JsonObject lastScreen = new JsonObject();
        while (System.nanoTime() <= deadline) {
            try {
                lastScreen = runOnClient(ScreenAutomationUtils::inspectCurrentScreenNow).get(500, TimeUnit.MILLISECONDS);
                if (matchesScreen(lastScreen, params)) {
                    JsonObject result = lastScreen.deepCopy();
                    result.addProperty("matched", true);
                    return result;
                }
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for screen", e);
            } catch (Exception e) {
                throw new IllegalStateException("Failed while waiting for screen", e);
            }
        }
        JsonObject result = lastScreen.deepCopy();
        result.addProperty("matched", false);
        result.addProperty("timeout_ms", timeoutMs);
        return result;
    }

    private static Screen requireScreen(Minecraft client) {
        Screen screen = client.screen;
        if (screen == null) {
            throw new IllegalStateException("No current screen is open.");
        }
        return screen;
    }

    private static ClickType parseClickType(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ClickType.PICKUP;
        }
        return ClickType.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
    }

    private static JsonArray serializeButtons(Screen screen) {
        JsonArray array = new JsonArray();
        List<AbstractWidget> buttons = collectButtons(screen);
        for (int i = 0; i < buttons.size(); i++) {
            AbstractWidget button = buttons.get(i);
            JsonObject item = new JsonObject();
            item.addProperty("index", i);
            item.addProperty("text", button.getMessage().getString());
            item.addProperty("x", button.getX());
            item.addProperty("y", button.getY());
            item.addProperty("width", button.getWidth());
            item.addProperty("height", button.getHeight());
            item.addProperty("active", button.active);
            item.addProperty("visible", button.visible);
            array.add(item);
        }
        return array;
    }

    private static List<AbstractWidget> collectButtons(Screen screen) {
        List<AbstractWidget> buttons = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                buttons.add(widget);
            }
        }
        return buttons;
    }

    private static int findButtonIndex(List<AbstractWidget> buttons, JsonObject params) {
        if (buttons.isEmpty()) {
            throw new IllegalStateException("Current screen has no clickable buttons.");
        }
        if (params != null && params.has("index")) {
            int index = params.get("index").getAsInt();
            if (index < 0 || index >= buttons.size()) {
                throw new IllegalArgumentException("Button index " + index + " is outside range 0-" + (buttons.size() - 1));
            }
            return index;
        }
        String text = params != null && params.has("text") ? params.get("text").getAsString() : null;
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: text or index");
        }
        boolean exact = getBoolean(params, "exact", false);
        String expected = normalize(text);
        for (int i = 0; i < buttons.size(); i++) {
            String actual = normalize(buttons.get(i).getMessage().getString());
            if ((exact && actual.equals(expected)) || (!exact && actual.contains(expected))) {
                return i;
            }
        }
        throw new IllegalArgumentException("No button matched text: " + text);
    }

    private static JsonArray serializeListEntries(Screen screen) {
        JsonArray array = new JsonArray();
        List<EntryInfo> entries = collectListEntries(screen);
        for (int i = 0; i < entries.size(); i++) {
            EntryInfo entry = entries.get(i);
            JsonObject item = new JsonObject();
            item.addProperty("index", i);
            item.addProperty("text", entry.text());
            item.addProperty("x", entry.centerX());
            item.addProperty("y", entry.centerY());
            item.addProperty("top", entry.top());
            item.addProperty("bottom", entry.bottom());
            item.addProperty("entryClass", entry.entry().getClass().getName());
            array.add(item);
        }
        return array;
    }

    private static List<EntryInfo> collectListEntries(Screen screen) {
        List<EntryInfo> entries = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            List<?> childEntries = invokeChildren(child);
            if (childEntries == null || childEntries.isEmpty()) {
                continue;
            }
            for (int i = 0; i < childEntries.size(); i++) {
                Object entry = childEntries.get(i);
                if (entry instanceof AbstractWidget || entry instanceof Screen) {
                    continue;
                }
                int top = invokeInt(child, "getRowTop", i, 32 + i * 36);
                int bottom = invokeInt(child, "getRowBottom", i, top + 36);
                int left = invokeInt(child, "getRowLeft", screen.width / 2 - 154);
                int width = invokeInt(child, "getRowWidth", 308);
                String text = extractText(entry);
                entries.add(new EntryInfo(entry, text, left + width / 2.0, (top + bottom) / 2.0, top, bottom));
            }
        }
        return entries;
    }

    private static int findEntryIndex(List<EntryInfo> entries, JsonObject params) {
        if (params != null && params.has("index")) {
            int index = params.get("index").getAsInt();
            if (index < 0 || index >= entries.size()) {
                throw new IllegalArgumentException("Entry index " + index + " is outside range 0-" + (entries.size() - 1));
            }
            return index;
        }
        String text = getFirstString(params, "text", "entryText", "name");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: text, entryText, name, or index");
        }
        boolean exact = getBoolean(params, "exact", false);
        String expected = normalize(text);
        for (int i = 0; i < entries.size(); i++) {
            String actual = normalize(entries.get(i).text());
            if ((exact && actual.equals(expected)) || (!exact && actual.contains(expected))) {
                return i;
            }
        }
        throw new IllegalArgumentException("No screen entry matched text: " + text);
    }

    private static void clickAt(Screen screen, double x, double y, int button, boolean doubleClick) {
        screen.mouseClicked(x, y, button);
        screen.mouseReleased(x, y, button);
        if (doubleClick) {
            screen.mouseClicked(x, y, button);
            screen.mouseReleased(x, y, button);
        }
    }

    private static boolean matchesScreen(JsonObject screen, JsonObject params) {
        if (params == null || params.entrySet().isEmpty()) {
            return screen.has("hasScreen") && screen.get("hasScreen").getAsBoolean();
        }
        String title = screen.has("title") ? screen.get("title").getAsString() : "";
        String screenClass = screen.has("screenClass") ? screen.get("screenClass").getAsString() : "";
        if (params.has("title") && !title.equals(params.get("title").getAsString())) {
            return false;
        }
        if (params.has("titleContains") && !normalize(title).contains(normalize(params.get("titleContains").getAsString()))) {
            return false;
        }
        if (params.has("titleRegex") && !Pattern.compile(params.get("titleRegex").getAsString()).matcher(title).find()) {
            return false;
        }
        if (params.has("screenClass") && !screenClass.equals(params.get("screenClass").getAsString())) {
            return false;
        }
        if (params.has("classContains") && !screenClass.contains(params.get("classContains").getAsString())) {
            return false;
        }
        return true;
    }

    private static JsonObject serializeStack(ItemStack stack) {
        JsonObject result = new JsonObject();
        boolean present = stack != null && !stack.isEmpty();
        result.addProperty("present", present);
        if (!present) {
            return result;
        }
        result.addProperty("itemId", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        result.addProperty("count", stack.getCount());
        result.addProperty("displayName", stack.getHoverName().getString());
        return result;
    }

    private static List<?> invokeChildren(Object target) {
        try {
            Method method = target.getClass().getMethod("children");
            Object value = method.invoke(target);
            return value instanceof List<?> list ? list : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int invokeInt(Object target, String methodName, int argument, int fallback) {
        try {
            Method method = findMethod(target.getClass(), methodName, int.class);
            if (method == null) {
                return fallback;
            }
            method.setAccessible(true);
            Object value = method.invoke(target, argument);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int invokeInt(Object target, String methodName, int fallback) {
        try {
            Method method = findMethod(target.getClass(), methodName);
            if (method == null) {
                return fallback;
            }
            method.setAccessible(true);
            Object value = method.invoke(target);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Method findMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String extractText(Object value) {
        StringBuilder builder = new StringBuilder();
        collectText(value, builder, new IdentityHashMap<>(), 0);
        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    private static void collectText(Object value, StringBuilder builder, Map<Object, Boolean> seen, int depth) {
        if (value == null || depth > 3 || seen.containsKey(value)) {
            return;
        }
        seen.put(value, Boolean.TRUE);
        if (value instanceof CharSequence text) {
            appendText(builder, text.toString());
            return;
        }
        Class<?> type = value.getClass();
        String className = type.getName();
        if (className.endsWith("Text") || className.endsWith("Component")) {
            String text = invokeString(value, "getString");
            appendText(builder, text != null ? text : value.toString());
            return;
        }
        String directName = invokeString(value, "getName");
        if (directName != null) {
            appendText(builder, directName);
        }
        String levelName = invokeString(value, "getLevelName");
        if (levelName != null) {
            appendText(builder, levelName);
        }
        if (type.isPrimitive() || value instanceof Number || value instanceof Boolean || type.isEnum()) {
            return;
        }
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                if (fieldValue instanceof CharSequence || isTextLike(fieldValue)) {
                    collectText(fieldValue, builder, seen, depth + 1);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isTextLike(Object value) {
        return value != null && (value.getClass().getName().endsWith("Text") || value.getClass().getName().endsWith("Component"));
    }

    private static String invokeString(Object target, String methodName) {
        try {
            Method method = findMethod(target.getClass(), methodName);
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            Object value = method.invoke(target);
            return value instanceof String string ? string : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void appendText(StringBuilder builder, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(text.trim());
    }

    private static int getInt(JsonObject params, String key, int defaultValue) {
        return params != null && params.has(key) ? params.get(key).getAsInt() : defaultValue;
    }

    private static boolean getBoolean(JsonObject params, String key, boolean defaultValue) {
        return params != null && params.has(key) ? params.get(key).getAsBoolean() : defaultValue;
    }

    static int keyCodeForCharacter(char character) {
        if (character >= 'a' && character <= 'z') {
            return GLFW.GLFW_KEY_A + (character - 'a');
        }
        if (character >= 'A' && character <= 'Z') {
            return GLFW.GLFW_KEY_A + (character - 'A');
        }
        if (character >= '1' && character <= '9') {
            return GLFW.GLFW_KEY_1 + (character - '1');
        }
        return switch (character) {
            case '0', ')' -> GLFW.GLFW_KEY_0;
            case '!' -> GLFW.GLFW_KEY_1;
            case '@' -> GLFW.GLFW_KEY_2;
            case '#' -> GLFW.GLFW_KEY_3;
            case '$' -> GLFW.GLFW_KEY_4;
            case '%' -> GLFW.GLFW_KEY_5;
            case '^' -> GLFW.GLFW_KEY_6;
            case '&' -> GLFW.GLFW_KEY_7;
            case '*' -> GLFW.GLFW_KEY_8;
            case '(' -> GLFW.GLFW_KEY_9;
            case '-', '_' -> GLFW.GLFW_KEY_MINUS;
            case '=', '+' -> GLFW.GLFW_KEY_EQUAL;
            case '[', '{' -> GLFW.GLFW_KEY_LEFT_BRACKET;
            case ']', '}' -> GLFW.GLFW_KEY_RIGHT_BRACKET;
            case ';', ':' -> GLFW.GLFW_KEY_SEMICOLON;
            case '\'', '"' -> GLFW.GLFW_KEY_APOSTROPHE;
            case '`', '~' -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            case '\\', '|' -> GLFW.GLFW_KEY_BACKSLASH;
            case ',', '<' -> GLFW.GLFW_KEY_COMMA;
            case '.', '>' -> GLFW.GLFW_KEY_PERIOD;
            case '/', '?' -> GLFW.GLFW_KEY_SLASH;
            case ' ' -> GLFW.GLFW_KEY_SPACE;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    private static String getFirstString(JsonObject params, String... keys) {
        if (params == null) {
            return null;
        }
        for (String key : keys) {
            if (params.has(key)) {
                return params.get(key).getAsString();
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
    }

    private record EntryInfo(Object entry, String text, double centerX, double centerY, int top, int bottom) {
    }

    @FunctionalInterface
    private interface ClientAction {
        JsonObject run(Minecraft client);
    }
}
