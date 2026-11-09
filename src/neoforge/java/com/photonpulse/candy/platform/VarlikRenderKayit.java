package com.photonpulse.candy.platform;

import com.photonpulse.candy.Candy;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Varlik renderer kaydi — NeoForge uygulamasi.
 *
 * <p>Fabric'te kayit dogrudan; NeoForge'da mod veri yolundaki
 * {@link EntityRenderersEvent.RegisterRenderers} icinde olmak zorunda. Cagrilar
 * burada toplanip olay geldiginde ayni sirayla aktariliyor.</p>
 *
 * <p><b>Niye Supplier:</b> entity tipleri ({@code CandyEntities.ARKADAS} vb.)
 * istemci giris kurucusundan ({@code NeoForgeIstemciGiris}) SONRA kayit oluyor.
 * Eskiden callsite tipi <i>deger</i> olarak okuyordu, yani null yakaliyordu;
 * null anahtar {@code registerEntityRenderer}'da {@code NullPointerException}
 * ("null key ... ArkadasRenderer") firlatip tum entity renderer kurulumunu
 * ("Failed to create model for minecraft:pig") cokertiyordu. Artik tip bir
 * tedarikci olarak saklaniyor ve olay aninda cozuluyor; o an tip dolu.</p>
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class VarlikRenderKayit {

    private record Kayit(Supplier<? extends EntityType<?>> tip,
                         EntityRendererProvider<?> saglayici) {}

    private static final List<Kayit> BEKLEYEN = new ArrayList<>();

    private VarlikRenderKayit() {}

    public static <T extends Entity> void register(Supplier<? extends EntityType<? extends T>> tip,
                                                   EntityRendererProvider<T> saglayici) {
        synchronized (BEKLEYEN) {
            BEKLEYEN.add(new Kayit(tip, saglayici));
        }
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void onRendererKayit(EntityRenderersEvent.RegisterRenderers event) {
        synchronized (BEKLEYEN) {
            for (Kayit k : BEKLEYEN) {
                EntityType<?> tip = k.tip().get();
                if (tip == null) {
                    Candy.LOG.warn("[Candy] renderer kaydi atlandi: entity tipi hala null");
                    continue;
                }
                event.registerEntityRenderer(
                    (EntityType<Entity>) tip, (EntityRendererProvider<Entity>) k.saglayici());
            }
        }
    }
}
