package com.photonpulse.candy.beyin;

/**
 * Karar — <b>tek bir üyenin</b> bir nabızdaki görüşü.
 *
 * Kullanıcının istediği üç şeyi taşır:
 * <pre>
 *   "ne kadar eminim"   → guven      (üyenin KENDİ beyanı)
 *   "neden bu hareket"  → gerekce    (hangi lob sürükledi, hangi duyu)
 *   "kanıt"             → sicil      (ÖLÇÜLEN geçmiş isabet — beyan değil)
 * </pre>
 *
 * <h3>NEDEN GÜVEN İLE KANIT AYRI</h3>
 * Bir beynin kendi kararına ne kadar güvendiği <i>ucuzdur</i>: emin olmak bedava, haklı
 * olmak değil. Bu yüzden hakem güveni tek başına ağırlık olarak kullanmaz;
 * {@link UyeSicili}'nden gelen ÖLÇÜLEN isabet ve kalibrasyonla çarpar. Emin ama sürekli
 * yanılan bir üyenin sesi zamanla kendiliğinden kısılır — ve bunu ona kimse söylemez,
 * ölçüm söyler. Modun ablasyonla ölçülen {@code hayatiOnem} felsefesinin komite düzeyine
 * taşınmış hâlidir.
 */
public class Karar {

    /** Kaçıncı üye (0..n-1). */
    public int uye;
    /** Hangi nabız — hakem yalnızca aynı nabızdan gelen kararları birleştirir. */
    public long nabiz;

    /** Üyenin seçtiği eylem (ZihinselProva.Eylem sırası). */
    public int eylem = -1;
    /** Üyenin her eylem için biçtiği değer (0..~1,5). */
    public float[] deger;

    /** Üyenin kendi beyanı: bu karara ne kadar eminim (0..1). */
    public float guven;

    /** ÖLÇÜLEN sicil: bu üye geçmişte ne kadar isabet etti (0..1). */
    public float sicil;
    /** Kalibrasyon (0..1): eminken haklı mıydı? Brier'dan türetilir, 1 = kusursuz. */
    public float kalibrasyon;

    /** Kararı en çok süren lob. */
    public String surukleyenLob = "-";
    /** O lobun nötrden sapması (-1..1) — "ne kadar bağırdı". */
    public float lobKatkisi;
    /** Kararı en çok süren duyu özelliği. */
    public String surukleyenDuyu = "-";

    /** Üye körelmiş/bilgisiz mi — hakem bunu sıfır ağırlıkla geçer. */
    public boolean gecersiz = false;

    public Karar() {}

    public Karar(int uye, long nabiz) {
        this.uye = uye;
        this.nabiz = nabiz;
    }

    /**
     * Hakemin kullanacağı nihai ağırlık.
     *
     * Çarpımsaldır ve bu kasıtlı: üç etkenden biri sıfıra giderse üye susar. Sicili olmayan
     * (yeni) bir üye 0,5'e çekilir — ne susturulur ne de körü körüne dinlenir.
     */
    public double agirlik() {
        if (gecersiz) return 0.0;
        double g = 0.35 + 0.65 * kirp(guven);          // beyan: en fazla 2 kat fark
        double s = 0.15 + 0.85 * kirp(sicil);          // ölçüm: 6 kata kadar fark
        double k = 0.30 + 0.70 * kirp(kalibrasyon);    // dürüstlük
        double a = g * s * k;
        return Double.isFinite(a) ? Math.max(0.0, a) : 0.0;
    }

    /** İnsan okunur gerekçe — hata ayıklama ve teşhis raporu için. */
    public String gerekce() {
        return String.format("%s %+.2f · %s · emin %.0f%% · sicil %.0f%%",
            surukleyenLob, lobKatkisi, surukleyenDuyu, guven * 100, sicil * 100);
    }

    private static double kirp(double x) {
        if (!Double.isFinite(x)) return 0.5;
        return Math.max(0.0, Math.min(1.0, x));
    }
}
