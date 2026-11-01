package com.photonpulse.candy.kalp;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.clone.CloneManager;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * KalpHizi — düşük HP'de Candy yaklaşır (v1.4.3).
 *
 * Player HP ≤ 4 (2 kalp) + korku ≥ 40 + kurulum tamam olduğunda,
 * CloneManager.zorlaKlonSpawn ile Candy klonu 12-20 blok içinde spawn olur.
 * Klon zaten default AI ile hareket eder — "gördüm mü?" hissi.
 *
 * Cooldown: 10 dk per oyuncu (spam olmasın).
 */
public class KalpHizi {

    private static final float HP_ESIK = 4.0f;
    private static final double KORKU_ESIK = 40.0;
    private static final long COOLDOWN_TICK = 12000L; // 10 dk

    private static final Map<UUID, Long> sonTetiklenme = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return; // saniyede bir

            long simdi = server.overworld().getGameTime();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;
                if (player.isCreative() || player.isSpectator()) continue;
                if (player.isDeadOrDying()) continue;

                if (player.getHealth() > HP_ESIK) continue;
                if (tp.korkuSkoru < KORKU_ESIK) continue;

                Long son = sonTetiklenme.get(player.getUUID());
                if (son != null && simdi - son < COOLDOWN_TICK) continue;

                boolean spawn = CloneManager.zorlaKlonSpawn(player, tp);
                if (spawn) {
                    sonTetiklenme.put(player.getUUID(), simdi);
                    Candy.LOG.info("[Candy] KalpHizi[{}] — HP={}, klon spawn",
                        player.getName().getString(),
                        String.format("%.1f", player.getHealth()));
                }
            }
        });
        Candy.LOG.info("[Candy] KalpHizi aktif — HP≤4 + korku≥40'ta Candy yaklaşır");
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonTetiklenme.remove(uuid);
    }

    public static void sunucuKapan() {
        sonTetiklenme.clear();
    }
}
