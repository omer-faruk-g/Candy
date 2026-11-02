package com.photonpulse.candy.platform;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Sunucu yasam dongusu — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi. ServerStartedEvent ve
 * ServerStoppingEvent birebir karsiliklar: ikisi de MinecraftServer tasiyor.
 */
@EventBusSubscriber
public final class SunucuYasamDongusu {

    public static final Kanal SERVER_STARTED = new Kanal();
    public static final Kanal SERVER_STOPPING = new Kanal();

    private SunucuYasamDongusu() {}

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
    public static void onBasladi(ServerStartedEvent event) {
        SERVER_STARTED.calistir(event.getServer());
    }

    @SubscribeEvent
    public static void onKapaniyor(ServerStoppingEvent event) {
        SERVER_STOPPING.calistir(event.getServer());
    }
}
