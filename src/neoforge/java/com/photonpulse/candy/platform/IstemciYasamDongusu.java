package com.photonpulse.candy.platform;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Istemci kapanisi — NeoForge uygulamasi.
 *
 * Fabric'in CLIENT_STOPPING'i yerine GameShuttingDownEvent kullaniliyor.
 * Olay istemciyi tasimiyor; Minecraft.getInstance() veriliyor — ayni tekil
 * ornek. Dist.CLIENT ile sinirli oldugu icin olay yalnizca istemcide dinlenir.
 *
 * ZAMANLAMA FARKI: Fabric CLIENT_STOPPING istemci kapanis dizisinin icinde,
 * GameShuttingDownEvent ise oyun surecinin kapanis kancasinda atilir. Ikisi de
 * kapanis anidir ama ayni nokta degildir; detay MULTILOADER.md'de.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class IstemciYasamDongusu {

    public static final Kanal CLIENT_STOPPING = new Kanal();

    private IstemciYasamDongusu() {}

    public static final class Kanal {
        private final List<Consumer<Minecraft>> dinleyiciler = new CopyOnWriteArrayList<>();

        private Kanal() {}

        public void register(Consumer<Minecraft> dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        void calistir(Minecraft istemci) {
            for (Consumer<Minecraft> d : dinleyiciler) {
                d.accept(istemci);
            }
        }
    }

    @SubscribeEvent
    public static void onKapaniyor(GameShuttingDownEvent event) {
        CLIENT_STOPPING.calistir(Minecraft.getInstance());
    }
}
