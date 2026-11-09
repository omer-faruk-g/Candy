package com.photonpulse.candy.mixin.client;

import com.photonpulse.candy.client.KaranlikState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LightmapTextureManagerMixin (v3.0 Faz 1.2, v8.1 şifre karanlığı) — lightmap karartma.
 *
 * calculateDarknessScale(LivingEntity, float, float) private methodu return değerini modifiye eder.
 * Bu vanilla lightmap pipeline'ına yerleşir → tüm blok aydınlatması etkilenir.
 *
 * İki ayrı kaynak:
 *   1. Mirror / Anti-Mirror boyutu → minimum karanlık zorlanır (v3.0).
 *   2. Çözülen şifre sayısı → her kademe ~1/15 karanlık ekler (v8.1). Boyuttan bağımsız,
 *      yani overworld'de de geçerli — "dünyanın genel ışık seviyesi" istenen buydu.
 */
@Mixin(LightTexture.class)
public class LightmapTextureManagerMixin {

    @Inject(method = "calculateDarknessScale", at = @At("RETURN"), cancellable = true)
    private void candy$mirrorDarkness(LivingEntity entity, float f, float g,
                                       CallbackInfoReturnable<Float> cir) {
        try {
            if (entity == null || entity.level() == null) return;
            float mevcut = cir.getReturnValueF();
            float sonuc = mevcut;

            // v8.1: şifre karanlığı — her boyutta geçerli, taban olarak eklenir.
            float taban = KaranlikState.taban();
            if (taban > 0.0f) {
                sonuc = Math.min(1.0f, sonuc + (1.0f - sonuc) * taban);
            }

            String dimId = entity.level().dimension().location().toString();
            if (dimId.equals("candy:mirror") || dimId.equals("candy:anti_mirror")) {
                // Mirror'da minimum %55 karanlık — DARKNESS effect üstüne ekstra baseline
                float min = dimId.equals("candy:anti_mirror") ? 0.75f : 0.55f;
                if (sonuc < min) sonuc = min;
            }

            if (sonuc != mevcut) cir.setReturnValue(sonuc);
        } catch (Throwable ignored) {}
    }
}
