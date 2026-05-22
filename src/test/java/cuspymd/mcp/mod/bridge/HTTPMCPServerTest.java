package cuspymd.mcp.mod.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cuspymd.mcp.mod.config.MCPConfig;
import cuspymd.mcp.mod.server.MCPProtocol;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

public class HTTPMCPServerTest {

    @Test
    public void testAwaitScreenshotResult_Success() {
        HTTPMCPServer server = new HTTPMCPServer(new MCPConfig());
        CompletableFuture<String> future = CompletableFuture.completedFuture("abc123");

        JsonObject response = server.awaitScreenshotResult(future);

        assertFalse(response.get("isError").getAsBoolean());
        JsonObject firstContent = response.getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("image", firstContent.get("type").getAsString());
        assertEquals("abc123", firstContent.get("data").getAsString());
        assertEquals("image/png", firstContent.get("mimeType").getAsString());
    }

    @Test
    public void testAwaitScreenshotResult_Timeout() {
        HTTPMCPServer server = new HTTPMCPServer(new MCPConfig());
        TimeoutFuture future = new TimeoutFuture();

        JsonObject response = server.awaitScreenshotResult(future);

        assertTrue(response.get("isError").getAsBoolean());
        assertTrue(extractText(response).contains("timed out"));
        assertTrue(future.cancelCalled, "Future should be cancelled on timeout");
    }

    @Test
    public void testAwaitScreenshotResult_Interrupted() {
        HTTPMCPServer server = new HTTPMCPServer(new MCPConfig());
        InterruptedFuture future = new InterruptedFuture();

        try {
            JsonObject response = server.awaitScreenshotResult(future);
            assertTrue(response.get("isError").getAsBoolean());
            assertTrue(extractText(response).contains("interrupted"));
            assertTrue(future.cancelCalled, "Future should be cancelled on interruption");
            assertTrue(Thread.currentThread().isInterrupted(), "Interrupted flag should be preserved");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void testAwaitScreenshotResult_ExecutionFailure() {
        HTTPMCPServer server = new HTTPMCPServer(new MCPConfig());
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("capture failed"));

        JsonObject response = server.awaitScreenshotResult(future);

        assertTrue(response.get("isError").getAsBoolean());
        assertTrue(extractText(response).contains("capture failed"));
    }

    @Test
    public void testHandleMCPRequest_ToolsCallTakeScreenshotSuccess() throws Exception {
        TestableHTTPMCPServer server = new TestableHTTPMCPServer(new MCPConfig());
        server.nextFuture = CompletableFuture.completedFuture("img-data");

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", 7);
        request.addProperty("method", "tools/call");
        JsonObject params = new JsonObject();
        params.addProperty("name", "take_screenshot");
        params.add("arguments", new JsonObject());
        request.add("params", params);

        JsonObject response = invokeHandleMCPRequest(server, request);

        assertEquals("2.0", response.get("jsonrpc").getAsString());
        assertEquals(7, response.get("id").getAsInt());
        JsonObject result = response.getAsJsonObject("result");
        assertFalse(result.get("isError").getAsBoolean());
        JsonObject firstContent = result.getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("image", firstContent.get("type").getAsString());
        assertEquals("img-data", firstContent.get("data").getAsString());
    }

    @Test
    public void testHandleMCPRequest_ToolsCallTakeScreenshotTimeout() throws Exception {
        TestableHTTPMCPServer server = new TestableHTTPMCPServer(new MCPConfig());
        server.nextFuture = new TimeoutFuture();

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", 8);
        request.addProperty("method", "tools/call");
        JsonObject params = new JsonObject();
        params.addProperty("name", "take_screenshot");
        params.add("arguments", new JsonObject());
        request.add("params", params);

        JsonObject response = invokeHandleMCPRequest(server, request);

        JsonObject result = response.getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean());
        assertTrue(extractText(result).contains("timed out"));
    }

    @Test
    public void testHandleMCPRequest_ToolsCallTakeScreenshotWithoutArguments() throws Exception {
        TestableHTTPMCPServer server = new TestableHTTPMCPServer(new MCPConfig());
        server.nextFuture = CompletableFuture.completedFuture("no-args-image");

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", 9);
        request.addProperty("method", "tools/call");
        JsonObject params = new JsonObject();
        params.addProperty("name", "take_screenshot");
        request.add("params", params);

        JsonObject response = invokeHandleMCPRequest(server, request);

        JsonObject result = response.getAsJsonObject("result");
        assertFalse(result.get("isError").getAsBoolean());
        JsonObject firstContent = result.getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("no-args-image", firstContent.get("data").getAsString());
    }

    @Test
    public void testHandleMCPRequest_ToolsCallGetCurrentScreen() throws Exception {
        TestableHTTPMCPServer server = new TestableHTTPMCPServer(guiEnabledConfig());
        server.screenResponse = new JsonObject();
        server.screenResponse.addProperty("title", "Weapon Assembly");

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", 10);
        request.addProperty("method", "tools/call");
        JsonObject params = new JsonObject();
        params.addProperty("name", "get_current_screen");
        params.add("arguments", new JsonObject());
        request.add("params", params);

        JsonObject response = invokeHandleMCPRequest(server, request);

        JsonObject result = response.getAsJsonObject("result");
        assertFalse(result.get("isError").getAsBoolean());
        assertTrue(extractText(result).contains("\"title\":\"Weapon Assembly\""));
    }

    @Test
    public void testHandleMCPRequest_ToolsCallClickScreenSlot() throws Exception {
        TestableHTTPMCPServer server = new TestableHTTPMCPServer(guiEnabledConfig());
        server.clickResponse = new JsonObject();
        server.clickResponse.addProperty("clicked", true);

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", 11);
        request.addProperty("method", "tools/call");
        JsonObject params = new JsonObject();
        params.addProperty("name", "click_screen_slot");
        JsonObject arguments = new JsonObject();
        arguments.addProperty("slot", 13);
        params.add("arguments", arguments);
        request.add("params", params);

        JsonObject response = invokeHandleMCPRequest(server, request);

        JsonObject result = response.getAsJsonObject("result");
        assertFalse(result.get("isError").getAsBoolean());
        assertTrue(extractText(result).contains("\"clicked\":true"));
    }

    @Test
    public void testHandleMCPRequest_ToolsCallCloseCurrentScreen() throws Exception {
        TestableHTTPMCPServer server = new TestableHTTPMCPServer(guiEnabledConfig());
        server.closeResponse = new JsonObject();
        server.closeResponse.addProperty("closed", true);

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", 12);
        request.addProperty("method", "tools/call");
        JsonObject params = new JsonObject();
        params.addProperty("name", "close_current_screen");
        params.add("arguments", new JsonObject());
        request.add("params", params);

        JsonObject response = invokeHandleMCPRequest(server, request);

        JsonObject result = response.getAsJsonObject("result");
        assertFalse(result.get("isError").getAsBoolean());
        assertTrue(extractText(result).contains("\"closed\":true"));
    }

    @Test
    public void testHandleMCPRequest_ToolsCallAttackBlock() throws Exception {
        TestableHTTPMCPServer server = new TestableHTTPMCPServer(new MCPConfig());
        server.clientInteractionResponse = new JsonObject();
        server.clientInteractionResponse.addProperty("tool", "attack_block");

        JsonObject response = invokeTool(server, "attack_block", new JsonObject(), 13);

        JsonObject result = response.getAsJsonObject("result");
        assertFalse(result.get("isError").getAsBoolean());
        assertTrue(extractText(result).contains("\"tool\":\"attack_block\""));
    }

    @Test
    public void testHandleMCPRequest_ToolsCallWaitForChat() throws Exception {
        TestableHTTPMCPServer server = new TestableHTTPMCPServer(new MCPConfig());
        server.clientInteractionResponse = new JsonObject();
        server.clientInteractionResponse.addProperty("matched", true);

        JsonObject response = invokeTool(server, "wait_for_chat", new JsonObject(), 14);

        JsonObject result = response.getAsJsonObject("result");
        assertFalse(result.get("isError").getAsBoolean());
        assertTrue(extractText(result).contains("\"matched\":true"));
    }

    private JsonObject invokeHandleMCPRequest(HTTPMCPServer server, JsonObject request) throws Exception {
        Method method = HTTPMCPServer.class.getDeclaredMethod("handleMCPRequest", JsonObject.class);
        method.setAccessible(true);
        return (JsonObject) method.invoke(server, request);
    }

    private JsonObject invokeTool(HTTPMCPServer server, String toolName, JsonObject arguments, int id) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", id);
        request.addProperty("method", "tools/call");
        JsonObject params = new JsonObject();
        params.addProperty("name", toolName);
        params.add("arguments", arguments);
        request.add("params", params);
        return invokeHandleMCPRequest(server, request);
    }

    private MCPConfig guiEnabledConfig() {
        return new Gson().fromJson("""
            {
              "server": {
                "enableGuiAutomationTools": true
              }
            }
            """, MCPConfig.class);
    }

    private String extractText(JsonObject response) {
        JsonArray content = response.getAsJsonArray("content");
        return content.get(0).getAsJsonObject().get("text").getAsString();
    }

    private static class TimeoutFuture extends CompletableFuture<String> {
        boolean cancelCalled = false;

        @Override
        public String get(long timeout, TimeUnit unit) throws TimeoutException {
            throw new TimeoutException("forced timeout");
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelCalled = true;
            return super.cancel(mayInterruptIfRunning);
        }
    }

    private static class InterruptedFuture extends CompletableFuture<String> {
        boolean cancelCalled = false;

        @Override
        public String get(long timeout, TimeUnit unit) throws InterruptedException {
            throw new InterruptedException("forced interrupt");
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelCalled = true;
            return super.cancel(mayInterruptIfRunning);
        }
    }

    private static class TestableHTTPMCPServer extends HTTPMCPServer {
        CompletableFuture<String> nextFuture;
        JsonObject screenResponse;
        JsonObject clickResponse;
        JsonObject closeResponse;
        JsonObject clientInteractionResponse;

        TestableHTTPMCPServer(MCPConfig config) {
            super(config);
        }

        @Override
        CompletableFuture<String> takeScreenshotAsync(JsonObject params) {
            return nextFuture;
        }

        @Override
        JsonObject getCurrentScreenInfo(JsonObject arguments) {
            return MCPProtocol.createSuccessResponse(screenResponse.toString());
        }

        @Override
        JsonObject clickScreenSlot(JsonObject arguments) {
            return MCPProtocol.createSuccessResponse(clickResponse.toString());
        }

        @Override
        JsonObject closeCurrentScreen(JsonObject arguments) {
            return MCPProtocol.createSuccessResponse(closeResponse.toString());
        }

        @Override
        JsonObject handleClientInteractionTool(String toolName, JsonObject arguments) {
            return MCPProtocol.createSuccessResponse(clientInteractionResponse.toString());
        }
    }
}
