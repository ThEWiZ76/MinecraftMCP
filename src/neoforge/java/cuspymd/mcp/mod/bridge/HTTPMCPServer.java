package cuspymd.mcp.mod.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import cuspymd.mcp.mod.command.CommandExecutor;
import cuspymd.mcp.mod.config.MCPConfig;
import cuspymd.mcp.mod.server.MCPProtocol;
import cuspymd.mcp.mod.utils.BlockScanner;
import cuspymd.mcp.mod.utils.ClientInputUtils;
import cuspymd.mcp.mod.utils.PlayerInfoProvider;
import cuspymd.mcp.mod.utils.ScreenAutomationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class HTTPMCPServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HTTPMCPServer.class);
    private static final Gson GSON = new Gson();

    private final MCPConfig config;
    private final CommandExecutor commandExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private HttpServer httpServer;
    private ExecutorService executor;

    public HTTPMCPServer(MCPConfig config) {
        this.config = config;
        this.commandExecutor = new CommandExecutor(config);
    }

    public void start() throws IOException {
        if (running.get()) return;
        running.set(true);
        InetSocketAddress address = new InetSocketAddress(config.getServer().getHost(), config.getServer().getPort());
        httpServer = HttpServer.create(address, 0);
        httpServer.createContext("/mcp", new MCPHandler());
        executor = Executors.newCachedThreadPool();
        httpServer.setExecutor(executor);
        httpServer.start();
        LOGGER.info("NeoForge HTTP MCP Server started on http://{}:{}/mcp", config.getServer().getHost(), config.getServer().getPort());
    }

    public void stop() {
        if (!running.get()) return;
        running.set(false);
        if (httpServer != null) httpServer.stop(0);
        if (executor != null) executor.shutdown();
    }

    private JsonObject handleMCPRequest(JsonObject request) {
        String method = request.get("method").getAsString();
        JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
        Integer requestId = request.has("id") ? request.get("id").getAsInt() : null;
        if (method.startsWith("notifications/")) return null;

        JsonObject result = switch (method) {
            case "initialize" -> handleInitialize(params);
            case "ping" -> handlePing();
            case "tools/list" -> handleToolsList();
            case "tools/call" -> handleToolsCall(params);
            default -> {
                JsonObject error = createErrorResponse("Unknown method: " + method, requestId);
                yield error;
            }
        };

        if (result.has("jsonrpc")) return result;
        return createSuccessResponse(result, requestId);
    }

    private JsonObject handleInitialize(JsonObject params) {
        JsonObject response = new JsonObject();
        response.addProperty("protocolVersion", params.has("protocolVersion") && "2025-03-26".equals(params.get("protocolVersion").getAsString()) ? "2025-03-26" : "2025-06-18");
        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        response.add("capabilities", capabilities);
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "minecraft-mcp-http-neoforge");
        serverInfo.addProperty("version", "1.0.0");
        response.add("serverInfo", serverInfo);
        return response;
    }

    private JsonObject handlePing() {
        JsonObject response = new JsonObject();
        response.addProperty("status", "pong");
        return response;
    }

    private JsonObject handleToolsList() {
        JsonObject response = new JsonObject();
        response.add("tools", MCPProtocol.getToolsListResponse(config));
        return response;
    }

    private JsonObject handleToolsCall(JsonObject params) {
        try {
            String toolName = params.get("name").getAsString();
            JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments")
                : new JsonObject();

            return switch (toolName) {
                case "execute_commands" -> commandExecutor.executeCommands(arguments);
                case "execute_chat_commands" -> commandExecutor.executeChatCommands(arguments);
                case "get_player_info" -> PlayerInfoProvider.getPlayerInfoResponse();
                case "get_blocks_in_area" -> BlockScanner.getBlocksInArea(arguments, config.getServer().getMaxAreaSize());
                case "get_current_screen" -> requireGuiTools() ? ScreenAutomationUtils.inspectCurrentScreen() : guiDisabled();
                case "click_screen_slot" -> requireGuiTools() ? ScreenAutomationUtils.clickScreenSlot(arguments) : guiDisabled();
                case "click_screen_button" -> requireGuiTools() ? ScreenAutomationUtils.clickScreenButton(arguments) : guiDisabled();
                case "click_screen_entry" -> requireGuiTools() ? ScreenAutomationUtils.clickScreenEntry(arguments) : guiDisabled();
                case "click_screen_xy" -> requireGuiTools() ? ScreenAutomationUtils.clickScreenXy(arguments) : guiDisabled();
                case "wait_for_screen" -> requireGuiTools() ? ScreenAutomationUtils.waitForScreen(arguments) : guiDisabled();
                case "close_current_screen" -> requireGuiTools() ? ScreenAutomationUtils.closeCurrentScreen() : guiDisabled();
                case "right_click_block" -> ClientInputUtils.rightClickBlock(arguments);
                case "right_click_item" -> ClientInputUtils.rightClickItem(arguments);
                case "set_held_slot" -> ClientInputUtils.setHeldSlot(arguments);
                case "movement_input" -> ClientInputUtils.movementInput(arguments);
                case "sneak" -> ClientInputUtils.sneak(arguments);
                case "wait_for_chat" -> ClientInputUtils.waitForChat(arguments);
                default -> MCPProtocol.createErrorResponse("Unknown tool: " + toolName, null);
            };
        } catch (Exception e) {
            LOGGER.error("Error handling tools/call", e);
            return MCPProtocol.createErrorResponse("Internal server error: " + e.getMessage(), null);
        }
    }

    private boolean requireGuiTools() {
        return config.getServer().isEnableGuiAutomationTools();
    }

    private JsonObject guiDisabled() {
        return MCPProtocol.createErrorResponse("GUI automation tools are disabled in config", null);
    }

    private JsonObject createSuccessResponse(JsonObject result, Integer requestId) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (requestId != null) response.addProperty("id", requestId);
        response.add("result", result);
        return response;
    }

    private JsonObject createErrorResponse(String message, Integer requestId) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (requestId != null) response.addProperty("id", requestId);
        JsonObject error = new JsonObject();
        error.addProperty("code", -32603);
        error.addProperty("message", message);
        response.add("error", error);
        return response;
    }

    private class MCPHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                if ("POST".equals(method)) {
                    String requestBody = readRequestBody(exchange);
                    String accept = exchange.getRequestHeaders().getFirst("Accept");
                    if (accept == null || (!accept.contains("application/json") && !accept.contains("text/event-stream"))) {
                        sendJsonResponse(exchange, 400, createErrorResponse("Invalid Accept header", null));
                        return;
                    }
                    JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
                    JsonObject response = handleMCPRequest(request);
                    sendJsonResponse(exchange, response == null ? 202 : 200, response == null ? new JsonObject() : response);
                } else if ("GET".equals(method)) {
                    sendJsonResponse(exchange, 405, createErrorResponse("Server-Sent Events not implemented", null));
                } else {
                    sendJsonResponse(exchange, 405, createErrorResponse("Method not allowed", null));
                }
            } catch (Exception e) {
                LOGGER.error("Error handling MCP request", e);
                sendJsonResponse(exchange, 500, createErrorResponse("Internal server error", null));
            }
        }

        private String readRequestBody(HttpExchange exchange) throws IOException {
            try (InputStream inputStream = exchange.getRequestBody();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                return body.toString();
            }
        }

        private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Accept, Origin");
            byte[] responseBytes = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        }
    }
}
