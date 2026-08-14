package cuspymd.mcp.mod.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cuspymd.mcp.mod.config.MCPConfig;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MCPProtocol {
    private static final Set<String> DESCRIBABLE_COMMANDS = Set.of(
        "fill", "clone", "setblock", "summon", "tp", "give", "gamemode",
        "effect", "enchant", "weather", "time", "say", "tell", "title"
    );

    private MCPProtocol() {
    }

    public static JsonArray getToolsListResponse(MCPConfig config) {
        JsonArray tools = new JsonArray();
        List<String> configuredAllowedCommands = config != null && config.getServer() != null
            ? config.getServer().getAllowedCommands()
            : List.of();
        String allowedCommandsText = String.join(", ", filterAllowedCommandsForDescription(configuredAllowedCommands));

        tools.add(tool("execute_commands",
            "Execute one or more Minecraft commands sequentially. Allowed commands: " + allowedCommandsText + ".",
            objectSchema(props(
                "commands", arrayProperty("Commands without leading slash"),
                "validate_safety", booleanProperty("Whether to validate command safety. Default true")
            ), "commands")));

        if (config != null && config.getServer().isEnableUnsafeChatCommands()) {
            tools.add(tool("execute_chat_commands",
                "Execute arbitrary player chat commands for trusted admin/debug testing. Unsafe.",
                objectSchema(props("commands", arrayProperty("Commands with or without leading slash")), "commands")));
        }

        tools.add(tool("get_player_info", "Get current player position, facing, dimension, health, and inventory summary.", objectSchema(new JsonObject())));
        tools.add(tool("get_blocks_in_area", "Scan non-air blocks in a rectangular area.", objectSchema(props(
            "from", positionProperty("Start position"),
            "to", positionProperty("End position")
        ), "from", "to")));
        tools.add(tool("take_screenshot", "Capture a screenshot from the active Minecraft client and return it as an image result. Optional x/y/z/yaw/pitch arguments can move or rotate the player view before capture.", objectSchema(props(
            "x", numberProperty("Optional absolute X coordinate for a temporary camera move before capture"),
            "y", numberProperty("Optional absolute Y coordinate for a temporary camera move before capture"),
            "z", numberProperty("Optional absolute Z coordinate for a temporary camera move before capture"),
            "yaw", numberProperty("Optional yaw rotation in degrees"),
            "pitch", numberProperty("Optional pitch rotation in degrees")
        ))));
        if (config != null && config.getServer().isEnableGuiAutomationTools()) {
            addGuiAutomationTools(tools);
        }
        tools.add(tool("attack_block",
            "Start, hold, release, or hold-until-broken left mouse attack on a target block face. Use for real client-side block breaking.",
            objectSchema(props(
                "pos", positionProperty("Target block position"),
                "face", stringProperty("Block face: north, south, east, west, up, down"),
                "mode", stringProperty("start, hold, release, or break_until_done"),
                "ticks", integerProperty("Hold duration in ticks for hold/break_until_done. Default 1, max 200")
            ), "pos")));
        tools.add(tool("left_click_air", "Perform one attack swing without a block target.", objectSchema(new JsonObject())));
        tools.add(tool("right_click_block", "Right-click an exact block face with the held item.", objectSchema(props(
            "pos", positionProperty("Target block position"),
            "face", stringProperty("Block face: north, south, east, west, up, down"),
            "hand", stringProperty("main_hand or off_hand. Default main_hand")
        ), "pos", "face")));
        tools.add(tool("right_click_item", "Use held item in air.", objectSchema(props(
            "hand", stringProperty("main_hand or off_hand. Default main_hand")
        ))));
        tools.add(tool("set_held_slot", "Select hotbar slot 0-8.", objectSchema(props("slot", integerProperty("Hotbar slot 0-8")), "slot")));
        tools.add(tool("movement_input", "Press/release movement keys: forward, back, left, right, jump, sprint, sneak.", objectSchema(props(
            "keys", arrayProperty("Movement keys"),
            "pressed", booleanProperty("true presses, false releases. Default true"),
            "ticks", integerProperty("Ticks to hold before auto-release. 0 means no auto-release")
        ), "keys")));
        tools.add(tool("keybind_input",
            "Press, release, hold, or tap named Minecraft keybindings. Covers inventory, chat, command, attack/use, hotbar1-9, screenshot, perspective, fullscreen, and movement keybinds.",
            objectSchema(props(
                "keybind", stringProperty("Single keybind name, for example inventory, chat, attack, use, hotbar1, screenshot, perspective"),
                "keybinds", arrayProperty("Multiple keybind names"),
                "action", stringProperty("tap, press, release, or hold. Default tap"),
                "ticks", integerProperty("Ticks to hold when action=hold. Default 1; max 200")
            ))));
        tools.add(tool("keyboard_input",
            "Send raw keyboard key events to the current screen, or to global client key handling when no screen is open. Use keybind_input for gameplay actions where possible.",
            objectSchema(props(
                "key", stringProperty("Key name such as e, enter, escape, tab, space, up, down, f2, slash"),
                "action", stringProperty("tap, press, release, or hold. Default tap"),
                "ticks", integerProperty("Reserved hold duration in ticks. Default 1"),
                "modifiers", arrayProperty("Optional modifiers: shift, ctrl, alt, super"),
                "shift", booleanProperty("Set shift modifier"),
                "ctrl", booleanProperty("Set control modifier"),
                "alt", booleanProperty("Set alt modifier")
            ), "key")));
        tools.add(tool("mouse_input",
            "Send raw mouse events to the currently open screen: click, double_click, press, release, scroll, or drag using scaled GUI coordinates.",
            objectSchema(props(
                "action", stringProperty("click, double_click, press, release, scroll, or drag. Default click"),
                "x", numberProperty("Scaled GUI X coordinate. Defaults to screen center"),
                "y", numberProperty("Scaled GUI Y coordinate. Defaults to screen center"),
                "button", integerProperty("Mouse button index. 0 = left, 1 = right, 2 = middle. Default 0"),
                "scrollX", numberProperty("Horizontal scroll delta for action=scroll"),
                "scrollY", numberProperty("Vertical scroll delta for action=scroll"),
                "toX", numberProperty("Drag target scaled GUI X coordinate"),
                "toY", numberProperty("Drag target scaled GUI Y coordinate")
            ))));
        tools.add(tool("look_input",
            "Set or adjust player camera yaw/pitch. Use before screenshots or directional interaction tests.",
            objectSchema(props(
                "yaw", numberProperty("Absolute yaw in degrees"),
                "pitch", numberProperty("Absolute pitch in degrees, clamped -90..90"),
                "deltaYaw", numberProperty("Relative yaw adjustment"),
                "deltaPitch", numberProperty("Relative pitch adjustment")
            ))));
        tools.add(tool("open_inventory",
            "Open the player inventory screen on the client. Use when tests need a deterministic E/inventory screen state.",
            objectSchema(new JsonObject())));
        tools.add(tool("sneak", "Press, release, toggle, or hold sneak.", objectSchema(props(
            "mode", stringProperty("press, release, toggle, hold"),
            "ticks", integerProperty("Ticks for hold")
        ))));
        tools.add(tool("wait_for_chat", "Wait until recent/incoming chat contains text or regex.", objectSchema(props(
            "text", stringProperty("Literal text"),
            "regex", stringProperty("Java regex"),
            "timeout_ms", integerProperty("Timeout milliseconds")
        ))));
        tools.add(tool("get_scoreboard", "Return visible sidebar scoreboard title, ordered lines, and scores.", objectSchema(new JsonObject())));
        tools.add(tool("get_client_disconnect", "Return last captured disconnect screen text and exception details, if available.", objectSchema(new JsonObject())));
        tools.add(tool("get_bossbar_actionbar_titles", "Return visible actionbar/title/bossbar text captured or observable on client.", objectSchema(new JsonObject())));
        tools.add(tool("get_nearby_entities", "Return nearby entities including armor stands, projectiles, and items within radius.", objectSchema(props(
            "radius", numberProperty("Radius in blocks. Default 16, max 128")
        ))));
        tools.add(tool("get_recent_sounds_particles", "Return client-observed sound and particle events from last N seconds when capture hooks are available.", objectSchema(props(
            "seconds", numberProperty("Lookback seconds. Default 5, max 60")
        ))));
        return tools;
    }

    private static void addGuiAutomationTools(JsonArray tools) {
        tools.add(tool("get_current_screen",
            "Inspect current Minecraft screen. Returns title, class, size, buttons, list entries, and handled-screen slots.",
            objectSchema(new JsonObject())));
        tools.add(tool("click_screen_slot",
            "Click a slot in a handled inventory screen.",
            objectSchema(props(
                "slot", integerProperty("Target slot id from get_current_screen"),
                "button", integerProperty("Mouse button index. 0 = left, 1 = right. Default 0."),
                "action", stringProperty("Click type. Defaults to PICKUP. Supported values include PICKUP, QUICK_MOVE, SWAP, THROW, QUICK_CRAFT, PICKUP_ALL.")
            ), "slot")));
        tools.add(tool("click_screen_button",
            "Click a visible screen button by text or index. Use for main menu Singleplayer/Multiplayer and submenu buttons.",
            objectSchema(props(
                "text", stringProperty("Button text to match. Partial, case-insensitive by default."),
                "index", integerProperty("Button index from get_current_screen"),
                "exact", booleanProperty("Require exact text match. Default false."),
                "button", integerProperty("Mouse button index. 0 = left, 1 = right. Default 0."),
                "doubleClick", booleanProperty("Click twice. Default false.")
            ))));
        tools.add(tool("click_screen_entry",
            "Click a detected list entry by text/name or index. Use for Select World and Multiplayer server lists. Set doubleClick=true to open/join.",
            objectSchema(props(
                "text", stringProperty("Entry text to match. Partial, case-insensitive by default."),
                "entryText", stringProperty("Alias for text."),
                "name", stringProperty("Alias for text."),
                "index", integerProperty("Entry index from get_current_screen"),
                "exact", booleanProperty("Require exact text match. Default false."),
                "button", integerProperty("Mouse button index. 0 = left, 1 = right. Default 0."),
                "doubleClick", booleanProperty("Click twice. Default false.")
            ))));
        tools.add(tool("click_screen_xy",
            "Click raw scaled GUI coordinates on current screen.",
            objectSchema(props(
                "x", numberProperty("Scaled GUI X coordinate"),
                "y", numberProperty("Scaled GUI Y coordinate"),
                "button", integerProperty("Mouse button index. 0 = left, 1 = right. Default 0."),
                "doubleClick", booleanProperty("Click twice. Default false.")
            ), "x", "y")));
        tools.add(tool("type_text",
            "Type text into the current screen by invoking Minecraft screen keyboard handlers on the client thread. Newlines press Enter.",
            objectSchema(props(
                "text", stringProperty("Text to type. Use \\n for Enter."),
                "submit", booleanProperty("Press Enter after typing. Default false.")
            ), "text")));
        tools.add(tool("wait_for_screen",
            "Wait until current screen title/class matches.",
            objectSchema(props(
                "title", stringProperty("Exact screen title."),
                "titleContains", stringProperty("Case-insensitive title substring."),
                "titleRegex", stringProperty("Java regex matched against screen title."),
                "screenClass", stringProperty("Exact screen class name."),
                "classContains", stringProperty("Class name substring."),
                "timeout_ms", integerProperty("Timeout in milliseconds. Default 5000.")
            ))));
        tools.add(tool("close_current_screen", "Close current Minecraft screen.", objectSchema(new JsonObject())));
    }

    public static JsonObject createSuccessResponse(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("isError", false);
        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", message);
        content.add(textContent);
        response.add("content", content);
        return response;
    }

    public static JsonObject createErrorResponse(String message, JsonObject meta) {
        JsonObject response = new JsonObject();
        response.addProperty("isError", true);
        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", message);
        content.add(textContent);
        response.add("content", content);
        if (meta != null) {
            response.add("_meta", meta);
        }
        return response;
    }

    public static JsonObject createImageResponse(String base64Data, String mimeType) {
        JsonObject response = new JsonObject();
        response.addProperty("isError", false);
        JsonArray content = new JsonArray();
        JsonObject imageContent = new JsonObject();
        imageContent.addProperty("type", "image");
        imageContent.addProperty("data", base64Data);
        imageContent.addProperty("mimeType", mimeType);
        content.add(imageContent);
        response.add("content", content);
        return response;
    }

    private static List<String> filterAllowedCommandsForDescription(List<String> commands) {
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (String command : commands) {
            if (command == null) continue;
            String normalized = command.trim().toLowerCase();
            if (normalized.startsWith("/")) normalized = normalized.substring(1);
            if (DESCRIBABLE_COMMANDS.contains(normalized)) filtered.add(normalized);
        }
        return List.copyOf(filtered);
    }

    private static JsonObject tool(String name, String description, JsonObject inputSchema) {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", name);
        tool.addProperty("description", description);
        tool.add("inputSchema", inputSchema);
        return tool;
    }

    private static JsonObject objectSchema(JsonObject properties, String... requiredFields) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        if (requiredFields.length > 0) {
            JsonArray required = new JsonArray();
            for (String field : requiredFields) required.add(field);
            schema.add("required", required);
        }
        return schema;
    }

    private static JsonObject props(Object... entries) {
        JsonObject properties = new JsonObject();
        for (int i = 0; i < entries.length; i += 2) {
            properties.add((String) entries[i], (JsonObject) entries[i + 1]);
        }
        return properties;
    }

    private static JsonObject positionProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "object");
        property.addProperty("description", description);
        property.add("properties", props(
            "x", integerProperty("Block X"),
            "y", integerProperty("Block Y"),
            "z", integerProperty("Block Z")
        ));
        JsonArray required = new JsonArray();
        required.add("x");
        required.add("y");
        required.add("z");
        property.add("required", required);
        return property;
    }

    private static JsonObject integerProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "integer");
        property.addProperty("description", description);
        return property;
    }

    private static JsonObject stringProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        return property;
    }

    private static JsonObject booleanProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "boolean");
        property.addProperty("description", description);
        return property;
    }

    private static JsonObject numberProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "number");
        property.addProperty("description", description);
        return property;
    }

    private static JsonObject arrayProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "array");
        property.addProperty("description", description);
        JsonObject item = new JsonObject();
        item.addProperty("type", "string");
        property.add("items", item);
        return property;
    }
}
