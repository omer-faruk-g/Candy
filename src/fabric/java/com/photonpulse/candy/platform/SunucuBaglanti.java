package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Oyuncu baglanti olaylari (sunucu) — Fabric uygulamasi.
 *
 * Her dinleyici dogrudan Fabric'in kendi olayina baglaniyor; araya sira/faz
 * yonetimi girmiyor, yani Fabric'in cagirma sirasi aynen korunuyor.
 *
 * SENDER PARAMETRESI DUSURULDU. Fabric'in imzasi
 * {@code (handler, sender, server)}; ortadaki {@code sender} Fabric'e ozgu
 * {@code PacketSender} ve NeoForge'da karsiligi YOK. Candy'nin 7 JOIN cagrisinin
 * hicbiri onu okumuyor, o yuzden hep {@code null} gecen hayalet bir parametre
 * uydurmak yerine arayuzden cikarildi — DISCONNECT'in zaten tasidigi bicim.
 */
public final class SunucuBaglanti {

    public static final GirisKanali JOIN = new GirisKanali();
    public static final CikisKanali DISCONNECT = new CikisKanali();

    private SunucuBaglanti() {}

    public interface Giris {
        void onPlayReady(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }

    public interface Cikis {
        void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }

    public static final class GirisKanali {
        private GirisKanali() {}

        public void register(Giris dinleyici) {
            ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> dinleyici.onPlayReady(handler, server));
        }
    }

    public static final class CikisKanali {
        private CikisKanali() {}

        public void register(Cikis dinleyici) {
            ServerPlayConnectionEvents.DISCONNECT.register(dinleyici::onPlayDisconnect);
        }
    }
}
