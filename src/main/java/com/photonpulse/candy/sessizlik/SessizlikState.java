package com.photonpulse.candy.sessizlik;

/**
 * SessizlikState (v1.9.0) — global sessizlik state.
 *
 * Belirli anlarda 30-60 sn tam sessizlik: chat, camscreen, ses hepsi susulur.
 * Sonrasında forced event (klon spawn) — build-up + payoff.
 *
 * Diğer sistemler `sessizlikte(simdi)` çağırır ve skip eder.
 */
public final class SessizlikState {

    private static long sessizlikBitisTick = 0L;
    private static long sonSessizlikBaslangicTick = 0L;

    private SessizlikState() {}

    public static void baslat(long simdi, int sureTick) {
        sessizlikBitisTick = simdi + sureTick;
        sonSessizlikBaslangicTick = simdi;
    }

    public static boolean sessizlikte(long simdi) {
        return sessizlikBitisTick > 0 && simdi < sessizlikBitisTick;
    }

    /** Sessizlik yeni bittiyse true, sonra tüketilir. */
    public static boolean tuketBitis(long simdi) {
        if (sessizlikBitisTick > 0 && simdi >= sessizlikBitisTick) {
            sessizlikBitisTick = 0L;
            return true;
        }
        return false;
    }

    public static long getSonBaslangic() {
        return sonSessizlikBaslangicTick;
    }

    public static void temizle() {
        sessizlikBitisTick = 0L;
        sonSessizlikBaslangicTick = 0L;
    }
}
