package com.photonpulse.candy.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FogRendererMixin (v3.0 Faz 1.2) — Mirror/Anti-Mirror özel fog rengi.
 *
 * setupFog() Return sonrasi oyuncunun dunyasina gore renk override edilir:
 *   - Mirror: koyu kirmizimsi-kahverengi (Silent Hill "otherworld" hissi)
 *   - Anti-Mirror: koyu mor (uzay boslugu / kayip hissi)
 *
 * Ad UC SURUMDE DE setupFog; degisen sey imza (javap ile uc jar'da olculdu):
 *   1.21.7   Vector4f setupFog(Camera, int, boolean, DeltaTracker, float, ClientLevel)
 *   1.21.11  Vector4f setupFog(Camera, int,          DeltaTracker, float, ClientLevel)
 *   26.x     FogData  setupFog(Camera, int,          DeltaTracker, float, ClientLevel)
 * 1.21.9'da `boolean thickFog` dustu; 26.x'te ayrica donus FogData oldu ve renk artik
 * FogData.color (public Vector4f). Davranis ucunde de AYNI -- ozellik kaybolmuyor.
 *
 * DUZELTME: burada `>=1.21.9 && <26` icin bir `setupFog -> applyFog` ad kurali vardi.
 * 1.21.11 jar'inda applyFog diye bir metot YOK; kural dogru adi yanlisa ceviriyordu ve
 * `defaultRequire = 0` yuzunden enjeksiyon SESSIZCE oluyordu -- yani ayna sisi 1.21.11'de
 * hic calismamis. Kural kalkti, imza farki buradaki ucuncu dal ile cozuldu.
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    //? if >=26 {
    /*@Inject(method = "setupFog", at = @At("RETURN"))
    private void candy$mirrorFog(
        Camera camera, int viewDistance,
        DeltaTracker tickCounter, float skyDarkness, ClientLevel world,
        CallbackInfoReturnable<FogData> cir
    ) {
        try {
            if (world == null) return;
            String dimId = world.dimension().location().toString();
            FogData veri = cir.getReturnValue();
            if (veri == null || veri.color == null) return;
            Vector4f col = veri.color;

            if (dimId.equals("candy:mirror")) {
                // Koyu kızıl-kahverengi — Silent Hill Otherworld
                col.set(0.25f, 0.06f, 0.06f, col.w);
            } else if (dimId.equals("candy:anti_mirror")) {
                // Koyu mor-siyah — sonsuzluk
                col.set(0.12f, 0.04f, 0.18f, col.w);
            }
        } catch (Throwable ignored) {}
    }
    */
    //?}
    //? if >=1.21.9 && <26 {
    /*@Inject(method = "setupFog", at = @At("RETURN"))
    private void candy$mirrorFog(
        Camera camera, int viewDistance,
        DeltaTracker tickCounter, float skyDarkness, ClientLevel world,
        CallbackInfoReturnable<Vector4f> cir
    ) {
        try {
            if (world == null) return;
            String dimId = world.dimension().location().toString();
            Vector4f col = cir.getReturnValue();
            if (col == null) return;

            if (dimId.equals("candy:mirror")) {
                // Koyu kizil-kahverengi -- Silent Hill Otherworld
                col.set(0.25f, 0.06f, 0.06f, col.w);
            } else if (dimId.equals("candy:anti_mirror")) {
                // Koyu mor-siyah -- sonsuzluk
                col.set(0.12f, 0.04f, 0.18f, col.w);
            }
        } catch (Throwable ignored) {}
    }
    */
    //?}
    //? if <1.21.9 {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void candy$mirrorFog(
        Camera camera, int viewDistance, boolean thickFog,
        DeltaTracker tickCounter, float skyDarkness, ClientLevel world,
        CallbackInfoReturnable<Vector4f> cir
    ) {
        try {
            if (world == null) return;
            String dimId = world.dimension().location().toString();
            Vector4f col = cir.getReturnValue();
            if (col == null) return;

            if (dimId.equals("candy:mirror")) {
                // Koyu kızıl-kahverengi — Silent Hill Otherworld
                col.set(0.25f, 0.06f, 0.06f, col.w);
            } else if (dimId.equals("candy:anti_mirror")) {
                // Koyu mor-siyah — sonsuzluk
                col.set(0.12f, 0.04f, 0.18f, col.w);
            }
        } catch (Throwable ignored) {}
    }
    //?}
}
