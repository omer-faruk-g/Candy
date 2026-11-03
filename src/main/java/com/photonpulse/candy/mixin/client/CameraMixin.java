package com.photonpulse.candy.mixin.client;

import com.photonpulse.candy.client.CandyMixinConfig;
import com.photonpulse.candy.client.IntensityState;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
// Camera.setup'in ilk parametresi 1.21.7'de BlockGetter, 1.21.11'de Level.
// Mixin descriptor'u birebir eslesmek zorunda; yanlis tip oyunu acilista
// InvalidInjectionException ile dusuruyor. defaultRequire:0 bunu YUTMUYOR:
// hedefi bulamamak sessiz gecer ama hatali descriptor sert hatadir.
//
// Bu loader farki DEGIL, surum farki: Fabric 1.21.11 ve NeoForge 1.21.11 ayni
// imzayi istiyor. Dolayisiyla Fabric 1.21.11 bu satir duzeltilene kadar
// acilista cokuyordu -- "Fabric yesil" yalnizca derleme yesiliydi, istemci hic
// calistirilmamisti.
//
// Esik olarak 1.21.11 yazildi cunku OLCULEN iki dugum 1.21.7 (BlockGetter) ve
// 1.21.11 (Level). Degisikligin 1.21.9 veya 1.21.10'da mi geldigi dogrulanmadi;
// o surumler icin dugum eklenirse bu esik yeniden olculmeli.
//? if <1.21.11 {
import net.minecraft.world.level.BlockGetter;
//?}
//? if >=1.21.11 {
/*import net.minecraft.world.level.Level;
*///?}
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

    @Shadow private float yRot;
    @Shadow private float xRot;
    @Shadow protected void setRotation(float yaw, float pitch) {}

    @Inject(method = "setup", at = @At("TAIL"))
    //? if <1.21.11 {
    private void candy$shake(BlockGetter area, Entity focused, boolean thirdPerson,
    //?}
    //? if >=1.21.11 {
    /*private void candy$shake(Level area, Entity focused, boolean thirdPerson,
    *///?}
                             boolean inverseView, float tickDelta, CallbackInfo ci) {
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
