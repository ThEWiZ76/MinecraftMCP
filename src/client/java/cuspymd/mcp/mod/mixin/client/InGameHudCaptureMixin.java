package cuspymd.mcp.mod.mixin.client;

import cuspymd.mcp.mod.utils.ClientHudState;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudCaptureMixin {
    @Inject(method = "setOverlayMessage(Lnet/minecraft/text/Text;Z)V", at = @At("HEAD"))
    private void onSetOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
        ClientHudState.recordActionbar(message == null ? "" : message.getString());
    }

    @Inject(method = "setTitle(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onSetTitle(Text title, CallbackInfo ci) {
        ClientHudState.recordTitle(title == null ? "" : title.getString());
    }

    @Inject(method = "setSubtitle(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onSetSubtitle(Text subtitle, CallbackInfo ci) {
        ClientHudState.recordSubtitle(subtitle == null ? "" : subtitle.getString());
    }
}
