package com.photonpulse.candy.core;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;

/**
 * Psikoloji — oyuncunun kalıcı ruh sağlığı state'i.
 *
 * v0.9+: state-based. Skor doğrudan hesaplanmıyor, `TruePersonality.psikolojiKotulugu`
 * alanından okunuyor. PsikolojiTakip sınıfı bu değeri günceller:
 *
 *   KÖTÜLEŞTİRİCİLER:
 *     - Uzun süre mağarada  → +0.10/sn
 *     - Mob'dan damage yeme → +2.0/hit
 *     - Yüksek korku (>70)  → +0.02/sn
 *
 *   İYİLEŞTİRİCİLER (sadece 30 sn duraklama sonrası):
 *     - Elmas kazma        → -3.0/blok
 *     - Çiçek koparma      → -1.0/blok
 *     - Yüzeyde + gündüz   → -0.05/sn
 *
 * Skor 0-100 arası. Agresivlik çarpanı 1.0-2.5x — Candy'nin müdahale sıklığını modüle eder.
 */
public class Psikoloji {

    /**
     * 0-100 arası kötü psikoloji skoru. Yüksek = kırılgan.
     * Artık state'ten okunuyor — PsikolojiTakip günceller.
     */
    public static double kotulukSkoru(TruePersonality tp) {
        if (tp == null) return 0;
        return Math.max(0, Math.min(100, tp.psikolojiKotulugu));
    }

    /**
     * Agresivlik çarpanı: 1.0 (sağlıklı) → 2.5x (dibe vurmuş) → 5.0x (dibe + max özgüven).
     * v1.3: Candy'nin özgüveni ek çarpan olarak eklenir (+0 → +2).
     * Cap: 5.0x — teorik max.
     */
    public static double agresivlikCarpani(TruePersonality tp) {
        double k = kotulukSkoru(tp);
        double base = 1.0 + (k / 100.0) * 1.5;              // 1.0 → 2.5
        double ozguvenBonus = (tp == null ? 0.0
            : Math.max(0, Math.min(100, tp.candyOzguven)) / 100.0) * 2.0;   // +0 → +2
        return Math.min(5.0, base + ozguvenBonus);
    }

    /**
     * Grup agresivlik çarpanı (v1.4): Player'ın yakınında (24 blok) başka player'lar
     * varsa hepsinin kotuluk ortalaması alınır. Bir kişi paniklerse takım da etkilenir.
     *
     * "Bir oyuncu paniklerse takım daha çok görür."
     */
    public static double grupAgresivlikCarpani(ServerPlayerEntity player) {
        TruePersonality tpMain = PersonalityManager.get(player);
        double toplam = kotulukSkoru(tpMain);
        double ozguvenToplam = tpMain.candyOzguven;
        int sayi = 1;

        Box arama = new Box(player.getBlockPos()).expand(24);
        for (var diger : player.getWorld().getEntitiesByClass(
                ServerPlayerEntity.class, arama, p -> p != player)) {
            TruePersonality tpD = PersonalityManager.get(diger);
            if (!tpD.kurulumTamam) continue;
            toplam += kotulukSkoru(tpD);
            ozguvenToplam += tpD.candyOzguven;
            sayi++;
        }

        double ortalamaKotuluk = toplam / sayi;
        double ortalamaOzguven = ozguvenToplam / sayi;

        double base = 1.0 + (ortalamaKotuluk / 100.0) * 1.5;
        double ozguvenBonus = (Math.max(0, Math.min(100, ortalamaOzguven)) / 100.0) * 2.0;
        double result = Math.min(5.0, base + ozguvenBonus);

        // Grup bonusu: 2+ oyuncu → ekstra %10 (birlikte hareket = daha çok görünme)
        if (sayi > 1) result = Math.min(5.0, result * 1.1);
        return result;
    }
}
