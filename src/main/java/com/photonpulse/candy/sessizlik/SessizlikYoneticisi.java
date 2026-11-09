package com.photonpulse.candy.sessizlik;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.clone.CloneManager;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.Psikoloji;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.server.level.ServerPlayer;
import java.util.Random;

/**
 * SessizlikYoneticisi (v1.9.0) — periyodik "hiçbir şey olmuyor" anları.
 *
 * Her 20 sn kontrol. Agresivlik yüksekse (>2.0) ve son sessizlikten en az 5 dk geçtiyse
 * %25 ihtimalle 30-60 sn sessizlik başlatır. Sessizlik sırasında:
 *   - HayaletOyuncular chat/mod skip
 *   - CamscreenTetikleyici skip
 *   - SesTaklit skip
 *
 * Sessizlik bittiği anda: rastgele online player için forced klon spawn.
 * "Hiçbir şey olmuyor" gerginliği → "işte oldu" payoff.
 */
public class SessizlikYoneticisi {

    private static final int KONTROL_ARALIGI = 400;      // 20 sn
    private static final long MIN_ARA_TICK = 6000L;      // Son sessizlikten min 5 dk sonra
    private static final double IHTIMAL = 0.25;
    private static final Random RNG = new Random();

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(server -> {
            long simdi = server.overworld().getGameTime();

            // Sessizlik bittiyse → forced event
            if (SessizlikState.tuketBitis(simdi)) {
                var players = server.getPlayerList().getPlayers();
                if (!players.isEmpty()) {
                    ServerPlayer target = players.get(RNG.nextInt(players.size()));
                    TruePersonality tp = PersonalityManager.get(target);
                    if (tp.kurulumTamam && !target.isCreative() && !target.isSpectator()) {
                        boolean spawn = CloneManager.zorlaKlonSpawn(target, tp);
                        Candy.LOG.info("[Candy] Sessizlik bitti → payoff (klon: {}) → {}",
                            spawn, target.getName().getString());
                        com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(target, "sessizlik_altin");
                        if (spawn) {
                            com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(target, "ayna");
                        }
                    }
                }
            }

            if (server.getTickCount() % KONTROL_ARALIGI != 0) return;
            if (SessizlikState.sessizlikte(simdi)) return;
            if (simdi - SessizlikState.getSonBaslangic() < MIN_ARA_TICK) return;

            var players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) return;

            // En agresif oyuncuyu bul (agresivlik >2.0 ise başlat)
            double maxAgr = 0;
            for (ServerPlayer p : players) {
                TruePersonality tp = PersonalityManager.get(p);
                if (!tp.kurulumTamam) continue;
                double agr = Psikoloji.agresivlikCarpani(tp);
                if (agr > maxAgr) maxAgr = agr;
            }
            if (maxAgr < 2.0) return;
            if (RNG.nextDouble() > IHTIMAL) return;

            int sure = 600 + RNG.nextInt(600); // 30-60 sn
            SessizlikState.baslat(simdi, sure);
            Candy.LOG.info("[Candy] Sessizlik başladı — {} tick (max agr {})",
                sure, String.format("%.2f", maxAgr));
        });

        Candy.LOG.info("[Candy] SessizlikYoneticisi aktif — build-up + payoff protokolü");
    }

    public static void sunucuKapan() {
        SessizlikState.temizle();
    }
}
