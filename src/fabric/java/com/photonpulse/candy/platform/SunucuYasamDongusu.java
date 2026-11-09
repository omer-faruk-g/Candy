package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/**
 * Sunucu yasam dongusu — Fabric uygulamasi.
 *
 * Kanal adlari Fabric'teki adlari koruyor, boylece cagri yeri govdeleri
 * degismiyor.
 */
public final class SunucuYasamDongusu {

    public static final Basladi SERVER_STARTED = new Basladi();
    public static final Kapaniyor SERVER_STOPPING = new Kapaniyor();

    private SunucuYasamDongusu() {}

    public static final class Basladi {
        private Basladi() {}

        public void register(Consumer<MinecraftServer> dinleyici) {
            ServerLifecycleEvents.SERVER_STARTED.register(dinleyici::accept);
        }
    }

    public static final class Kapaniyor {
        private Kapaniyor() {}

        public void register(Consumer<MinecraftServer> dinleyici) {
            ServerLifecycleEvents.SERVER_STOPPING.register(dinleyici::accept);
        }
    }
}
