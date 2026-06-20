package cuspymd.mcp.mod;

import cuspymd.mcp.mod.bridge.HTTPMCPServer;
import cuspymd.mcp.mod.config.MCPConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(MCPServerModNeoForge.MOD_ID)
public final class MCPServerModNeoForge {
    public static final String MOD_ID = "mcp_server_mod";
    private static final Logger LOGGER = LoggerFactory.getLogger(MCPServerModNeoForge.class);
    private static HTTPMCPServer httpServer;

    public MCPServerModNeoForge() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            LOGGER.info("MCP Server Mod NeoForge loaded on non-client dist; HTTP MCP client server will not start");
            return;
        }

        NeoForge.EVENT_BUS.addListener(NeoForgeClientHooks::onClientTick);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientHooks::onClientChat);

        MCPConfig config = MCPConfig.load();
        if (!config.getServer().isAutoStart()) {
            LOGGER.info("MCP HTTP server auto-start disabled");
            return;
        }

        try {
            httpServer = new HTTPMCPServer(config);
            httpServer.start();
        } catch (Exception e) {
            LOGGER.error("Failed to start NeoForge MCP HTTP server", e);
        }
    }

    public static void stopServer() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }
}
