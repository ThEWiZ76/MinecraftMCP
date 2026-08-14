package cuspymd.mcp.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cuspymd.mcp.mod.config.MCPConfig;
import cuspymd.mcp.mod.server.MCPProtocol;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

public class MCPProtocolTest {

    @Test
    public void testCreateImageResponse() {
        String base64Data = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        String mimeType = "image/png";

        JsonObject response = MCPProtocol.createImageResponse(base64Data, mimeType);

        assertFalse(response.get("isError").getAsBoolean());
        assertTrue(response.has("content"));

        JsonArray content = response.getAsJsonArray("content");
        assertEquals(1, content.size());

        JsonObject imageContent = content.get(0).getAsJsonObject();
        assertEquals("image", imageContent.get("type").getAsString());
        assertEquals(base64Data, imageContent.get("data").getAsString());
        assertEquals(mimeType, imageContent.get("mimeType").getAsString());
    }

    @Test
    public void toolsListIncludesClientInteractionTools() {
        JsonArray tools = MCPProtocol.getToolsListResponse(new MCPConfig());

        Set<String> toolNames = StreamSupport.stream(tools.spliterator(), false)
            .map(element -> element.getAsJsonObject().get("name").getAsString())
            .collect(Collectors.toSet());

        assertTrue(toolNames.containsAll(Set.of(
            "attack_block",
            "left_click_air",
            "right_click_block",
            "right_click_item",
            "set_held_slot",
            "movement_input",
            "sneak",
            "wait_for_chat",
            "get_scoreboard",
            "get_client_disconnect",
            "get_bossbar_actionbar_titles",
            "get_nearby_entities",
            "get_recent_sounds_particles",
            "keybind_input",
            "keyboard_input",
            "mouse_input",
            "look_input",
            "open_inventory"
        )));
    }
}
