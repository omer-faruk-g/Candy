package com.photonpulse.candy.psikoloji;

import com.photonpulse.candy.core.TruePersonality;

/**
 * Ozguven — Candy'nin oyuncudan aldığı korku/kaçma sinyalleri ile artar.
 *
 * Kaynaklar (event-driven, TruePersonality.candyOzguven güncellenir):
 *  - Sprint (kaçma) → küçük artış
 *  - Sneak + hareket → küçük artış
 *  - Ani yön değişimi (panik hareket) → orta artış
 *  - Aynı yerde ileri geri (köşe sıkışması) → orta artış
 *
 * Decay:
 *  - Yüzeyde + gündüz + sakin → yavaş azalır
 *
 * Etki: Psikoloji.agresivlikCarpani üzerine EKLENiR (+0 → +2).
 * Yüksek özgüven = Candy tereddütsüz saldırır → tüm olaylar 2-3x sıklaşır.
 */
public class Ozguven {

    public static void artir(TruePersonality tp, double miktar) {
        if (tp == null) return;
        tp.candyOzguven = Math.min(100, tp.candyOzguven + miktar);
    }

    public static void azalt(TruePersonality tp, double miktar) {
        if (tp == null) return;
        tp.candyOzguven = Math.max(0, tp.candyOzguven - miktar);
    }

    /**
     * Özgüven çarpanı: 1.0 (sıfır) → 3.0 (max özgüven).
     * Psikoloji.agresivlikCarpani içine EKLENiR (kombinasyon yapılır).
     */
    public static double carpani(TruePersonality tp) {
        if (tp == null) return 1.0;
        return 1.0 + (Math.max(0, Math.min(100, tp.candyOzguven)) / 100.0) * 2.0;
    }
}
