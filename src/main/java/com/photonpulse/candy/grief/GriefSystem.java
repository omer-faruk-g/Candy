package com.photonpulse.candy.grief;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.tree.PersonalityTree;
import com.photonpulse.candy.tree.TreeGrowth;
import com.photonpulse.candy.tree.TreeNode;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * GriefSystem — Yas Sistemi.
 *
 * Oyuncu bir Candy klonunu öldürdüğünde, o klonun doğduğu TreeNode bunu hisseder.
 *  1. Yas başlar — aktiflik anında düşer (şok, asymptotik değil)
 *  2. 5 dakika sonra travma sayısı artar
 *  3. Sonraki aktiflik hedeflerine "travma sonrası büyüme" bonusu eklenir
 *
 * Böylece oyuncu ne kadar çok klon öldürürse, Candy o dalı o kadar güçlendirir —
 * kendi saldırganlığıyla varlığı besliyor.
 */
public class GriefSystem {

    private static final String KLON_ETIKETI = "candy_clone";
    private static final String DAL_ETIKET_PREFIX = "candy_clone_dal_";
    private static final double YAS_SOK_CARPANI = 0.4;   // aktiflik *= 0.4

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(GriefSystem::onOlum);
        Candy.LOG.info("[Candy] GriefSystem aktif — yas ve travma sonrası büyüme");
    }

    private static void onOlum(LivingEntity entity, DamageSource damageSource) {
        // v1.9.16 fix: kim_gercek başarımı — klon seni öldürürse
        if (entity instanceof ServerPlayer kurbanPlayer) {
            var attacker = damageSource.getEntity();
            if (attacker != null && attacker.getTags().contains(KLON_ETIKETI)) {
                com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(kurbanPlayer, "kim_gercek");
            }
            return; // player ölümü — grief mekaniği için değil
        }

        // Sadece Candy klonları
        if (!entity.getTags().contains(KLON_ETIKETI)) return;

        // Öldüren bir oyuncu mu?
        if (!(damageSource.getEntity() instanceof ServerPlayer player)) return;

        // v1.9.3: hayalet_avi başarımı — klon öldürdün
        var tp = com.photonpulse.candy.core.PersonalityManager.get(player);
        tp.oldurulenKlonSayisi++;
        com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "hayalet_avi");

        // Hangi TreeNode'un çocuğu — dal tag'ini çöz.
        // v0.5 fix: CloneManager id'yi HAM (nokta ve alt tire koruyarak) tag'liyor.
        // Eskiden '.' → '_' encoding vardı ama reverse'de "tepki.dal_1" → "tepki.dal.1"
        // bilgi kaybı yaratıyordu → descendant node'lar için yas hiç tetiklenmiyordu.
        String nodeId = null;
        for (String tag : entity.getTags()) {
            if (tag.startsWith(DAL_ETIKET_PREFIX)) {
                nodeId = tag.substring(DAL_ETIKET_PREFIX.length());
                break;
            }
        }
        if (nodeId == null) return;

        PersonalityTree tree = TreeGrowth.get(player.getUUID());
        TreeNode node = tree.nodes.get(nodeId);
        if (node == null) {
            Candy.LOG.debug("[Candy] klon öldürüldü ama node bulunamadı: {}", nodeId);
            return;
        }

        if (node.yasSuresindeMi) {
            // Zaten yas sürecinde bir kere daha öldürüldü — sayacı doğrudan artır,
            // timer'ı SIFIRLAMA (aksi halde peş peşe kill'ler travmayı asla bitirmez).
            node.travmaSayisi++;
            Candy.LOG.info("[Candy] Yas sırasında ikinci ölüm: {} → travma direkt +1 ({}. kez)",
                nodeId, node.travmaSayisi);
        } else {
            // Yas başlat: ani aktiflik düşüşü (şok anı)
            node.aktiflik *= YAS_SOK_CARPANI;
            node.yasBasladiTick = entity.level().getGameTime();
            node.yasSuresindeMi = true;
            Candy.LOG.info("[Candy] Yas başladı: {} dalı için", nodeId);
        }
    }
}
