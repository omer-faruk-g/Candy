package com.photonpulse.candy.client;

/**
 * Client-side Faz 8 countdown state. Server payload gelir → tetikle çağrılır.
 * InGameHudMixin sağ üstte gösterir.
 */
public final class BossCountdownState {

    private static int saniye = -1;

    private BossCountdownState() {}

    public static void set(int s) { saniye = s; }

    public static boolean aktif() { return saniye >= 0; }

    public static int getSaniye() { return saniye; }
}
