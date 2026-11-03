package com.photonpulse.candy.mixin.client;

import com.photonpulse.candy.client.CandyMixinConfig;
import com.photonpulse.candy.client.IntensityState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
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
 *
 * Hedef metot sürüme bağlı (javap ile ölçüldü):
 *   1.21.x  setup(BlockGetter, Entity, boolean, boolean, float)
 *   26.x    update(DeltaTracker)
 * 26.x'te setup kalmadı; kamerayı kare başına kuran metot update(DeltaTracker) ve
 * gövdesinde alignWithEntity + setupPerspective çağrılıyor — yani aynı noktadaki aynı
 * iş. @Shadow'lanan xRot/yRot alanları ve protected setRotation(float, float) 26.3'te
 * de aynen var. İmza değiştiği için enjeksiyon metodu ikiye ayrıldı, sallama hesabı
 * ise tek yerde (candy$shakeUygula).
 */
@Mixin(Camera.class)
public class CameraMixin {

    @Shadow private float yRot;
    @Shadow private float xRot;
    @Shadow protected void setRotation(float yaw, float pitch) {}

    //? if >=26 {
    /*@Inject(method = "update", at = @At("TAIL"))
    private void candy$shake(DeltaTracker tickCounter, CallbackInfo ci) {
        candy$shakeUygula();
    }
    */
    //?}
    //? if <26 {
    @Inject(method = "setup", at = @At("TAIL"))
    private void candy$shake(BlockGetter area, Entity focused, boolean thirdPerson,
                             boolean inverseView, float tickDelta, CallbackInfo ci) {
        candy$shakeUygula();
    }
    //?}

    /** Sallama hesabı — iki sürümün enjeksiyon metodu da buraya giriyor. */
    private void candy$shakeUygula() {
        try {
            if (!CandyMixinConfig.cameraShake) return;
            double intensity = IntensityState.get();
            if (intensity < 0.5) return;

            float siddet = (float) ((intensity - 0.5) * 2.0); // 0-1
            float t = (System.currentTimeMillis() % 100000L) / 1000f;
            float dYaw = (float) (Math.sin(t * 6.3) * siddet * 0.6);
            float dPitch = (float) (Math.cos(t * 7.7) * siddet * 0.4);

            setRotation(this.yRot + dYaw, this.xRot + dPitch);
        } catch (Throwable ignored) {}
    }
}
