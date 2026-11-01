package com.photonpulse.candy.platform;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Oyuncu baglanti olaylari (istemci) — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi. HANDLER VE SENDER DUSURULDU,
 * gerekcesi src/fabric/.../IstemciBaglanti icinde.
 */
@EventBusSubscriber
public final class IstemciBaglanti {

    public static final Kanal JOIN = new Kanal();
    public static final Kanal DISCONNECT = new Kanal();

    private IstemciBaglanti() {}

    public static final class Kanal {
        private final List<Consumer<Minecraft>> dinleyiciler = new CopyOnWriteArrayList<>();

        private Kanal() {}

        public void register(Consumer<Minecraft> dinleyici) { dinleyiciler.add(dinleyici); }

        void calistir(Minecraft client) {
            for (Consumer<Minecraft> d : dinleyiciler) d.accept(client);
        }
    }

    @SubscribeEvent
    public static void giris(ClientPlayerNetworkEvent.LoggingIn olay) {
        JOIN.calistir(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void cikis(ClientPlayerNetworkEvent.LoggingOut olay) {
        DISCONNECT.calistir(Minecraft.getInstance());
    }
}
