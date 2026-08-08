package com.photonpulse.candy.client;

/**
 * Client-side camscreen state.
 * Server packet gelir → tetikle çağrılır → belirtilen süre boyunca aktif kalır.
 * InGameHudMixin bu state'i okur ve overlay çizer.
 */
public final class CamscreenState {

    private static long baslangicMs = 0L;
    private static long sureMs = 0L;
    private static int textIndex = 0;

    private CamscreenState() {}

    public static void tetikle(int idx, int duration) {
        baslangicMs = System.currentTimeMillis();
        sureMs = duration;
        textIndex = idx;
    }

    public static boolean aktif() {
        return sureMs > 0 && (System.currentTimeMillis() - baslangicMs) < sureMs;
    }

    public static int getTextIndex() {
        return textIndex;
    }

    /** Fade değeri 1.0 → 0.0 (son 100ms'de fade out). */
    public static float getAlfa() {
        long gecen = System.currentTimeMillis() - baslangicMs;
        long kalan = sureMs - gecen;
        if (kalan <= 0) return 0f;
        if (kalan < 100) return kalan / 100f;
        return 1f;
    }
}
