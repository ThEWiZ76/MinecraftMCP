package cuspymd.mcp.mod.mixin.client;

import cuspymd.mcp.mod.utils.ClientDisconnectState;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PacketListener.class)
public interface PacketListenerDisconnectCaptureMixin {
    @Inject(method = "createDisconnectionInfo", at = @At("HEAD"))
    private void onCreateDisconnectionInfo(Text reason, Throwable exception, CallbackInfoReturnable<DisconnectionInfo> cir) {
        ClientDisconnectState.record("", reason == null ? "" : reason.getString(), exception);
    }
}
