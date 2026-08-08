package com.photonpulse.candy.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PersonalityTree — bir oyuncu için Candy'nin kişilik ağacı.
 *
 * Node'lar flat map'te tutulur (id → node), parent-child ilişkisi id ile.
 * Gson serialization için basit yapı.
 *
 * initEt() 3 sabit kökü oluşturur. Alt dallar dinamik oluşur (TreeGrowth).
 */
public class PersonalityTree {

    public UUID oyuncuId;
    public Map<String, TreeNode> nodes = new HashMap<>();

    // TruePersonality'de dinlenebilecek alanların listesi
    // (mutasyon yeni trait eklerken buradan seçer)
    public static final List<String> ALANLAR = List.of(
        "kacOrani", "saldirOrani", "donOrani", "kesfetOrani",
        "aciklik", "inkarEgilimi", "kesfetmeIstegi", "panikEsigi"
    );

    public PersonalityTree() {}

    /**
     * Sabit 3 kökü oluşturur (yoksa). Idempotent — yükleme sonrası da güvenle çağrılabilir.
     */
    public void initEt() {
        for (TreeBranch dal : TreeBranch.values()) {
            if (!nodes.containsKey(dal.id)) {
                TreeNode kok = new TreeNode();
                kok.id = dal.id;
                kok.isim = dal.isim;
                kok.parentId = null;
                kok.trait = new HashMap<>(dal.defaultTrait());
                kok.aktiflik = 0.5;   // kökler her zaman biraz aktif
                nodes.put(dal.id, kok);
            }
        }
    }

    /**
     * Belirli bir kök dalın "en aktif alt dalını" bulur.
     * Alt dal yoksa kökün kendisini döner (kökler her zaman erişilebilir).
     * Hibernasyondaki node'lar dışlanır.
     */
    public TreeNode baskinAlt(TreeBranch dal) {
        TreeNode kok = nodes.get(dal.id);
        if (kok == null) return null;

        TreeNode enaktif = kok;
        double maxA = kok.aktiflik * 0.5;   // kökün eşiği düşük — çocuklar öne çıkabilsin

        for (String cid : kok.childIds) {
            TreeNode child = nodes.get(cid);
            if (child == null || child.hibernation) continue;
            if (child.aktiflik > maxA) {
                maxA = child.aktiflik;
                enaktif = child;
            }
            // İkinci seviye (grandchild) de kontrol edilebilir — şimdilik tek seviye
            for (String gcid : child.childIds) {
                TreeNode gc = nodes.get(gcid);
                if (gc == null || gc.hibernation) continue;
                if (gc.aktiflik > maxA) {
                    maxA = gc.aktiflik;
                    enaktif = gc;
                }
            }
        }
        return enaktif;
    }

    /**
     * Aktif (hibernasyonda olmayan) tüm node'ların listesi. Debug/logging için.
     */
    public List<TreeNode> aktifNodeList() {
        List<TreeNode> r = new ArrayList<>();
        for (TreeNode n : nodes.values()) if (!n.hibernation) r.add(n);
        return r;
    }
}
