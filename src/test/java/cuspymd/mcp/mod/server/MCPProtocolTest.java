package cuspymd.mcp.mod.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cuspymd.mcp.mod.config.MCPConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MCPProtocolTest {
    private static final Gson GSON = new Gson();

    @Test
    public void executeCommandsDescriptionUsesFilteredAllowList() {
        MCPConfig config = GSON.fromJson("""
            {
              "server": {
                "allowedCommands": ["tp", "op", "/fill", "reload"]
              }
            }
            """, MCPConfig.class);

        JsonArray tools = MCPProtocol.getToolsListResponse(config);
        JsonObject executeCommandsTool = tools.get(0).getAsJsonObject();
        String description = executeCommandsTool.get("description").getAsString();
        String allowedLine = description.split("\\n\\n", 2)[0];

        assertTrue(allowedLine.contains("Allowed commands: tp, fill."));
        assertFalse(allowedLine.contains(" op"));
        assertFalse(allowedLine.contains("reload"));
        assertTrue(description.contains("acceptedCount"));
        assertTrue(description.contains("status values: applied, rejected_by_game, execution_error, timed_out, rejected_by_safety, unknown"));
    }

    @Test
    public void unsafeChatToolIsHiddenByDefault() {
        MCPConfig config = GSON.fromJson("""
            {
              "server": {
                "allowedCommands": ["tp", "fill"]
              }
            }
            """, MCPConfig.class);

        JsonArray tools = MCPProtocol.getToolsListResponse(config);

        assertFalse(containsToolNamed(tools, "execute_chat_commands"));
    }

    @Test
    public void unsafeChatToolIsExposedWhenEnabled() {
        MCPConfig config = GSON.fromJson("""
            {
              "server": {
                "enableUnsafeChatCommands": true
              }
            }
            """, MCPConfig.class);

        JsonArray tools = MCPProtocol.getToolsListResponse(config);

        assertTrue(containsToolNamed(tools, "execute_chat_commands"));
    }

    @Test
    public void screenshotToolIsExposedByDefault() {
        JsonArray tools = MCPProtocol.getToolsListResponse(new MCPConfig());

        assertTrue(containsToolNamed(tools, "take_screenshot"));
    }

    @Test
    public void guiToolsAreHiddenByDefault() {
        JsonArray tools = MCPProtocol.getToolsListResponse(new MCPConfig());

        assertFalse(containsToolNamed(tools, "get_current_screen"));
        assertFalse(containsToolNamed(tools, "click_screen_slot"));
        assertFalse(containsToolNamed(tools, "click_screen_button"));
        assertFalse(containsToolNamed(tools, "click_screen_entry"));
        assertFalse(containsToolNamed(tools, "click_screen_xy"));
        assertFalse(containsToolNamed(tools, "wait_for_screen"));
        assertFalse(containsToolNamed(tools, "close_current_screen"));
    }

    @Test
    public void guiToolsAreExposedWhenEnabled() {
        MCPConfig config = GSON.fromJson("""
            {
              "server": {
                "enableGuiAutomationTools": true
              }
            }
            """, MCPConfig.class);

        JsonArray tools = MCPProtocol.getToolsListResponse(config);

        assertTrue(containsToolNamed(tools, "get_current_screen"));
        assertTrue(containsToolNamed(tools, "click_screen_slot"));
        assertTrue(containsToolNamed(tools, "click_screen_button"));
        assertTrue(containsToolNamed(tools, "click_screen_entry"));
        assertTrue(containsToolNamed(tools, "click_screen_xy"));
        assertTrue(containsToolNamed(tools, "wait_for_screen"));
        assertTrue(containsToolNamed(tools, "close_current_screen"));
    }

    private static boolean containsToolNamed(JsonArray tools, String toolName) {
        for (int i = 0; i < tools.size(); i++) {
            JsonObject tool = tools.get(i).getAsJsonObject();
            if (toolName.equals(tool.get("name").getAsString())) {
                return true;
            }
        }
        return false;
    }
}
