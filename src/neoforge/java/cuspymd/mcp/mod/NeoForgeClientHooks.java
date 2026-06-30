package cuspymd.mcp.mod;

import cuspymd.mcp.mod.command.ChatMessageCapture;
import cuspymd.mcp.mod.utils.ClientInputUtils;
import cuspymd.mcp.mod.utils.ScreenshotUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class NeoForgeClientHooks {
    private NeoForgeClientHooks() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ClientInputUtils.onClientTick(client);
        ScreenshotUtils.onClientTick(client);
    }

    public static void onClientChat(ClientChatReceivedEvent event) {
        ChatMessageCapture.getInstance().captureMessage(
            event.getMessage().getString(),
            ChatMessageCapture.MessageSource.SYSTEM
        );
    }
}
