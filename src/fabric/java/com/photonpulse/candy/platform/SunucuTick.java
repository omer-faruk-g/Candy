package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/**
 * Sunucu tick kancasi — Fabric uygulamasi.
 *
 * Bicim, Candy'nin kaynaginda zaten kullanilan bicimle ayni tutuldu
 * (END_SERVER_TICK.register(X::onTick)), boylece cagri yerlerinde yalnizca
 * import satiri degisiyor, govde degismiyor.
 */
public final class SunucuTick {

    public static final Kanal END_SERVER_TICK = new Kanal();

    private SunucuTick() {}

    public static final class Kanal {
        private Kanal() {}

        public void register(Consumer<MinecraftServer> dinleyici) {
            ServerTickEvents.END_SERVER_TICK.register(dinleyici::accept);
        }
    }
}
