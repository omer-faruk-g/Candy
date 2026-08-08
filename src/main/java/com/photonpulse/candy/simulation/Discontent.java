package com.photonpulse.candy.simulation;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.tree.PersonalityTree;
import com.photonpulse.candy.tree.TreeGrowth;
import com.photonpulse.candy.tree.TreeNode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Discontent — Candy'nin memnuniyetsizlik motoru.
 *
 * Her ~1000 tick'te bir ana ağacın ortalama bilgisini ölçer:
 *  - Artış küçükse → memnuniyetsizlik yükselir
 *  - Artış yeterliyse → memnuniyetsizlik asymptotik olarak 0'a doğru düşer
 *
 * memnuniyetsizlik > BASTIRMA_ESIK → Bastirma denemesi
 * memnuniyetsizlik >= SHADOWTREE_ESIK → ShadowTree doğar (bir defalık)
 */
public class Discontent {

    private static final int TICK_ARALIGI = 700;              // 35 sn (eski 50 sn)
    private static final double YETERLI_ARTIS_ESIK = 0.01;
    private static final double MEMNUNIYETSIZLIK_ARTISI = 6.0; // Daha hızlı birikim
    private static final double BASTIRMA_ESIK = 40.0;
    private static final double SHADOWTREE_ESIK = 70.0;        // Eski 80 — daha erken ShadowTree

    // Oyuncu başına önceki ölçüm — restart sonrası kaybolabilir, sorun değil
    private static final Map<UUID, Double> oncekiBilgiOrt = new HashMap<>();
    private static int tickSayaci = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Discontent::onTick);
        Candy.LOG.info("[Candy] Discontent aktif");
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI != 0) return;

        long simdi = server.getOverworld().getTime();
        for (Map.Entry<UUID, TruePersonality> e : PersonalityManager.tumOyuncular().entrySet()) {
            UUID uid = e.getKey();
            TruePersonality tp = e.getValue();
            if (!tp.kurulumTamam) continue;

            PersonalityTree tree = TreeGrowth.get(uid);
            double bilgiOrt = bilgiOrtalamasi(tree);
            Double onceki = oncekiBilgiOrt.get(uid);
            oncekiBilgiOrt.put(uid, bilgiOrt);

            if (onceki == null) continue;   // ilk ölçüm — baseline

            double artis = bilgiOrt - onceki;
            if (artis < YETERLI_ARTIS_ESIK) {
                tp.memnuniyetsizlik = Math.min(100, tp.memnuniyetsizlik + MEMNUNIYETSIZLIK_ARTISI);
            } else {
                tp.memnuniyetsizlik = TruePersonality.kayma(tp.memnuniyetsizlik, 0.0, 0.05);
            }

            // Aşama C önce: eşik aşıldıysa ShadowTree doğar (bir defalık)
            // NOT: Bastirma öncesinde çünkü BASTIRMA_ESIK ile aynı hızda azaltıyor,
            //     sıra tersse ShadowTree eşiği asla geçilemez ("stuck at 75" tuzağı).
            if (tp.memnuniyetsizlik >= SHADOWTREE_ESIK
                && ShadowTreeManager.getIfExists(uid) == null) {
                ShadowTreeManager.dogur(uid, tree, simdi);
                // Doğduktan sonra memnuniyetsizliği yarıya indir — "bir çıkış buldu"
                tp.memnuniyetsizlik = tp.memnuniyetsizlik * 0.5;
            }
            // Aşama B: bastırma denemesi (ShadowTree fırsatı kaçırılmadan)
            else if (tp.memnuniyetsizlik > BASTIRMA_ESIK) {
                SimulatedPersonality sp = SimulationManager.get(uid);
                Bastirma.dene(tp, tree, sp);
            }
        }
    }

    private static double bilgiOrtalamasi(PersonalityTree tree) {
        if (tree == null || tree.nodes == null || tree.nodes.isEmpty()) return 0;
        double toplam = 0;
        int say = 0;
        for (TreeNode n : tree.nodes.values()) {
            toplam += n.bilgi;
            say++;
        }
        return say == 0 ? 0 : toplam / say;
    }

    public static void oyuncuAyrildi(UUID uuid) {
        oncekiBilgiOrt.remove(uuid);
    }
}
