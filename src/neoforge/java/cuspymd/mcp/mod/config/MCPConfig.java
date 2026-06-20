package cuspymd.mcp.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MCPConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MCPConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ServerConfig server = new ServerConfig();
    private ClientConfig client = new ClientConfig();
    private SafetyConfig safety = new SafetyConfig();

    public static MCPConfig load() {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("mcp-client-neoforge.json");
        if (Files.exists(configFile)) {
            try {
                return GSON.fromJson(Files.readString(configFile), MCPConfig.class);
            } catch (IOException e) {
                LOGGER.warn("Failed to load NeoForge MCP config, using defaults", e);
            }
        }

        MCPConfig defaultConfig = new MCPConfig();
        defaultConfig.save();
        return defaultConfig;
    }

    public void save() {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("mcp-client-neoforge.json");
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.error("Failed to save NeoForge MCP config", e);
        }
    }

    public ServerConfig getServer() { return server; }
    public ClientConfig getClient() { return client; }
    public SafetyConfig getSafety() { return safety; }

    public static class ServerConfig {
        private String transport = "http";
        private int port = 8080;
        private String host = "localhost";
        private boolean enableSafety = true;
        private boolean enableUnsafeChatCommands = false;
        private boolean enableGuiAutomationTools = false;
        private int maxAreaSize = 10;
        private List<String> allowedCommands = List.of("fill", "clone", "setblock", "summon", "tp", "give", "gamemode", "effect", "enchant", "weather", "time", "say", "tell", "title");
        private int requestTimeoutMs = 30000;
        private boolean autoStart = true;

        public String getTransport() { return transport; }
        public int getPort() { return port; }
        public String getHost() { return host; }
        public boolean isEnableSafety() { return enableSafety; }
        public boolean isEnableUnsafeChatCommands() { return enableUnsafeChatCommands; }
        public boolean isEnableGuiAutomationTools() { return enableGuiAutomationTools; }
        public int getMaxAreaSize() { return maxAreaSize; }
        public List<String> getAllowedCommands() { return allowedCommands; }
        public int getRequestTimeoutMs() { return requestTimeoutMs; }
        public boolean isAutoStart() { return autoStart; }
    }

    public static class ClientConfig {
        private boolean showNotifications = true;
        private String logLevel = "INFO";
        private boolean logCommands = false;
        private boolean saveScreenshotsForDebug = false;

        public boolean isShowNotifications() { return showNotifications; }
        public String getLogLevel() { return logLevel; }
        public boolean isLogCommands() { return logCommands; }
        public boolean isSaveScreenshotsForDebug() { return saveScreenshotsForDebug; }
    }

    public static class SafetyConfig {
        private int maxEntitiesPerCommand = 10;
        private int maxBlocksPerCommand = 125000;
        private boolean blockCreativeForAll = true;
        private boolean requireOpForAdminCommands = true;

        public int getMaxEntitiesPerCommand() { return maxEntitiesPerCommand; }
        public int getMaxBlocksPerCommand() { return maxBlocksPerCommand; }
        public boolean isBlockCreativeForAll() { return blockCreativeForAll; }
        public boolean isRequireOpForAdminCommands() { return requireOpForAdminCommands; }
    }
}
