package com.photonpulse.candy.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LightmapTextureManagerMixin (v3.0 Faz 1.2) — Mirror'da lightmap karartma.
 *
 * getDarkness(LivingEntity, float, float) private methodu return değerini modifiye eder.
 * Player Mirror/Anti-Mirror'daysa minimum darkness zorlanır — dünya sürekli karanlık hisseder.
 * Bu vanilla lightmap pipeline'ına yerleşir → tüm blok aydınlatması etkilenir.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    @Inject(method = "getDarkness", at = @At("RETURN"), cancellable = true)
    private void candy$mirrorDarkness(LivingEntity entity, float f, float g,
                                       CallbackInfoReturnable<Float> cir) {
        try {
            if (entity == null || entity.getWorld() == null) return;
            String dimId = entity.getWorld().getRegistryKey().getValue().toString();
            if (!dimId.equals("candy:mirror") && !dimId.equals("candy:anti_mirror")) return;

            float mevcut = cir.getReturnValueF();
            // Mirror'da minimum %55 karanlık — DARKNESS effect üstüne ekstra baseline
            float min = dimId.equals("candy:anti_mirror") ? 0.75f : 0.55f;
            if (mevcut < min) cir.setReturnValue(min);
        } catch (Throwable ignored) {}
    }
}
