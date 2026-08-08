package com.photonpulse.candy.mixin.client;

import com.photonpulse.candy.client.CandyMixinConfig;
import com.photonpulse.candy.client.IntensityState;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CameraMixin — intensity yüksekken kamera hafif sallanır (v1.5.0-mixin).
 *
 * Sodium uyumlu — Camera sınıfına Sodium dokunmuyor.
 * Sin dalgası kullanılır (rastgele değil) — smooth shake, epileptik değil.
 */
@Mixin(Camera.class)
public class CameraMixin {

    @Shadow private float yaw;
    @Shadow private float pitch;
    @Shadow protected void setRotation(float yaw, float pitch) {}

    @Inject(method = "update", at = @At("TAIL"))
    private void candy$shake(BlockView area, Entity focused, boolean thirdPerson,
                             boolean inverseView, float tickDelta, CallbackInfo ci) {
        try {
            if (!CandyMixinConfig.cameraShake) return;
            double intensity = IntensityState.get();
            if (intensity < 0.5) return;

            float siddet = (float) ((intensity - 0.5) * 2.0); // 0-1
            float t = (System.currentTimeMillis() % 100000L) / 1000f;
            float dYaw = (float) (Math.sin(t * 6.3) * siddet * 0.6);
            float dPitch = (float) (Math.cos(t * 7.7) * siddet * 0.4);

            setRotation(this.yaw + dYaw, this.pitch + dPitch);
        } catch (Throwable ignored) {}
    }
}
