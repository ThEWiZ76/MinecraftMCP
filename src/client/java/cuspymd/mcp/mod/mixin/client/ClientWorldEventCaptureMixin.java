package cuspymd.mcp.mod.mixin.client;

import cuspymd.mcp.mod.utils.ClientEventRecorder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldEventCaptureMixin {
    @Inject(
        method = "playSoundClient(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZ)V",
        at = @At("HEAD")
    )
    private void onPlaySoundClient(double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch, boolean useDistance, CallbackInfo ci) {
        ClientEventRecorder.recordSound(x, y, z, sound, category, volume, pitch);
    }

    @Inject(
        method = "addParticleClient(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",
        at = @At("HEAD")
    )
    private void onAddParticleClient(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfo ci) {
        ClientEventRecorder.recordParticle(parameters, x, y, z, velocityX, velocityY, velocityZ);
    }

    @Inject(
        method = "addParticleClient(Lnet/minecraft/particle/ParticleEffect;ZZDDDDDD)V",
        at = @At("HEAD")
    )
    private void onAddParticleClientForced(ParticleEffect parameters, boolean force, boolean important, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfo ci) {
        ClientEventRecorder.recordParticle(parameters, x, y, z, velocityX, velocityY, velocityZ);
    }
}
