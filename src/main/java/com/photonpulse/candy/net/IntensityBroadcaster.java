package com.photonpulse.candy.net;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.Psikoloji;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * IntensityBroadcaster — server her saniye oyuncularına korku/agresivlik değerini yollar.
 *
 * Client mixinleri bu değeri IntensityState üzerinden okur.
 * Ölçek: 0-1 arası (dış katman normalize eder).
 */
public class IntensityBroadcaster {

    public static void register() {
        // Payload type — single-player'da client de aynı JVM'de register etmeye çalışır.
        // Duplicate registration exception atar, o yüzden try/catch.
        try {
            PayloadTypeRegistry.playS2C().register(
                CandyIntensityPayload.ID, CandyIntensityPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Payload zaten kayıtlı (client tarafından): {}", t.getMessage());
        }

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;
                double agr = Psikoloji.agresivlikCarpani(tp);
                double korku = tp.korkuSkoru;
                double ozguven = tp.candyOzguven;
                try {
                    ServerPlayNetworking.send(player,
                        new CandyIntensityPayload(agr, korku, ozguven));
                } catch (Throwable t) {
                    // Client bu payload'ı bilmiyorsa (vanilla client) hata verir — sessizce yut
                }
            }
        });

        Candy.LOG.info("[Candy] IntensityBroadcaster aktif — client sync 1 sn");
    }
}
