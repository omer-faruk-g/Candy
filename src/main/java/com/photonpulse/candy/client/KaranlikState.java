package com.photonpulse.candy.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * KaranlikState — sunucudan gelen ışık kademesini tutar; LightmapTextureManagerMixin okur.
 *
 * Değer yalnızca ağ thread'inde yazılıp render thread'inde okunuyor, o yüzden volatile.
 */
@Environment(EnvType.CLIENT)
public final class KaranlikState {

    private KaranlikState() {}

    /** Vanilla ışık ölçeği 0..15; bir kademe ≈ 1/15 karanlık. */
    private static final float KADEME = 1.0f / 15.0f;
    /** Üst sınır: ekran tamamen siyah olmasın, oyun oynanabilir kalsın. */
    private static final float TABAN_UST = 0.45f;

    private static volatile int kademe = 0;

    public static void ayarla(int k) {
        kademe = Math.max(0, Math.min(10, k));
    }

    public static void sifirla() {
        kademe = 0;
    }

    public static int kademe() {
        return kademe;
    }

    /** Lightmap'e eklenecek karanlık tabanı (0..TABAN_UST). */
    public static float taban() {
        int k = kademe;
        if (k <= 0) return 0.0f;
        return Math.min(TABAN_UST, k * KADEME);
    }
}
