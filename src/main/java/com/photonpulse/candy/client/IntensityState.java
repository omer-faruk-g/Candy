package com.photonpulse.candy.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffects;

/**
 * IntensityState (v1.5.0-mixin) — 0-1 arası korku yoğunluğu.
 *
 * Öncelikli kaynak: server'dan gelen CandyIntensityPayload (agresivlik+korku+ozguven).
 * Yedek: server payload'ı 5 sn'den eskiyse client-side türetilir (DARKNESS/BLINDNESS/gece).
 *
 * Mixin'ler bu sınıftan okur.
 */
public final class IntensityState {

    private static double serverIntensity = 0.0;
    private static long sonServerGuncelleme = 0L;
    private static final long TAZE_SURE_MS = 5000L;

    private IntensityState() {}

    /** Server 1 sn'de bir çağırıyor. */
    public static void setFromServer(double agresivlik, double korku, double ozguven) {
        // Normalizasyon: 3 kaynak → 0-1
        // agresivlik 1.0-5.0 → 0-1 (2 tabanlı)
        // korku 0-100 → 0-1
        // ozguven 0-100 → 0-1 (bonus)
        double agrN = Math.min(1.0, Math.max(0, (agresivlik - 1.0) / 4.0));
        double korkuN = Math.min(1.0, korku / 100.0);
        double ozN = Math.min(1.0, ozguven / 100.0);
        // Ağırlıklı ortalama — korku baskın
        serverIntensity = Math.min(1.0, korkuN * 0.55 + agrN * 0.30 + ozN * 0.15);
        sonServerGuncelleme = System.currentTimeMillis();
    }

    /** 0.0 = sakin, 1.0 = tam kaos. */
    public static double get() {
        long simdi = System.currentTimeMillis();
        if (simdi - sonServerGuncelleme < TAZE_SURE_MS) {
            return serverIntensity;
        }
        // Fallback: client-side türetme
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return 0.0;
        double i = 0.0;
        var p = mc.player;
        if (p.hasStatusEffect(StatusEffects.DARKNESS)) i += 0.4;
        if (p.hasStatusEffect(StatusEffects.BLINDNESS)) i += 0.3;
        if (p.hasStatusEffect(StatusEffects.WEAKNESS)) i += 0.2;
        long tod = mc.world.getTimeOfDay() % 24000L;
        boolean gece = tod >= 13000L && tod <= 23000L;
        if (gece) i += 0.1;
        return Math.min(1.0, i);
    }

    public static boolean aktif() {
        return get() > 0.2;
    }
}
