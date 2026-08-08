package com.photonpulse.candy.basarim;

import com.photonpulse.candy.Candy;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * CandyBasarimlar (v1.9.2) — merkezi advancement tetikleyici.
 *
 * Kullanım: CandyBasarimlar.tetikle(player, "hos_geldin")
 * Advancement JSON'ları data/candy/advancement/*.json'da tanımlı.
 * Trigger tipi minecraft:impossible → sadece kod grant edebilir.
 */
public class CandyBasarimlar {

    public static void tetikle(ServerPlayerEntity player, String kod) {
        try {
            if (player == null) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;
            AdvancementEntry adv = server.getAdvancementLoader()
                .get(Identifier.of("candy", kod));
            if (adv == null) return;
            var progress = player.getAdvancementTracker().getProgress(adv);
            if (progress.isDone()) return;
            for (String criterion : progress.getUnobtainedCriteria()) {
                player.getAdvancementTracker().grantCriterion(adv, criterion);
            }
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] basarim tetikleme hata ({}): {}", kod, t.getMessage());
        }
    }
}
