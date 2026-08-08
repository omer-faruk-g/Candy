package com.photonpulse.candy.audio;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.sessizlik.SessizlikState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;

import java.util.UUID;

/**
 * BhootAmbient (v1.9.4) — modun ana sesi. Her zaman çalar.
 *
 * bhoot.ogg 8 sn uzunluğunda. Her 7 sn (140 tick) player'a doğrudan gönderilir
 * → seamless loop, ambient background music hissi.
 * MASTER kategori → sadece master volume slider'ı kontrol eder.
 *
 * playSoundToPlayer kullanılıyor — positional değil, player'la beraber gelir.
 * Sessizlik protokolü aktifken susulur (build-up bozulmasın).
 */
public class BhootAmbient {

    private static final int TICK_ARALIGI = 140; // 7 sn (bhoot 8 sn — 1 sn overlap)
    private static final float VOLUME = 0.5f;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BhootAmbient::onTick);
        Candy.LOG.info("[Candy] BhootAmbient aktif — ana ses (MASTER, loop 7sn)");
    }

    private static void onTick(MinecraftServer server) {
        if (server.getTicks() % TICK_ARALIGI != 0) return;
        long simdi = server.getOverworld().getTime();
        if (SessizlikState.sessizlikte(simdi)) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;
            if (player.isSpectator()) continue;

            try {
                player.playSoundToPlayer(
                    CandySounds.BHOOT, SoundCategory.MASTER,
                    VOLUME, 1.0f);
            } catch (Throwable ignored) {}
        }
    }

    public static void oyuncuAyrildi(UUID uuid) {
        // no state
    }

    public static void sunucuKapan() {
        // no state
    }
}
