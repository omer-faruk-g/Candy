package com.photonpulse.candy.platform;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Varlik renderer kaydi — NeoForge uygulamasi.
 *
 * Fabric'te kayit dogrudan; NeoForge'da mod veri yolundaki
 * EntityRenderersEvent.RegisterRenderers icinde olmak zorunda. Cagrilar burada
 * toplanip olay geldiginde ayni sirayla aktariliyor.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class VarlikRenderKayit {

    private static final Map<EntityType<?>, EntityRendererProvider<?>> BEKLEYEN =
        new LinkedHashMap<>();

    private VarlikRenderKayit() {}

    public static <T extends Entity> void register(EntityType<? extends T> tip,
                                                   EntityRendererProvider<T> saglayici) {
        synchronized (BEKLEYEN) {
            BEKLEYEN.put(tip, saglayici);
        }
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void onRendererKayit(EntityRenderersEvent.RegisterRenderers event) {
        synchronized (BEKLEYEN) {
            BEKLEYEN.forEach((tip, saglayici) -> event.registerEntityRenderer(
                (EntityType<Entity>) tip, (EntityRendererProvider<Entity>) saglayici));
        }
    }
}
