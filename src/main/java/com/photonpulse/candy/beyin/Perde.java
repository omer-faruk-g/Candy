package com.photonpulse.candy.beyin;

import com.photonpulse.candy.core.TruePersonality;

/**
 * Perde (v8.2) — olay sınıflarını <b>ölçülen kalibrasyonla</b> açar.
 *
 * <h3>NİYE GÜN SAYISIYLA DEĞİL</h3>
 * The Mimic altı kademesini "hayatta kalınan gün"e bağlıyor. Candy'de bu yanlış olurdu:
 * modun bütün değeri emergent olmasında — takvime bağlı bir tırmanma, 11 beyinli Kurul'u
 * süslemeye indirger. Oyuncu bir hafta AFK kalsa "kademe 4" olurdu.
 *
 * <p>Burada kapıyı açan şey Kurul'un <b>kendi başarısı</b>: üyelerin ortalama Brier
 * kalibrasyonu. Candy oyuncuyu doğru tahmin etmeye başlayınca yeni mekaniklere erişir.
 * Hem bir yay var (kademeler sırayla açılıyor) hem emergent kalıyor (ne zaman açılacağı
 * oyuncunun ne kadar okunabilir olduğuna bağlı).</p>
 *
 * <h3>NİYE GERİ DÜŞMÜYOR</h3>
 * Kalibrasyon dalgalanır (oyuncu taktik değiştirir, Brier düşer). Kademe kalibrasyonu
 * doğrudan izlerse mekanikler açılıp kapanır ve oyuncu bunu "rastgele" olarak okur —
 * tırmanmanın anlamı kaybolur. Bir kez kazanılan hak kalır: {@link TruePersonality#perdeKademe}
 * yalnızca artar.
 *
 * <h3>ÖLÇÜT NEREDE TANIMLI</h3>
 * {@link UyeSicili#kalibrasyon()} — Brier tabanlı ve <i>uygun</i> (proper) bir puanlama
 * kuralı. İsabet oranı kullanılmıyor çünkü uygunsuz: üye tahminine varyans ekleyip
 * yükseltebilir. Ölçüt birliği kasıtlı.
 */
public final class Perde {

    private Perde() {}

    /** Kademe eşikleri — ortalama kalibrasyon bunu geçerse kademe kazanılır. */
    private static final double[] ESIKLER = {0.56, 0.64, 0.72};

    /** Kanıt eşiği: üye bu kadar tahmin yapmadan kalibrasyonu anlamlı değil. */
    private static final long MIN_TAHMIN = 40L;

    /** En yüksek kademe. */
    public static final int MAKS_KADEME = 3;

    /**
     * Kurulun ölçülen ortalama kalibrasyonu (0..1), ya da kanıt yoksa 0,5.
     *
     * <p>Yalnızca {@link #MIN_TAHMIN} tahmine ulaşmış üyeler sayılır. Yeni açılmış
     * sunucuda bütün siciller 0,5 döner ({@code kalibrasyon()} kanıt yokken böyle
     * yapıyor) ve o 0,5'lerin ortalaması ilk eşiği neredeyse geçecekti — kademe
     * hiçbir şey öğrenilmeden açılırdı.</p>
     */
    public static double kalibrasyon(Kurul kurul) {
        if (kurul == null) return 0.5;
        Beyin[] uyeler = kurul.uyeler();
        if (uyeler == null) return 0.5;
        double toplam = 0.0;
        int n = 0;
        for (Beyin b : uyeler) {
            if (b == null || b.sicil == null) continue;
            if (b.sicil.tahmin < MIN_TAHMIN) continue;
            toplam += b.sicil.kalibrasyon();
            n++;
        }
        if (n == 0) return 0.5;
        double o = toplam / n;
        return Double.isFinite(o) ? Math.max(0.0, Math.min(1.0, o)) : 0.5;
    }

    /**
     * Kademeyi günceller (yalnızca artar) ve güncel kademeyi döner.
     */
    public static int kademeGuncelle(TruePersonality tp, Kurul kurul) {
        if (tp == null) return 0;
        if (tp.perdeKademe < 0) tp.perdeKademe = 0;
        if (tp.perdeKademe >= MAKS_KADEME) return MAKS_KADEME;

        double k = kalibrasyon(kurul);
        int hedef = 0;
        for (double esik : ESIKLER) {
            if (k >= esik) hedef++;
        }
        if (hedef > tp.perdeKademe) tp.perdeKademe = Math.min(MAKS_KADEME, hedef);
        return tp.perdeKademe;
    }
}
