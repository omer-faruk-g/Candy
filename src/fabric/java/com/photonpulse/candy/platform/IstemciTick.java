package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/**
 * Istemci tick kancasi — Fabric uygulamasi.
 *
 * SunucuTick ile ayni bicim: cagri yerlerinde yalnizca import satiri
 * degisiyor, govde degismiyor. MeYuz::istemciTick gibi metot referanslari
 * oldugu gibi calisiyor.
 */
public final class IstemciTick {

    public static final Kanal END_CLIENT_TICK = new Kanal();

    private IstemciTick() {}

    public static final class Kanal {
        private Kanal() {}

        public void register(Consumer<Minecraft> dinleyici) {
            ClientTickEvents.END_CLIENT_TICK.register(dinleyici::accept);
        }
    }
}
