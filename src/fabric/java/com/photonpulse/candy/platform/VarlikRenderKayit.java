package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

/**
 * Varlik renderer kaydi — Fabric uygulamasi.
 *
 * Tip bir {@link Supplier} olarak geliyor: NeoForge'da entity tipleri istemci
 * giris kurucusundan SONRA kayit oluyor, o yuzden deger degil tedarikci
 * saklanmali. Fabric'te sira dogru oldugu icin tedarikci hemen cozuluyor.
 */
public final class VarlikRenderKayit {

    private VarlikRenderKayit() {}

    public static <T extends Entity> void register(Supplier<? extends EntityType<? extends T>> tip,
                                                   EntityRendererProvider<T> saglayici) {
        EntityRendererRegistry.register(tip.get(), saglayici);
    }
}
