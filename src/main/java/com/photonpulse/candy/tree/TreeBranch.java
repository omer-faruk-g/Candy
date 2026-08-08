package com.photonpulse.candy.tree;

import java.util.HashMap;
import java.util.Map;

/**
 * TreeBranch — 3 sabit kök dal.
 *
 * Bunların altındaki alt dallar tamamen dinamik olarak büyür.
 * Kökler silinmez, hibernasyona giremez.
 *
 * Her kök farklı sistem katmanına hizmet eder:
 *  IZLEME     → tabela/klon nerede yerleştirilecek
 *  ETKILESIM  → tabela yazı tarzı, mesaj tonu
 *  TEPKI      → klon agresifliği, cooldown, saldırı davranışı
 */
public enum TreeBranch {

    IZLEME("izleme", "İzleme kökü"),
    ETKILESIM("etkilesim", "Etkileşim kökü"),
    TEPKI("tepki", "Tepki kökü");

    public final String id;
    public final String isim;

    TreeBranch(String id, String isim) {
        this.id = id;
        this.isim = isim;
    }

    /**
     * Kökün başlangıç trait'i.
     * Bu değerler oyunla birlikte HER KÖK için ayrı ayrı kayabilir.
     * (Kök trait'leri sabit değil — Candy zamanla dinlediklerini değiştirebilir.)
     */
    public Map<String, Double> defaultTrait() {
        Map<String, Double> t = new HashMap<>();
        switch (this) {
            case IZLEME -> {
                t.put("kesfetOrani", 0.6);
                t.put("donOrani", 0.3);
                t.put("kacOrani", 0.4);
            }
            case ETKILESIM -> {
                t.put("aciklik", 0.7);
                t.put("inkarEgilimi", -0.4);
                t.put("kesfetmeIstegi", 0.5);
            }
            case TEPKI -> {
                t.put("saldirOrani", 0.5);
                t.put("panikEsigi", 0.4);
                t.put("kacOrani", -0.3);
            }
        }
        return t;
    }
}
