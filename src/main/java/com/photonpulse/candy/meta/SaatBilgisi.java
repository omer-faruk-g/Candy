package com.photonpulse.candy.meta;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.net.SaatPayload;
import com.photonpulse.candy.platform.AgKayit;
import com.photonpulse.candy.platform.SunucuAg;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SaatBilgisi (v9.1.0) — sunucu tarafı duvar saati deposu (RAM-only, diske yazılmaz).
 *
 * <p>İstemci {@link SaatPayload}'u <b>yalnızca join'de bir kez</b> yollar; buradaki
 * {@link #dakika(ServerPlayer)} aradan geçen gerçek süreyi ekleyerek o anki dakikayı
 * verir. Böylece saatlik yenileme paketine gerek kalmıyor ve oturum gece yarısını
 * geçtiğinde değer kendiliğinden dönüyor — dizüstü uykuya girse bile, çünkü sayılan
 * şey monotonik süre değil duvar saati.</p>
 *
 * <p>{@code HayaletOyuncular} bunu Steve/Alex diyaloglarında kullanır; veri yalnızca
 * sahibine gösterilir (şehir ve donanım ile aynı kural).</p>
 */
public class SaatBilgisi {

    /** dakika = join anındaki gün içi dakika, ms = o anın duvar saati. */
    private record Kayit(int dakika, String dilim, long ms) {}

    private static final Map<UUID, Kayit> saatler = new ConcurrentHashMap<>();

    public static void register() {
        try {
            AgKayit.playC2S().register(SaatPayload.ID, SaatPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Saat C2S zaten kayıtlı: {}", t.getMessage());
        }

        SunucuAg.registerGlobalReceiver(SaatPayload.ID, (payload, context) -> {
            try {
                int dakika = payload.dakika();
                if (dakika < 0 || dakika > 1439) return;
                String dilim = payload.dilim();
                if (dilim == null || dilim.length() > 64) dilim = "";
                saatler.put(context.player().getUUID(),
                    new Kayit(dakika, dilim, System.currentTimeMillis()));
                // Log'a SAAT yazılmıyor, yalnızca alındığı — kişisel veri log'a düşmesin.
                Candy.LOG.info("[Candy] Saat alindi [{}]", context.player().getName().getString());
            } catch (Throwable ignored) {}
        });

        Candy.LOG.info("[Candy] SaatBilgisi aktif (v9.1.0)");
    }

    /** Oyuncunun o anki gün içi dakikası (0–1439), bilinmiyorsa -1. */
    public static int dakika(ServerPlayer player) {
        if (player == null) return -1;
        Kayit k = saatler.get(player.getUUID());
        if (k == null) return -1;
        long gecen = (System.currentTimeMillis() - k.ms()) / 60_000L;
        if (gecen < 0) gecen = 0;
        return (int) ((k.dakika() + gecen) % 1440L);
    }

    /** Oyuncunun o anki saati (0–23), bilinmiyorsa -1. */
    public static int saat(ServerPlayer player) {
        int d = dakika(player);
        return (d < 0) ? -1 : d / 60;
    }

    /** "Europe/Istanbul" — bilinmiyorsa null. */
    public static String dilim(ServerPlayer player) {
        if (player == null) return null;
        Kayit k = saatler.get(player.getUUID());
        if (k == null || k.dilim() == null || k.dilim().isBlank()) return null;
        return k.dilim();
    }

    /** "03:14" — bilinmiyorsa null. */
    public static String metin(ServerPlayer player) {
        int d = dakika(player);
        if (d < 0) return null;
        return String.format("%02d:%02d", d / 60, d % 60);
    }

    public static void oyuncuAyrildi(UUID uuid) {
        saatler.remove(uuid);
    }

    public static void sunucuKapan() {
        saatler.clear();
    }
}
