package com.photonpulse.candy.client;

/**
 * Client cache — server'dan gelen aktif hastalık adı.
 * InGameHudMixin bu değeri okur ve HUD'da gösterir.
 */
public final class HastalikState {

    private static String aktif = "";

    private HastalikState() {}

    public static void set(String h) {
        aktif = h == null ? "" : h;
    }

    public static String get() {
        return aktif;
    }

    public static boolean var() {
        return !aktif.isEmpty();
    }
}
