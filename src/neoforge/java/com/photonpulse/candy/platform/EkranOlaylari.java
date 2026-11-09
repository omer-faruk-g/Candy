package com.photonpulse.candy.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Ekran olaylari — NeoForge uygulamasi.
 *
 * ScreenEvent.Init.Post, Fabric'in AFTER_INIT'i ile ayni ani verir. Olay
 * istemciyi ve ekran olculerini tasimiyor; istemci Minecraft.getInstance(),
 * olculer ise ekranin kendisinden okunuyor — Fabric de ayni iki degeri veriyor.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class EkranOlaylari {

    public static final SonrasindaAcildi AFTER_INIT = new SonrasindaAcildi();

    private EkranOlaylari() {}

    @FunctionalInterface
    public interface Dinleyici {
        void ekranAcildi(Minecraft istemci, Screen ekran, int genislik, int yukseklik);
    }

    public static final class SonrasindaAcildi {
        private final List<Dinleyici> dinleyiciler = new CopyOnWriteArrayList<>();

        private SonrasindaAcildi() {}

        public void register(Dinleyici dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        boolean bos() {
            return dinleyiciler.isEmpty();
        }

        void calistir(Minecraft istemci, Screen ekran) {
            for (Dinleyici d : dinleyiciler) {
                d.ekranAcildi(istemci, ekran, ekran.width, ekran.height);
            }
        }
    }

    @SubscribeEvent
    public static void onEkranAcildi(ScreenEvent.Init.Post event) {
        if (AFTER_INIT.bos()) return;
        AFTER_INIT.calistir(Minecraft.getInstance(), event.getScreen());
    }
}
