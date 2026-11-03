package com.photonpulse.candy.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Oyuncu baglanti olaylari (sunucu) — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi. SENDER PARAMETRESI DUSURULDU,
 * gerekcesi src/fabric/.../SunucuBaglanti icinde.
 *
 * {@code handler} GERCEK bir nesne, uydurma degil: NeoForge olayindan gelen
 * oyuncunun {@code ServerPlayer.connection} alani ayni
 * {@code ServerGamePacketListenerImpl}. Boylece cagri yerlerindeki
 * {@code handler.player} ve {@code handler.getPlayer()} iki loader'da da ayni
 * nesneyi okuyor.
 *
 * ZAMANLAMA FARKI — KALIYOR, GIZLENMIYOR:
 * Fabric'in JOIN'i ({@code Join.onPlayReady}) oyun asamasi hazir oldugunda,
 * NeoForge'un {@code PlayerLoggedInEvent}'i oyuncu listesine eklendiginde
 * atesleniyor. Ikisi yakin ama ayni nokta degil. Candy'nin 7 JOIN dinleyicisinin
 * hicbiri paket GONDERMIYOR (hepsi kisilik/hafiza okuyup zamanlayici kuruyor),
 * o yuzden fark derleme ya da protokol duzeyinde bir sorun degil; yine de
 * calisma zamaninda dogrulanmasi gereken bir nokta ve NeoForge jar uretilince
 * ilk bakilacak yerlerden biri.
 */
@EventBusSubscriber
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
        private final List<Giris> dinleyiciler = new CopyOnWriteArrayList<>();

        private GirisKanali() {}

        public void register(Giris dinleyici) { dinleyiciler.add(dinleyici); }

        void calistir(ServerGamePacketListenerImpl handler, MinecraftServer server) {
            for (Giris d : dinleyiciler) d.onPlayReady(handler, server);
        }
    }

    public static final class CikisKanali {
        private final List<Cikis> dinleyiciler = new CopyOnWriteArrayList<>();

        private CikisKanali() {}

        public void register(Cikis dinleyici) { dinleyiciler.add(dinleyici); }

        void calistir(ServerGamePacketListenerImpl handler, MinecraftServer server) {
            for (Cikis d : dinleyiciler) d.onPlayDisconnect(handler, server);
        }
    }

    @SubscribeEvent
    public static void giris(PlayerEvent.PlayerLoggedInEvent olay) {
        if (!(olay.getEntity() instanceof ServerPlayer oyuncu)) return;
        JOIN.calistir(oyuncu.connection, oyuncu.level().getServer());
    }

    @SubscribeEvent
    public static void cikis(PlayerEvent.PlayerLoggedOutEvent olay) {
        if (!(olay.getEntity() instanceof ServerPlayer oyuncu)) return;
        DISCONNECT.calistir(oyuncu.connection, oyuncu.level().getServer());
    }
}
