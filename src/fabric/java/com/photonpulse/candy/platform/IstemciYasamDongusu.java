package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/**
 * Istemci yasam dongusu — Fabric uygulamasi.
 */
public final class IstemciYasamDongusu {

    public static final Kapaniyor CLIENT_STOPPING = new Kapaniyor();

    private IstemciYasamDongusu() {}

    public static final class Kapaniyor {
        private Kapaniyor() {}

        public void register(Consumer<Minecraft> dinleyici) {
            ClientLifecycleEvents.CLIENT_STOPPING.register(dinleyici::accept);
        }
    }
}
