package com.photonpulse.candy.beyin;

import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.tree.PersonalityTree;
import com.photonpulse.candy.tree.TreeGrowth;

import java.util.List;

/**
 * BeyinGirdi — beynin TEK duyu giriş noktası: 52 özellik (v8; v4-v7'de 48).
 *
 * Kasıtlı olarak {@link TreeGrowth}'un alan üreticisi yeniden kullanılır; böylece ağaç ile
 * beyin BİREBİR aynı duyu dünyasını görür. İki ayrı toplayıcı yazmak, ikisinin zamanla
 * birbirinden ayrılması demekti (klasik hata kaynağı).
 *
 * Özellik SIRASI {@link PersonalityTree#ALANLAR} listesidir ve {@link Lob#girdiIndeksleri}
 * bu sıraya göre indeksler — sıra değişirse loblar yanlış özellikleri dinler.
 */
public class BeyinGirdi {

    /** Özellik adları = ağacın alan listesi (aynı sıra, aynı normalizasyon). */
    public static final List<String> OZELLIK_ADLARI = PersonalityTree.ALANLAR;

    /**
     * Oyuncunun güncel durumunu {@link Beyin#GIRDI_SAYISI} boyutlu duyu vektörüne
     * çevirir (hepsi 0..1).
     *
     * @param hedef yeniden kullanılan tampon (null ise yeni dizi ayrılır) — tick başına
     *              çöp üretmemek için beynin kendi {@code girdi} dizisi geçilir
     */
    public static float[] topla(TruePersonality tp, float[] hedef) {
        float[] g = (hedef != null && hedef.length == Beyin.GIRDI_SAYISI)
            ? hedef : new float[Beyin.GIRDI_SAYISI];
        if (tp == null) return g;
        // v8: eskiden alanMapi() cagirilip 52 hash aramasi + 52 unboxing yapiliyordu.
        // alanDizisi ayni kaynaktan (TreeGrowth.doldur) dogrudan indeks sirasinda yazar;
        // degerler zaten 0..1 e sinirlanmis ve NaN temizlenmis geliyor.
        return TreeGrowth.alanDizisi(tp, g);
    }

    /** Şema doğrulaması — özellik sayısı beynin beklediğiyle uyuşuyor mu? */
    public static boolean semaGecerli() {
        return OZELLIK_ADLARI.size() == Beyin.GIRDI_SAYISI;
    }
}
