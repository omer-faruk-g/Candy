package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Varlik renderer kaydi — Fabric uygulamasi.
 */
public final class VarlikRenderKayit {

    private VarlikRenderKayit() {}

    public static <T extends Entity> void register(EntityType<? extends T> tip,
                                                   EntityRendererProvider<T> saglayici) {
        EntityRendererRegistry.register(tip, saglayici);
    }
}
