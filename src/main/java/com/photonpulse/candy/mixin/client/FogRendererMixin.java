package com.photonpulse.candy.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FogRendererMixin (v3.0 Faz 1.2) — Mirror/Anti-Mirror özel fog rengi.
 *
 * applyFog() Vector4f döner (r, g, b, alpha/distance). Return sonrası player'ın dünyasına
 * göre renk override edilir:
 *   - Mirror: koyu kırmızımsı-kahverengi (Silent Hill "otherworld" hissi)
 *   - Anti-Mirror: koyu mor (uzay boşluğu / kayıp hissi)
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @Inject(method = "applyFog", at = @At("RETURN"))
    private void candy$mirrorFog(
        Camera camera, int viewDistance, boolean thickFog,
        RenderTickCounter tickCounter, float skyDarkness, ClientWorld world,
        CallbackInfoReturnable<Vector4f> cir
    ) {
        try {
            if (world == null) return;
            String dimId = world.getRegistryKey().getValue().toString();
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
}
