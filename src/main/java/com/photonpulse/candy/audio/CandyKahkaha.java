package com.photonpulse.candy.audio;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.sessizlik.SessizlikState;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * CandyKahkaha (v1.9.6) — Candy'nin üstünlük kahkahası.
 *
 * candyOzguven ≥ 60 iken (Candy güç kazandı, oyuncu darda) her 2 dk kontrol.
 * İhtimal özgüven ile ölçekli: 60'ta %0, 100'de %40 → yüksek özgüven = sık kahkaha.
 * Cooldown 5 dk minimum → spam olmaz, memorable moment.
 *
 * MASTER kategori → master volume slider ile kontrol.
 * Sessizlik protokolünde susulur (build-up bozulmaz).
 * Kahkaha sonrası özgüven +5 (dominance loop — kahkaha attığı için daha güvenli).
 */
public class CandyKahkaha {

    private static final int TICK_ARALIGI = 2400;  // 2 dk kontrol
    private static final long MIN_COOLDOWN = 6000L; // 5 dk minimum
    private static final double OZGUVEN_ESIK = 60.0;
    private static final Random RNG = new Random();
    private static final Map<UUID, Long> sonKahkaha = new HashMap<>();

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(CandyKahkaha::onTick);
        Candy.LOG.info("[Candy] CandyKahkaha aktif — özgüven ≥ 60'ta üstünlük kahkahası");
    }

    private static void onTick(MinecraftServer server) {
        if (server.getTickCount() % TICK_ARALIGI != 0) return;
        long simdi = server.overworld().getGameTime();
        if (SessizlikState.sessizlikte(simdi)) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;
            if (player.isCreative() || player.isSpectator()) continue;
            if (tp.candyOzguven < OZGUVEN_ESIK) continue;

            Long son = sonKahkaha.get(player.getUUID());
            if (son != null && simdi - son < MIN_COOLDOWN) continue;

            // İhtimal — özgüven 60'ta %0, 100'de %40
            double ihtimal = (tp.candyOzguven - OZGUVEN_ESIK) / 100.0;
            if (RNG.nextDouble() > ihtimal) continue;

            sonKahkaha.put(player.getUUID(), simdi);

            try {
                SesGonder.tek(player,
                    CandySounds.LAUGH, SoundSource.MASTER,
                    0.9f, 1.0f);
            } catch (Throwable ignored) {}

            // Dominance loop — kahkaha attığı için daha güvenli
            tp.candyOzguven = Math.min(100, tp.candyOzguven + 5);

            Candy.LOG.info("[Candy] KAHKAHA → {} (ozguven {} → {})",
                player.getName().getString(),
                String.format("%.1f", tp.candyOzguven - 5),
                String.format("%.1f", tp.candyOzguven));
        }
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonKahkaha.remove(uuid);
    }

    public static void sunucuKapan() {
        sonKahkaha.clear();
    }
}
