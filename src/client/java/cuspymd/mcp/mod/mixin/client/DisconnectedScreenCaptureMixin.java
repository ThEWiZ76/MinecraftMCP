package cuspymd.mcp.mod.mixin.client;

import cuspymd.mcp.mod.utils.ClientDisconnectState;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenCaptureMixin {
    @Inject(
        method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/text/Text;Lnet/minecraft/network/DisconnectionInfo;)V",
        at = @At("RETURN")
    )
    private void onInit(Screen parent, Text title, DisconnectionInfo info, CallbackInfo ci) {
        ClientDisconnectState.record(
            title == null ? "" : title.getString(),
            info == null || info.reason() == null ? "" : info.reason().getString(),
            null
        );
    }

    @Inject(
        method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/text/Text;Lnet/minecraft/network/DisconnectionInfo;Lnet/minecraft/text/Text;)V",
        at = @At("RETURN")
    )
    private void onInitWithButton(Screen parent, Text title, DisconnectionInfo info, Text buttonLabel, CallbackInfo ci) {
        onInit(parent, title, info, ci);
    }

    @Inject(
        method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/text/Text;Lnet/minecraft/text/Text;)V",
        at = @At("RETURN")
    )
    private void onInitLegacy(Screen parent, Text title, Text reason, CallbackInfo ci) {
        ClientDisconnectState.record(
            title == null ? "" : title.getString(),
            reason == null ? "" : reason.getString(),
            null
        );
    }

    @Inject(
        method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/text/Text;Lnet/minecraft/text/Text;Lnet/minecraft/text/Text;)V",
        at = @At("RETURN")
    )
    private void onInitLegacyWithButton(Screen parent, Text title, Text reason, Text buttonLabel, CallbackInfo ci) {
        onInitLegacy(parent, title, reason, ci);
    }
}
