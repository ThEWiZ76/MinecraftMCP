package cuspymd.mcp.mod.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cuspymd.mcp.mod.config.MCPConfig;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MCPProtocol {
    private static final Set<String> DESCRIBABLE_COMMANDS = Set.of(
        "fill", "clone", "setblock", "summon", "tp", "give", "gamemode",
        "effect", "enchant", "weather", "time", "say", "tell", "title"
    );
    
    public static JsonArray getToolsListResponse(MCPConfig config) {
        JsonArray tools = new JsonArray();
        List<String> configuredAllowedCommands = (config != null && config.getServer() != null && config.getServer().getAllowedCommands() != null)
            ? config.getServer().getAllowedCommands()
            : List.of("fill", "clone", "setblock", "summon", "tp", "give", "gamemode", "effect", "enchant", "weather", "time", "say", "tell", "title");
        List<String> allowedCommands = filterAllowedCommandsForDescription(configuredAllowedCommands);
        String allowedCommandsText = allowedCommands.isEmpty() ? "(none configured)" : String.join(", ", allowedCommands);
        
        // Execute commands tool
        JsonObject executeCommandsTool = new JsonObject();
        executeCommandsTool.addProperty("name", "execute_commands");
        executeCommandsTool.addProperty("description",
            "Execute one or more Minecraft commands sequentially. " +
            "Allowed commands: " + allowedCommandsText + ".\n\n" +
            "Response schema highlights:\n" +
            "- top-level: totalCommands, acceptedCount, appliedCount, failedCount\n" +
            "- per command: status, accepted, applied, summary, chatMessages\n" +
            "- status values: applied, rejected_by_game, execution_error, timed_out, rejected_by_safety, unknown\n\n" +
            "BLOCK STATE SYNTAX (critical for quality builds):\n" +
            "- Doors: setblock X Y Z oak_door[facing=north,half=lower,hinge=left,open=false] then setblock X Y+1 Z oak_door[facing=north,half=upper,hinge=left,open=false]\n" +
            "- Stairs: setblock X Y Z oak_stairs[facing=east,half=bottom,shape=straight]\n" +
            "- Slabs: setblock X Y Z oak_slab[type=top] or [type=bottom] or [type=double]\n" +
            "- Trapdoors: setblock X Y Z oak_trapdoor[facing=north,half=top,open=false]\n" +
            "- Fences/Walls: placed adjacently they auto-connect\n" +
            "- Logs/Pillars: setblock X Y Z oak_log[axis=y] (y=vertical, x/z=horizontal)\n" +
            "- Glazed Terracotta: [facing=north/south/east/west]\n" +
            "- Beds: setblock X Y Z red_bed[facing=south,part=foot] then setblock X Y Z+1 red_bed[facing=south,part=head] (head goes in facing direction: south=+Z, north=-Z, east=+X, west=-X)\n" +
            "- Chests: setblock X Y Z chest[facing=north]\n" +
            "- Torches: torch (floor), wall_torch[facing=north] (wall)\n" +
            "- Lanterns: lantern[hanging=true/false]\n" +
            "- Glass Panes: auto-connect to adjacent blocks\n\n" +
            "FILL COMMAND SYNTAX:\n" +
            "- fill X1 Y1 Z1 X2 Y2 Z2 <block> [replace|hollow|outline|destroy|keep]\n" +
            "- 'hollow' fills outer shell with block, inner with air - great for rooms\n" +
            "- 'outline' fills only outer shell, keeps interior unchanged\n" +
            "- 'replace <oldBlock>' replaces only matching blocks\n" +
            "- fill X1 Y1 Z1 X2 Y2 Z2 air replace <block> - removes specific block type\n\n" +
            "BUILDING BEST PRACTICES:\n" +
            "1. Use get_player_info first to get your position and use ABSOLUTE coordinates (not relative ~)\n" +
            "2. Plan structure dimensions before building. Typical house: 7-11 wide, 5-7 tall, 9-13 deep\n" +
            "3. Build in order: foundation -> walls (use fill hollow) -> roof -> windows/doors -> interior -> decoration\n" +
            "4. Doors MUST have two blocks: lower half (half=lower) and upper half (half=upper) at Y+1\n" +
            "5. Windows: use glass_pane, not glass (panes look much better)\n" +
            "6. Roofs: use stairs with correct facing for sloped roofs, slabs for flat roofs\n" +
            "7. After building, ALWAYS verify with get_blocks_in_area to check for errors\n" +
            "8. Group related commands in one call (e.g., all wall commands together) for efficiency\n" +
            "9. Max fill volume: 32768 blocks per command (Minecraft limit). Max entities per summon: 10"
        );

        JsonObject inputSchema = new JsonObject();
        inputSchema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject commandsProperty = new JsonObject();
        commandsProperty.addProperty("type", "array");
        commandsProperty.addProperty("description", "Array of Minecraft commands to execute without leading slash. Each command is executed sequentially. Per-command results include status, accepted/applied booleans, summary, and command-scoped chat messages.");
        commandsProperty.addProperty("minItems", 1);
        
        JsonObject commandsItems = new JsonObject();
        commandsItems.addProperty("type", "string");
        commandsProperty.add("items", commandsItems);
        
        JsonObject validateSafetyProperty = new JsonObject();
        validateSafetyProperty.addProperty("type", "boolean");
        validateSafetyProperty.addProperty("description", "Whether to validate command safety (default: true)");
        validateSafetyProperty.addProperty("default", true);
        
        properties.add("commands", commandsProperty);
        properties.add("validate_safety", validateSafetyProperty);
        inputSchema.add("properties", properties);
        
        JsonArray required = new JsonArray();
        required.add("commands");
        inputSchema.add("required", required);
        
        executeCommandsTool.add("inputSchema", inputSchema);
        tools.add(executeCommandsTool);

        if (config != null && config.getServer() != null && config.getServer().isEnableUnsafeChatCommands()) {
            JsonObject executeChatCommandsTool = new JsonObject();
            executeChatCommandsTool.addProperty("name", "execute_chat_commands");
            executeChatCommandsTool.addProperty("description",
                "Execute one or more arbitrary player chat commands sequentially for admin/debug/plugin testing. " +
                "This tool is intentionally unsafe and bypasses the normal execute_commands allowlist and safety validator.\n\n" +
                "Use this for plugin commands such as /customgear, /mtgrinding, or other server commands that are not part of the safe vanilla tool.\n\n" +
                "Input accepts either '/command args' or 'command args'. Commands are normalized before dispatch. " +
                "For large FastAsyncWorldEdit operations, set auto_confirm_large_edits=true to send the configured confirm command immediately after each submitted command.\n\n" +
                "Response schema mirrors execute_commands where practical, including per-command status, summary, and captured chat messages."
            );

            JsonObject unsafeInputSchema = inputSchema.deepCopy();
            JsonObject unsafeProperties = unsafeInputSchema.getAsJsonObject("properties");

            JsonObject autoConfirmProperty = new JsonObject();
            autoConfirmProperty.addProperty("type", "boolean");
            autoConfirmProperty.addProperty("description", "Automatically send the FastAsyncWorldEdit confirm command immediately after each submitted command (default: false).");
            autoConfirmProperty.addProperty("default", false);

            JsonObject confirmCommandProperty = new JsonObject();
            confirmCommandProperty.addProperty("type", "string");
            confirmCommandProperty.addProperty("description", "Command used when auto_confirm_large_edits is true (default: /fastasyncworldedit:/confirm).");
            confirmCommandProperty.addProperty("default", "/fastasyncworldedit:/confirm");

            unsafeProperties.add("auto_confirm_large_edits", autoConfirmProperty);
            unsafeProperties.add("confirm_command", confirmCommandProperty);

            executeChatCommandsTool.add("inputSchema", unsafeInputSchema);
            tools.add(executeChatCommandsTool);
        }
        
        // Get player info tool
        JsonObject getPlayerInfoTool = new JsonObject();
        getPlayerInfoTool.addProperty("name", "get_player_info");
        getPlayerInfoTool.addProperty("description",
            "Get current player position and world context. CALL THIS FIRST before any building task to get absolute coordinates.\n\n" +
            "Returns:\n" +
            "- blockPosition: {x,y,z} integer coordinates - USE THESE for setblock/fill commands\n" +
            "- position: {x,y,z} exact floating-point coordinates\n" +
            "- facingDirection: cardinal direction (North/South/East/West)\n" +
            "- frontPosition: {x,y,z} 3 blocks ahead of player - good starting point for builds\n" +
            "- gameMode, dimension, timeOfDay, health, foodLevel, inventory\n\n" +
            "IMPORTANT: Minecraft Y-axis is vertical (Y=64 is typical ground level). " +
            "Use blockPosition for command coordinates. " +
            "Build at frontPosition or offset from blockPosition using absolute coordinates for reliability."
        );
        
        JsonObject playerInfoInputSchema = new JsonObject();
        playerInfoInputSchema.addProperty("type", "object");
        
        JsonObject playerInfoProperties = new JsonObject();
        // No required parameters for this tool
        playerInfoInputSchema.add("properties", playerInfoProperties);
        
        getPlayerInfoTool.add("inputSchema", playerInfoInputSchema);
        tools.add(getPlayerInfoTool);
        
        // Get blocks in area tool
        JsonObject getBlocksInAreaTool = new JsonObject();
        getBlocksInAreaTool.addProperty("name", "get_blocks_in_area");
        int maxAreaSize = config != null ? config.getServer().getMaxAreaSize() : 48;
        getBlocksInAreaTool.addProperty("description",
            "Scan and return all non-air blocks in a rectangular area. Use this to VERIFY builds after construction.\n\n" +
            "Maximum " + maxAreaSize + " blocks per axis. Air blocks are excluded. " +
            "Returns compressed block data grouped by type with regions (connected areas) and single blocks.\n\n" +
            "USAGE: After building, scan the build area to verify:\n" +
            "- All walls are complete (no gaps)\n" +
            "- Doors have both upper and lower halves\n" +
            "- Roof is fully covered\n" +
            "- Windows are placed correctly\n" +
            "If you find errors, use execute_commands to fix them."
        );
        
        JsonObject blocksInputSchema = new JsonObject();
        blocksInputSchema.addProperty("type", "object");
        
        JsonObject blocksProperties = new JsonObject();
        
        // From position
        JsonObject fromProperty = new JsonObject();
        fromProperty.addProperty("type", "object");
        fromProperty.addProperty("description", "Starting position of the area to scan");
        JsonObject fromPosProperties = new JsonObject();
        JsonObject xProp = new JsonObject();
        xProp.addProperty("type", "integer");
        JsonObject yProp = new JsonObject();
        yProp.addProperty("type", "integer");
        JsonObject zProp = new JsonObject();
        zProp.addProperty("type", "integer");
        fromPosProperties.add("x", xProp);
        fromPosProperties.add("y", yProp);
        fromPosProperties.add("z", zProp);
        fromProperty.add("properties", fromPosProperties);
        JsonArray fromRequired = new JsonArray();
        fromRequired.add("x");
        fromRequired.add("y");
        fromRequired.add("z");
        fromProperty.add("required", fromRequired);
        
        // To position
        JsonObject toProperty = new JsonObject();
        toProperty.addProperty("type", "object");
        toProperty.addProperty("description", "Ending position of the area to scan");
        JsonObject toPosProperties = new JsonObject();
        toPosProperties.add("x", xProp);
        toPosProperties.add("y", yProp);
        toPosProperties.add("z", zProp);
        toProperty.add("properties", toPosProperties);
        JsonArray toRequired = new JsonArray();
        toRequired.add("x");
        toRequired.add("y");
        toRequired.add("z");
        toProperty.add("required", toRequired);
        
        blocksProperties.add("from", fromProperty);
        blocksProperties.add("to", toProperty);
        blocksInputSchema.add("properties", blocksProperties);
        
        JsonArray blocksRequiredFields = new JsonArray();
        blocksRequiredFields.add("from");
        blocksRequiredFields.add("to");
        blocksInputSchema.add("required", blocksRequiredFields);
        
        getBlocksInAreaTool.add("inputSchema", blocksInputSchema);
        tools.add(getBlocksInAreaTool);

        JsonObject takeScreenshotTool = new JsonObject();
        takeScreenshotTool.addProperty("name", "take_screenshot");
        takeScreenshotTool.addProperty("description",
            "Capture a screenshot from the active Minecraft client and return it as an image result. " +
            "Optional x/y/z/yaw/pitch arguments can reposition or rotate the player view before capture."
        );

        JsonObject screenshotInputSchema = new JsonObject();
        screenshotInputSchema.addProperty("type", "object");
        JsonObject screenshotProperties = new JsonObject();
        screenshotProperties.add("x", numberProperty("number", "Optional absolute X coordinate for a temporary camera move before capture"));
        screenshotProperties.add("y", numberProperty("number", "Optional absolute Y coordinate for a temporary camera move before capture"));
        screenshotProperties.add("z", numberProperty("number", "Optional absolute Z coordinate for a temporary camera move before capture"));
        screenshotProperties.add("yaw", numberProperty("number", "Optional yaw rotation in degrees"));
        screenshotProperties.add("pitch", numberProperty("number", "Optional pitch rotation in degrees"));
        screenshotInputSchema.add("properties", screenshotProperties);
        takeScreenshotTool.add("inputSchema", screenshotInputSchema);
        tools.add(takeScreenshotTool);

        if (config != null && config.getServer() != null && config.getServer().isEnableGuiAutomationTools()) {
            JsonObject getCurrentScreenTool = new JsonObject();
            getCurrentScreenTool.addProperty("name", "get_current_screen");
            getCurrentScreenTool.addProperty("description",
                "Inspect the currently open Minecraft screen. For handled inventory screens this returns title, screen class, sync id, cursor stack, and slot contents."
            );
            JsonObject emptyInputSchema = new JsonObject();
            emptyInputSchema.addProperty("type", "object");
            emptyInputSchema.add("properties", new JsonObject());
            getCurrentScreenTool.add("inputSchema", emptyInputSchema);
            tools.add(getCurrentScreenTool);

            JsonObject clickScreenSlotTool = new JsonObject();
            clickScreenSlotTool.addProperty("name", "click_screen_slot");
            clickScreenSlotTool.addProperty("description",
                "Click a slot in the currently open handled screen. Intended for GUI testing. " +
                "Defaults to a left-click pickup action. Use this together with get_current_screen."
            );
            JsonObject clickInputSchema = new JsonObject();
            clickInputSchema.addProperty("type", "object");
            JsonObject clickProperties = new JsonObject();
            clickProperties.add("slot", integerProperty("Target slot id from get_current_screen"));
            clickProperties.add("button", integerProperty("Mouse button index. 0 = left, 1 = right"));
            JsonObject actionProperty = new JsonObject();
            actionProperty.addProperty("type", "string");
            actionProperty.addProperty("description", "Slot action type. Defaults to PICKUP. Supported values include PICKUP, QUICK_MOVE, SWAP, THROW, QUICK_CRAFT, PICKUP_ALL.");
            clickProperties.add("action", actionProperty);
            clickInputSchema.add("properties", clickProperties);
            JsonArray clickRequired = new JsonArray();
            clickRequired.add("slot");
            clickInputSchema.add("required", clickRequired);
            clickScreenSlotTool.add("inputSchema", clickInputSchema);
            tools.add(clickScreenSlotTool);

            JsonObject closeCurrentScreenTool = new JsonObject();
            closeCurrentScreenTool.addProperty("name", "close_current_screen");
            closeCurrentScreenTool.addProperty("description",
                "Close the currently open Minecraft screen on the client."
            );
            closeCurrentScreenTool.add("inputSchema", emptyInputSchema.deepCopy());
            tools.add(closeCurrentScreenTool);
        }

        addClientInteractionTools(tools);
        
        return tools;
    }

    private static void addClientInteractionTools(JsonArray tools) {
        tools.add(tool("attack_block",
            "Start, hold, release, or hold-until-broken left mouse attack on a target block face. Use for real client-side block breaking.",
            objectSchema(props(
                "pos", positionProperty("Target block position"),
                "face", stringProperty("Block face: north, south, east, west, up, down"),
                "mode", stringProperty("start, hold, release, or break_until_done"),
                "ticks", integerProperty("Hold duration in ticks for hold/break_until_done. Default 1, max 200")
            ), "pos")));
        tools.add(tool("left_click_air",
            "Perform one attack swing without a block target. Use for weapons and special held items.",
            objectSchema(new JsonObject())));
        tools.add(tool("right_click_block",
            "Right-click an exact block face with the held item.",
            objectSchema(props(
                "pos", positionProperty("Target block position"),
                "face", stringProperty("Block face: north, south, east, west, up, down"),
                "hand", stringProperty("main_hand or off_hand. Default main_hand")
            ), "pos", "face")));
        tools.add(tool("right_click_item",
            "Use held item in air.",
            objectSchema(props("hand", stringProperty("main_hand or off_hand. Default main_hand")))));
        tools.add(tool("set_held_slot",
            "Select hotbar slot 0-8.",
            objectSchema(props("slot", integerProperty("Hotbar slot index 0-8")), "slot")));
        tools.add(tool("movement_input",
            "Press or release movement keys for real client movement. Keys: forward, back, left, right, jump, sprint, sneak.",
            objectSchema(props(
                "keys", arrayProperty("Movement keys to change"),
                "pressed", booleanProperty("true presses keys, false releases keys. Default true"),
                "ticks", integerProperty("Ticks to hold before auto-release. Default 1; 0 means no auto-release")
            ), "keys")));
        tools.add(tool("sneak",
            "Press, release, toggle, or hold sneak/shift.",
            objectSchema(props(
                "mode", stringProperty("press, release, toggle, or hold"),
                "ticks", integerProperty("Ticks to hold when mode=hold")
            ))));
        tools.add(tool("wait_for_chat",
            "Wait until recent or incoming chat contains text or matches regex.",
            objectSchema(props(
                "text", stringProperty("Literal text to find"),
                "regex", stringProperty("Regex to match"),
                "timeout_ms", integerProperty("Timeout in milliseconds. Default 5000")
            ))));
        tools.add(tool("get_scoreboard",
            "Return visible sidebar scoreboard title, ordered lines, and scores.",
            objectSchema(new JsonObject())));
        tools.add(tool("get_client_disconnect",
            "Return last captured disconnect screen text and exception details, if any.",
            objectSchema(new JsonObject())));
        tools.add(tool("get_bossbar_actionbar_titles",
            "Return visible actionbar/title/bossbar text captured or observable on client.",
            objectSchema(new JsonObject())));
        tools.add(tool("get_nearby_entities",
            "Return nearby entities including armor stands, projectiles, and items within radius.",
            objectSchema(props("radius", numberProperty("number", "Radius in blocks. Default 16, max 128")))));
        tools.add(tool("get_recent_sounds_particles",
            "Return client-observed sound and particle events from last N seconds.",
            objectSchema(props("seconds", numberProperty("number", "Lookback seconds. Default 5, max 60")))));
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

    private static List<String> filterAllowedCommandsForDescription(List<String> configuredAllowedCommands) {
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (String command : configuredAllowedCommands) {
            if (command == null) {
                continue;
            }
            String normalized = command.trim().toLowerCase();
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            if (!normalized.isEmpty() && DESCRIBABLE_COMMANDS.contains(normalized)) {
                filtered.add(normalized);
            }
        }
        return List.copyOf(filtered);
    }

    private static JsonObject numberProperty(String type, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", type);
        property.addProperty("description", description);
        return property;
    }

    private static JsonObject integerProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "integer");
        property.addProperty("description", description);
        return property;
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
            for (String field : requiredFields) {
                required.add(field);
            }
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
