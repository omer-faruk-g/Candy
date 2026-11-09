package com.photonpulse.candy.client;

import com.photonpulse.candy.sifre.IkilikMetin;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}

/**
 * IkilikEkranState (v8.3, istemci) — ikilik fısıltının görünür durumu.
 *
 * <p>Metin burada <b>üretiliyor</b>, ağdan gelmiyor: sunucu indeks yolluyor, bu sınıf
 * {@link IkilikMetin#fisiltiIkilik(int)} ile 0/1 dizisini kuruyor. Tek kaynak ortak
 * kodda olduğu için sunucu ve istemcinin metni ayrışamaz.</p>
 *
 * <p>{@link CamscreenState} gibi ayrı bir sınıf olmasının nedeni: camscreen'in metin
 * varyantı yalnızca {@code candy.camscreen.N} lang anahtarını çiziyor ve ham bir dizi
 * basamağı çizemiyor. Camscreen'i genişletmek orada beş dalı olan bir switch'i daha da
 * dallandıracaktı; ayrı durum daha ucuz.</p>
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
public final class IkilikEkranState {

    private IkilikEkranState() {}

    /** Sönümlenme süresi (ms) — son bu kadarında alfa düşer. */
    private static final long SONUM_MS = 500L;

    private static volatile String ikilik = "";
    private static volatile int kanal = 0;
    private static volatile long bitis = 0L;
    private static volatile long sure = 0L;

    public static void tetikle(int indeks, int sureMs, int kanalNo) {
        String s = IkilikMetin.fisiltiIkilik(indeks);
        if (s.isEmpty()) return;
        int ms = Math.max(200, Math.min(15_000, sureMs));
        ikilik = s;
        kanal = kanalNo;
        sure = ms;
        bitis = System.currentTimeMillis() + ms;
    }

    public static void sifirla() {
        bitis = 0L;
        ikilik = "";
    }

    public static boolean aktif() {
        return bitis > System.currentTimeMillis() && !ikilik.isEmpty();
    }

    /** 0 = tam ekran, 1 = köşe. */
    public static int kanal() {
        return kanal;
    }

    public static String metin() {
        return ikilik;
    }

    public static float alfa() {
        long kalan = bitis - System.currentTimeMillis();
        if (kalan <= 0L) return 0f;
        if (kalan >= sure - SONUM_MS) {
            // Açılış sönümü: ani belirme glitch gibi değil, sızma gibi görünsün.
            long gecen = sure - kalan;
            if (gecen < SONUM_MS) return Math.max(0f, Math.min(1f, gecen / (float) SONUM_MS));
            return 1f;
        }
        if (kalan < SONUM_MS) return Math.max(0f, Math.min(1f, kalan / (float) SONUM_MS));
        return 1f;
    }
}
