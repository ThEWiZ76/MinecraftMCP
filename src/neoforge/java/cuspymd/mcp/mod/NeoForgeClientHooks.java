package cuspymd.mcp.mod;

import cuspymd.mcp.mod.command.ChatMessageCapture;
import cuspymd.mcp.mod.utils.ClientInputUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class NeoForgeClientHooks {
    private NeoForgeClientHooks() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        ClientInputUtils.onClientTick(Minecraft.getInstance());
    }

    public static void onClientChat(ClientChatReceivedEvent event) {
        ChatMessageCapture.getInstance().captureMessage(
            event.getMessage().getString(),
            ChatMessageCapture.MessageSource.SYSTEM
        );
    }
}
