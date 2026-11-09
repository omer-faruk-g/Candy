package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Ekran olaylari — Fabric uygulamasi.
 */
public final class EkranOlaylari {

    public static final SonrasindaAcildi AFTER_INIT = new SonrasindaAcildi();

    private EkranOlaylari() {}

    @FunctionalInterface
    public interface Dinleyici {
        void ekranAcildi(Minecraft istemci, Screen ekran, int genislik, int yukseklik);
    }

    public static final class SonrasindaAcildi {
        private SonrasindaAcildi() {}

        public void register(Dinleyici dinleyici) {
            ScreenEvents.AFTER_INIT.register(dinleyici::ekranAcildi);
        }
    }
}
