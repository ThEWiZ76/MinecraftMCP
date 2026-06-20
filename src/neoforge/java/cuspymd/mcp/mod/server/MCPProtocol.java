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
        tools.add(tool("set_held_slot", "Select hotbar slot 0-8.", objectSchema(props("slot", integerProperty("Hotbar slot 0-8")), "slot")));
        tools.add(tool("movement_input", "Press/release movement keys: forward, back, left, right, jump, sprint, sneak.", objectSchema(props(
            "keys", arrayProperty("Movement keys"),
            "pressed", booleanProperty("true presses, false releases. Default true"),
            "ticks", integerProperty("Ticks to hold before auto-release. 0 means no auto-release")
        ), "keys")));
        tools.add(tool("sneak", "Press, release, toggle, or hold sneak.", objectSchema(props(
            "mode", stringProperty("press, release, toggle, hold"),
            "ticks", integerProperty("Ticks for hold")
        ))));
        tools.add(tool("wait_for_chat", "Wait until recent/incoming chat contains text or regex.", objectSchema(props(
            "text", stringProperty("Literal text"),
            "regex", stringProperty("Java regex"),
            "timeout_ms", integerProperty("Timeout milliseconds")
        ))));
        return tools;
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
