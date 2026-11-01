package com.photonpulse.candy.altyazi;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.net.AltyaziPayload;
import com.photonpulse.candy.net.BaslikPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Altyazi (v8.2, sunucu) — altyazı kanalı ve pencere başlığı için gönderim tarafı.
 *
 * <p>İki küçük kanal aynı sınıfta: ikisi de "oyuncunun Candy beklemediği yere Candy
 * koymak" işini yapıyor ve ikisi de yalnızca indeks gönderiyor. Ayrı iki sınıf olması
 * iki kayıt bloğu + iki register çağrısı demekti, kazancı yok.</p>
 */
public final class Altyazi {

    private Altyazi() {}

    /** lang'deki {@code candy.altyazi.N} sayısı. */
    public static final int ALTYAZI_SAYISI = 8;
    /** {@code client.PencereBasligi.BASLIKLAR} uzunluğu — istemciyle elle eşlenik. */
    public static final int BASLIK_SAYISI = 8;
    /** İkilik başlıkların dizideki ilk indeksi (v8.3) — 6 ve 7. */
    public static final int IKILIK_BASLIK_BAS = 6;

    public static void register() {
        try {
            PayloadTypeRegistry.playS2C().register(AltyaziPayload.ID, AltyaziPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Altyazi payload zaten kayıtlı: {}", t.getMessage());
        }
        try {
            PayloadTypeRegistry.playS2C().register(BaslikPayload.ID, BaslikPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Baslik payload zaten kayıtlı: {}", t.getMessage());
        }
        try {
            PayloadTypeRegistry.playS2C().register(
                com.photonpulse.candy.net.IkilikEkranPayload.ID,
                com.photonpulse.candy.net.IkilikEkranPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] IkilikEkran payload zaten kayıtlı: {}", t.getMessage());
        }
        Candy.LOG.info("[Candy] Altyazi kanalı hazır — altyazı + pencere başlığı");
    }

    /** Altyazı gönder. Metin istemcide; burada yalnızca indeks ve süre var. */
    public static void gonder(ServerPlayer player, int indeks, int sureMs) {
        try {
            if (player == null) return;
            if (indeks < 0 || indeks >= ALTYAZI_SAYISI) return;
            ServerPlayNetworking.send(player, new AltyaziPayload(indeks, sureMs));
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Altyazi gönderilemedi: {}", t.getMessage());
        }
    }

    /** Pencere başlığını değiştir. */
    public static void baslik(ServerPlayer player, int indeks) {
        try {
            if (player == null) return;
            if (indeks < 0 || indeks >= BASLIK_SAYISI) return;
            ServerPlayNetworking.send(player, new BaslikPayload(indeks, false));
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Başlık gönderilemedi: {}", t.getMessage());
        }
    }

    /**
     * Pencere başlığını <b>ikilik</b> bir fısıltıya çevirir (v8.3).
     *
     * <p>Terminal açılmadı; istek edilen "arka planda 0/1 yazsın" etkisinin gerçek
     * kaynağı görev çubuğundaki yazıydı ve bu kanal v8.2'den beri duruyor.</p>
     */
    public static void baslikIkilik(ServerPlayer player) {
        int kac = BASLIK_SAYISI - IKILIK_BASLIK_BAS;
        if (kac <= 0) return;
        baslik(player, IKILIK_BASLIK_BAS + (int) (Math.random() * kac));
    }

    /** İkilik fısıltıyı ekrana yollar (tam ekran ya da köşe). */
    public static void ikilikEkran(ServerPlayer player, int indeks,
                                   int sureMs, int kanal) {
        try {
            if (player == null) return;
            if (indeks < 0 || indeks >= com.photonpulse.candy.sifre.IkilikMetin.FISILTI.length) {
                return;
            }
            ServerPlayNetworking.send(player,
                new com.photonpulse.candy.net.IkilikEkranPayload(indeks, sureMs, kanal));
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] İkilik ekran gönderilemedi: {}", t.getMessage());
        }
    }

    /** Pencere başlığını özgün hâline döndür. */
    public static void baslikGeriAl(ServerPlayer player) {
        try {
            if (player == null) return;
            ServerPlayNetworking.send(player, new BaslikPayload(0, true));
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Başlık geri alınamadı: {}", t.getMessage());
        }
    }
}
