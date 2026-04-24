package cuspymd.mcp.mod.command;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cuspymd.mcp.mod.config.MCPConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnsafeChatCommandExecutorTest {
    private static final Gson GSON = new Gson();

    @Test
    public void unsafeChatCommandsReturnErrorWhenFeatureIsDisabled() {
        MCPConfig config = GSON.fromJson("""
            {
              "server": {
                "enableUnsafeChatCommands": false
              }
            }
            """, MCPConfig.class);
        CommandExecutor executor = new CommandExecutor(config);

        JsonObject arguments = new JsonObject();
        JsonArray commands = new JsonArray();
        commands.add("/customgear ammo");
        arguments.add("commands", commands);

        JsonObject response = executor.executeChatCommands(arguments);

        assertTrue(response.get("isError").getAsBoolean());
        assertTrue(response.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString()
            .contains("disabled"));
    }

    @Test
    public void unsafeChatCommandsRejectBlankCommandsBeforeClientAccess() {
        MCPConfig config = GSON.fromJson("""
            {
              "server": {
                "enableUnsafeChatCommands": true
              }
            }
            """, MCPConfig.class);
        CommandExecutor executor = new CommandExecutor(config);

        JsonObject arguments = new JsonObject();
        JsonArray commands = new JsonArray();
        commands.add("   ");
        arguments.add("commands", commands);

        JsonObject response = executor.executeChatCommands(arguments);

        assertTrue(response.get("isError").getAsBoolean());
        assertEquals(
            "Command at index 0 is blank after normalization",
            response.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString()
        );
    }
}
