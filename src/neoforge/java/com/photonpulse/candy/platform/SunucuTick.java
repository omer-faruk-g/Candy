package com.photonpulse.candy.platform;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Sunucu tick kancasi — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi; her dugum yalnizca kendi loader'inin
 * kopyasini derler (src/fabric/java veya src/neoforge/java). Cagri yerleri
 * iki tarafta da ayni kodu yaziyor.
 */
@EventBusSubscriber
public final class SunucuTick {

    public static final Kanal END_SERVER_TICK = new Kanal();

    private SunucuTick() {}

    public static final class Kanal {
        private final List<Consumer<MinecraftServer>> dinleyiciler = new CopyOnWriteArrayList<>();

        private Kanal() {}

        public void register(Consumer<MinecraftServer> dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        void calistir(MinecraftServer sunucu) {
            for (Consumer<MinecraftServer> d : dinleyiciler) {
                d.accept(sunucu);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        END_SERVER_TICK.calistir(event.getServer());
    }
}
