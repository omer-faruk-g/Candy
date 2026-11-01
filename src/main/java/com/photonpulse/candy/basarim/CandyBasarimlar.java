package com.photonpulse.candy.basarim;

import com.photonpulse.candy.Candy;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * CandyBasarimlar (v1.9.2) — merkezi advancement tetikleyici.
 *
 * Kullanım: CandyBasarimlar.tetikle(player, "hos_geldin")
 * Advancement JSON'ları data/candy/advancement/*.json'da tanımlı.
 * Trigger tipi minecraft:impossible → sadece kod grant edebilir.
 */
public class CandyBasarimlar {

    public static void tetikle(ServerPlayer player, String kod) {
        try {
            if (player == null) return;
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            AdvancementHolder adv = server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath("candy", kod));
            if (adv == null) return;
            var progress = player.getAdvancements().getOrStartProgress(adv);
            if (progress.isDone()) return;
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(adv, criterion);
            }
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] basarim tetikleme hata ({}): {}", kod, t.getMessage());
        }
    }
}
