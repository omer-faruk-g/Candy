package com.photonpulse.candy.simulation;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.tree.PersonalityTree;
import com.photonpulse.candy.tree.TreeNode;

import java.util.Random;

/**
 * Bastirma — Aşama B.
 *
 * memnuniyetsizlik > BASTIRMA_ESIK olduğunda ana PersonalityTree'nin bazı
 * node trait'lerini SimulatedPersonality yönünde küçük bir ağırlıkla karıştırır.
 * Bu Candy'nin memnuniyetsizliği bastırma denemesidir.
 *
 * Başarısız kalırsa (memnuniyetsizlik >= SHADOWTREE_ESIK), Discontent Aşama C'ye geçer.
 */
public class Bastirma {

    private static final double KARISIM_ORANI = 0.08;   // %8 simülasyon → gerçek
    private static final double AZALTMA_MIKTARI = 5.0;
    private static final Random RNG = new Random();

    /**
     * Bastırma denemesi. tree ve sp bilinmesi gerekir, tp memnuniyetsizliği azaltmak için.
     */
    public static void dene(TruePersonality tp, PersonalityTree tree, SimulatedPersonality sp) {
        if (tree == null || tree.nodes == null || tree.nodes.isEmpty()) return;
        if (sp == null) return;

        // Rastgele bir kök-olmayan node seç; yoksa köklerden birini al
        TreeNode hedef = rastgeleKokDisi(tree);
        if (hedef == null) {
            // Kökten birini al (map iterasyon sırası deterministik değil ama kabul edilebilir)
            for (TreeNode n : tree.nodes.values()) { hedef = n; break; }
        }
        if (hedef == null || hedef.trait == null || hedef.trait.isEmpty()) return;

        // Trait'in her alanını SimulatedPersonality'nin karşılık gelen alanıyla harmanla
        for (var entry : hedef.trait.entrySet()) {
            String alan = entry.getKey();
            double mevcut = entry.getValue();
            double simDeger = simAlanOku(sp, alan);
            // Sim değeri -1..+1 aralığına projeksiyon (SimulatedPersonality alanları 0..1)
            double simTrait = (simDeger - 0.5) * 2.0;
            double yeni = mevcut * (1 - KARISIM_ORANI) + simTrait * KARISIM_ORANI;
            entry.setValue(Math.max(-1, Math.min(1, yeni)));
        }

        tp.memnuniyetsizlik = Math.max(0, tp.memnuniyetsizlik - AZALTMA_MIKTARI);
        Candy.LOG.debug("[Candy] bastırma denendi: {} node={}", tp.oyuncuId, hedef.id);
    }

    private static TreeNode rastgeleKokDisi(PersonalityTree tree) {
        java.util.List<TreeNode> aday = new java.util.ArrayList<>();
        for (TreeNode n : tree.nodes.values()) {
            if (!n.kokMu() && !n.hibernation) aday.add(n);
        }
        if (aday.isEmpty()) return null;
        return aday.get(RNG.nextInt(aday.size()));
    }

    private static double simAlanOku(SimulatedPersonality sp, String alan) {
        return switch (alan) {
            case "kacOrani" -> sp.kacOrani;
            case "saldirOrani" -> sp.saldirOrani;
            case "donOrani" -> sp.donOrani;
            case "kesfetOrani" -> sp.kesfetOrani;
            case "aciklik" -> sp.aciklik;
            case "inkarEgilimi" -> sp.inkarEgilimi;
            case "kesfetmeIstegi" -> sp.kesfetmeIstegi;
            case "panikEsigi" -> sp.panikEsigi;
            default -> 0.5;
        };
    }
}
