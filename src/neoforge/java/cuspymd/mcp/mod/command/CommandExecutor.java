package cuspymd.mcp.mod.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import cuspymd.mcp.mod.config.MCPConfig;
import cuspymd.mcp.mod.server.MCPProtocol;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CommandExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandExecutor.class);
    private final MCPConfig config;
    private final SafetyValidator safetyValidator;

    public CommandExecutor(MCPConfig config) {
        this.config = config;
        this.safetyValidator = new SafetyValidator(config);
    }

    public JsonObject executeCommands(JsonObject arguments) {
        try {
            JsonArray commandsArray = arguments.getAsJsonArray("commands");
            boolean validateSafety = !arguments.has("validate_safety") || arguments.get("validate_safety").getAsBoolean();
            List<String> commands = readCommands(commandsArray);
            if (validateSafety) {
                for (int i = 0; i < commands.size(); i++) {
                    SafetyValidator.ValidationResult validation = safetyValidator.validate(commands.get(i));
                    if (!validation.isValid()) return response(commands, i, "rejected_by_safety", validation.getErrorMessage());
                }
            }
            return executeSequentially(commands);
        } catch (Exception e) {
            LOGGER.error("Error executing commands", e);
            return MCPProtocol.createErrorResponse("Internal error: " + e.getMessage(), null);
        }
    }

    public JsonObject executeChatCommands(JsonObject arguments) {
        if (!config.getServer().isEnableUnsafeChatCommands()) {
            return MCPProtocol.createErrorResponse("Unsafe chat command tool is disabled in config", null);
        }
        try {
            return executeSequentially(readCommands(arguments.getAsJsonArray("commands")));
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Internal error: " + e.getMessage(), null);
        }
    }

    private List<String> readCommands(JsonArray array) {
        List<String> commands = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String command = normalizeCommand(array.get(i).getAsString());
            if (command.isBlank()) throw new IllegalArgumentException("Command at index " + i + " is blank");
            commands.add(command);
        }
        return commands;
    }

    private JsonObject executeSequentially(List<String> commands) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return MCPProtocol.createErrorResponse("Player or world is not available", null);
        }

        JsonArray results = new JsonArray();
        int accepted = 0;
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            long start = System.currentTimeMillis();
            boolean sent = sendCommand(client, command);
            if (sent) accepted++;
            JsonObject result = new JsonObject();
            result.addProperty("index", i);
            result.addProperty("command", command);
            result.addProperty("status", sent ? "unknown" : "execution_error");
            result.addProperty("accepted", sent);
            result.add("applied", JsonNull.INSTANCE);
            result.addProperty("executionTimeMs", System.currentTimeMillis() - start);
            result.addProperty("summary", sent ? "Command sent" : "Command failed before send");
            result.add("chatMessages", new JsonArray());
            results.add(result);
        }

        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("totalCommands", commands.size());
        responseJson.addProperty("acceptedCount", accepted);
        responseJson.addProperty("appliedCount", 0);
        responseJson.addProperty("failedCount", commands.size() - accepted);
        responseJson.add("results", results);
        responseJson.add("chatMessages", new JsonArray());
        responseJson.addProperty("hint", "Use get_blocks_in_area to verify world changes.");
        return MCPProtocol.createSuccessResponse(responseJson.toString());
    }

    private boolean sendCommand(Minecraft client, String command) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                if (client.player == null || client.player.connection == null) {
                    future.complete(false);
                    return;
                }
                client.player.connection.sendCommand(normalizeCommand(command));
                future.complete(true);
            } catch (Exception e) {
                LOGGER.error("Failed to send command {}", command, e);
                future.complete(false);
            }
        });
        try {
            return future.get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private JsonObject response(List<String> commands, int failedIndex, String status, String summary) {
        JsonArray results = new JsonArray();
        for (int i = 0; i < commands.size(); i++) {
            JsonObject result = new JsonObject();
            result.addProperty("index", i);
            result.addProperty("command", commands.get(i));
            result.addProperty("status", status);
            result.addProperty("accepted", false);
            result.addProperty("applied", false);
            result.addProperty("executionTimeMs", 0);
            result.addProperty("summary", i == failedIndex ? summary : "Skipped because validation failed at command " + (failedIndex + 1));
            result.add("chatMessages", new JsonArray());
            results.add(result);
        }
        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("totalCommands", commands.size());
        responseJson.addProperty("acceptedCount", 0);
        responseJson.addProperty("appliedCount", 0);
        responseJson.addProperty("failedCount", commands.size());
        responseJson.add("results", results);
        responseJson.add("chatMessages", new JsonArray());
        return MCPProtocol.createSuccessResponse(responseJson.toString());
    }

    private static String normalizeCommand(String command) {
        if (command == null) return "";
        String normalized = command.trim();
        return normalized.startsWith("/") ? normalized.substring(1).trim() : normalized;
    }
}
