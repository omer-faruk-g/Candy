package com.photonpulse.candy.beyin;

import java.util.SplittableRandom;

/**
 * Hakem — on bir bağımsız görüşten <b>tek bir eylem</b> çıkaran karar organı.
 *
 * <h3>NEDEN OYLAMA YETMEZ</h3>
 * Basit çoğunluk, on bir üyeyi eşit sayar. Ama üyeler eşit değildir: kimi kalibre, kimi
 * emin ama yanılgın, kimi yeni ve sicilsiz. Dahası çoğunluk, <i>ne kadar</i> tercih
 * edildiğini atar — 6'ya 5 bir sonuç ile 11'e 0 bir sonuç aynı görünür. Bu ikisini de
 * kaybetmemek için hakem dört adımdan geçer:
 *
 * <pre>
 *   1) ARAŞTIR   — her üyenin ÖLÇÜLEN sicili ve kalibrasyonu ağırlığa çevrilir
 *   2) TOPLA     — ağırlıklı ortalama değer + ağırlıklı anlaşmazlık (yayılım)
 *   3) SİMÜLE ET — üyelerin görüşünden örnekleyerek "hangi eylem kazanır?" dağılımı
 *   4) SEÇ       — anlaşma varsa en iyi değer, yoksa en çok BİLGİ getiren eylem
 * </pre>
 *
 * <h3>3. ADIM NEDEN GEREKLİ</h3>
 * Ortalama değeri en yüksek eylem, en iyi eylem olmayabilir. A eyleminin ortalaması 0,80
 * ama yayılımı 0,40; B'nin ortalaması 0,75 ama yayılımı 0,05 ise — B'yi seçmek çoğu
 * durumda daha iyidir, çünkü A'nın 0,80'i "kimse emin değil" demektir. Monte Carlo
 * örneklemesi bunu doğrudan ölçer: her eylem için <i>en iyi çıkma olasılığını</i> verir,
 * yani belirsizlik hesaba katılmış hâlde. 6 eylem × {@value #ORNEK} örnek — mikrosaniyeler.
 *
 * <h3>4. ADIMDAKİ ÇATAL</h3>
 * Anlaşmazlık yüksekse komite <i>bilmiyordur</i>. O anda en yüksek değeri kovalamak,
 * gürültüyü kovalamaktır. Bunun yerine üyeleri en çok AYIRAN eylem seçilir: sonucu ne
 * olursa olsun, on saniye sonra hangi üyelerin haklı olduğu öğrenilir ve siciller
 * keskinleşir. Bu, modun mevcut "DENEY modu / Denek 239" fikrinin komite düzeyindeki
 * karşılığıdır — Candy bilmediğini fark ettiğinde deney yapar.
 */
public class Hakem {

    /** Monte Carlo örnek sayısı. */
    public static final int ORNEK = 96;
    /** Anlaşmazlık bu eşiğin üstündeyse keşif moduna geçilir. */
    private static final double KESIF_ESIGI = 0.55;

    /**
     * v8: tohum artık SABİT DEĞİL.
     *
     * <p>Eskiden her hakem {@code 0x5EED} ile tohumlanıp hiç yeniden tohumlanmıyordu:
     * sunucu her açılışta Monte Carlo'nun BİREBİR aynı çekiliş dizisini üretiyor, yani
     * "rastgele" karar dizisi yeniden başlatmalarda tekrarlıyordu. Hakem örneği başına
     * farklı tohum bunu kapatır ve oyuncular arasında da bağımsızlık verir.</p>
     */
    private final SplittableRandom rng;

    /** Karar tamponları (v8) — karar başına 4 dizi tahsisini kaldırır. */
    private double[] ortT = new double[0];
    private double[] yayilimT = new double[0];
    private double[] cekilisT = new double[0];
    private int[] kazanmaT = new int[0];

    /** Hakemin kendi karnesi — komite tek üyeden iyi mi, ÖLÇÜLÜR. */
    public final UyeSicili sicil = new UyeSicili();

    public Hakem() {
        this(System.nanoTime() ^ (System.identityHashCode(new Object()) * 0x9E3779B97F4A7C15L));
    }

    public Hakem(long tohum) {
        this.rng = new SplittableRandom(tohum);
    }

    /** Hakemin bir nabızdaki nihai kararı ve gerekçesi. */
    public static class Sonuc {
        public int eylem = -1;
        /** Komitenin bu eyleme dair topluca ne kadar emin olduğu (0..1). */
        public double kurulGuveni;
        /** Üyeler ne kadar hemfikir (0 = tam ayrışma, 1 = tam uzlaşı). */
        public double anlasma;
        /** Her eylemin "en iyi çıkma" olasılığı — simülasyonun çıktısı. */
        public double[] olasilik;
        /**
         * Hakemin SEÇTİĞİ eylem için kendi beklenen değeri (ağırlıklı ortalama).
         *
         * Karnesi bununla tutulur. İlk sürüm hakemi ham sezgiselin değeriyle
         * puanlıyordu — yani vermediği bir tahminden sorumlu tutuluyordu; üyeler kendi
         * tahminleriyle ölçülürken hakem başkasınınkiyle ölçülünce "kurul tek üyeden
         * iyi mi" karşılaştırması geçersiz oluyordu.
         */
        public double beklenenDeger;
        /** Kaç üye bu eylemi seçmişti. */
        public int destekleyen;
        /** Kaç üye geçerli görüş bildirdi. */
        public int gecerliUye;
        /** Anlaşmazlık yüksekti, bilgi kazancı için seçildi. */
        public boolean kesifModu;
        /** En ağır basan üye. */
        public int baskinUye = -1;
        /** İnsan okunur açıklama. */
        public String gerekce = "-";
    }

    /**
     * Kararı verir.
     *
     * @param kararlar üyelerin görüşleri (null öğeler atlanır)
     * @param sezgisel el yazımı sezgiselin eylem değerleri — komite susarsa taban budur
     * @param yasakli  emekliye ayrılmış eylemler (null olabilir)
     */
    public Sonuc karar(Karar[] kararlar, double[] sezgisel, boolean[] yasakli) {
        return karar(kararlar, sezgisel, yasakli, 0.0);
    }

    /**
     * Kararı verir, <b>öfke</b> keşif eşiğini gevşeterek.
     *
     * @param ofke 0..1 — {@code TruePersonality.candyOfke}. Yükseldikçe keşif modu daha
     *             ERKEN tetiklenir, yani "en belirsiz eylem kazanır" dalına daha sık
     *             girilir. Şiddeti değil BELİRSİZLİĞİ büyütür: oyuncu Candy'nin
     *             cümlelerini yok saydıkça Candy'nin ne yapacağı daha az tahmin edilir
     *             olur. 0,55 → en fazla 0,30'a iner (tam öfkede bile uzlaşı hâlâ kazanır,
     *             yoksa komite tamamen kendi ölçümünü yok sayan bir kumarbaza dönerdi).
     */
    public Sonuc karar(Karar[] kararlar, double[] sezgisel, boolean[] yasakli, double ofke) {
        final int E = sezgisel.length;
        Sonuc s = new Sonuc();
        s.olasilik = new double[E];

        // ── 1) ARAŞTIR: ölçülen sicilden ağırlık ──
        double agirlikToplam = 0;
        int gecerli = 0;
        double enAgir = -1;
        for (Karar k : kararlar) {
            if (k == null || k.gecersiz || k.deger == null || k.deger.length != E) continue;
            double a = k.agirlik();
            if (a <= 0) continue;
            agirlikToplam += a;
            gecerli++;
            if (a > enAgir) { enAgir = a; s.baskinUye = k.uye; }
        }
        s.gecerliUye = gecerli;

        // Komite susuyorsa (taze sunucu, tüm üyeler hazırlanıyor) sezgisele düş.
        if (gecerli == 0 || agirlikToplam <= 0) {
            s.eylem = enIyiSerbest(sezgisel, yasakli);
            s.beklenenDeger = (s.eylem >= 0) ? sezgisel[s.eylem] : 0.0;
            s.anlasma = 0;
            s.kurulGuveni = 0;
            s.gerekce = "kurul sessiz — sezgisel taban";
            return s;
        }

        // ── 2) TOPLA: ağırlıklı ortalama ve yayılım ──
        //
        // v8: dört tampon karar başına yeniden tahsis ediliyordu. Hakem örneği bir
        // oyuncuya aittir ve karar() yalnız ana thread'den çağrılır, bu yüzden örnek
        // alanları yeterli. Birikimli olanlar SIFIRLANMAK zorunda (taze dizi 0 veriyordu);
        // cekilis her örnekte baştan yazıldığı için sıfırlanmıyor.
        if (ortT.length != E) {
            ortT = new double[E];
            yayilimT = new double[E];
            cekilisT = new double[E];
            kazanmaT = new int[E];
        }
        final double[] ort = ortT;
        final double[] yayilim = yayilimT;
        java.util.Arrays.fill(ort, 0.0);
        java.util.Arrays.fill(yayilim, 0.0);
        for (Karar k : kararlar) {
            if (k == null || k.gecersiz || k.deger == null || k.deger.length != E) continue;
            double a = k.agirlik();
            if (a <= 0) continue;
            for (int e = 0; e < E; e++) ort[e] += a * k.deger[e];
        }
        for (int e = 0; e < E; e++) ort[e] /= agirlikToplam;
        for (Karar k : kararlar) {
            if (k == null || k.gecersiz || k.deger == null || k.deger.length != E) continue;
            double a = k.agirlik();
            if (a <= 0) continue;
            for (int e = 0; e < E; e++) {
                double d = k.deger[e] - ort[e];
                yayilim[e] += a * d * d;
            }
        }
        for (int e = 0; e < E; e++) {
            yayilim[e] = Math.sqrt(Math.max(0.0, yayilim[e] / agirlikToplam));
        }

        // ── 3) SİMÜLE ET: "hangi eylem en iyi çıkar?" olasılık dağılımı ──
        final int[] kazanma = kazanmaT;
        final double[] cekilis = cekilisT;
        java.util.Arrays.fill(kazanma, 0);
        for (int t = 0; t < ORNEK; t++) {
            for (int e = 0; e < E; e++) {
                if (yasakli != null && e < yasakli.length && yasakli[e]) {
                    cekilis[e] = Double.NEGATIVE_INFINITY;
                    continue;
                }
                // Ağırlıklı bir üyenin görüşünü çek, komitenin yayılımı kadar gürültüle.
                Karar k = agirlikliSec(kararlar, agirlikToplam, E);
                double taban = (k == null) ? ort[e] : k.deger[e];
                cekilis[e] = taban + gauss() * yayilim[e];
            }
            int en = 0;
            for (int e = 1; e < E; e++) if (cekilis[e] > cekilis[en]) en = e;
            if (cekilis[en] > Double.NEGATIVE_INFINITY) kazanma[en]++;
        }
        for (int e = 0; e < E; e++) s.olasilik[e] = kazanma[e] / (double) ORNEK;

        // ── ANLAŞMA: olasılık dağılımının entropisi ──
        double h = 0;
        for (int e = 0; e < E; e++) {
            double p = s.olasilik[e];
            if (p > 1e-9) h -= p * Math.log(p);
        }
        s.anlasma = 1.0 - Math.min(1.0, h / Math.log(E));
        if (!Double.isFinite(s.anlasma)) s.anlasma = 0;

        // ── 4) SEÇ ──
        final double o = Double.isFinite(ofke) ? Math.max(0.0, Math.min(1.0, ofke)) : 0.0;
        final double esik = KESIF_ESIGI - 0.25 * o;
        if (s.anlasma < 1.0 - esik) {
            // Komite bilmiyor → üyeleri en çok AYIRAN eylemi seç (bilgi kazancı)
            s.kesifModu = true;
            int en = -1;
            double enYayilim = -1;
            for (int e = 0; e < E; e++) {
                if (yasakli != null && e < yasakli.length && yasakli[e]) continue;
                if (yayilim[e] > enYayilim) { enYayilim = yayilim[e]; en = e; }
            }
            s.eylem = (en >= 0) ? en : enIyiSerbest(sezgisel, yasakli);
        } else {
            int en = -1;
            double enP = -1;
            for (int e = 0; e < E; e++) {
                if (yasakli != null && e < yasakli.length && yasakli[e]) continue;
                if (s.olasilik[e] > enP) { enP = s.olasilik[e]; en = e; }
            }
            s.eylem = (en >= 0) ? en : enIyiSerbest(sezgisel, yasakli);
        }

        // ── KAZANANIN LANETİ DÜZELTMESİ ──
        // Hakem, gürültülü on bir tahminin ARGMAX'ını seçer. Argmax seçmek, seçilen
        // tahmini sistematik olarak YUKARI saptırır: N seçenekten en yükseğinin beklenen
        // değeri, gerçek en yükseğin üstündedir (N=6 için ~1,3 standart sapma). Ham
        // ort[secilen]'i beklenti olarak ilan etmek, hakemi kalibrasyonsuz bir iyimsere
        // çevirir — ve bu ÖLÇÜLDÜ: hakem tutarlı biçimde ortalama üyeden kötü puan
        // alıyordu, çünkü sürekli fazla iyimser tahmin ediyordu.
        //
        // Düzeltme, seçilen eylemin beklentisini kendi yayılımı kadar kısar. Yayılım
        // sıfırsa (tam uzlaşı) düzeltme de sıfırdır — seçim gürültüden gelmiyorsa
        // sapma da yoktur.
        if (s.eylem >= 0 && s.eylem < E) {
            double genelOrt = 0;
            int sayilan = 0;
            for (int e = 0; e < E; e++) {
                if (yasakli != null && e < yasakli.length && yasakli[e]) continue;
                genelOrt += ort[e];
                sayilan++;
            }
            genelOrt = (sayilan == 0) ? ort[s.eylem] : genelOrt / sayilan;
            double duzeltilmis = ort[s.eylem] - 0.6 * yayilim[s.eylem];
            // Genel ortalamanın altına inme: düzeltme bir cezalandırma değil, sapma giderme.
            s.beklenenDeger = Math.max(genelOrt, duzeltilmis);
        }
        for (Karar k : kararlar) {
            if (k != null && !k.gecersiz && k.eylem == s.eylem) s.destekleyen++;
        }
        s.kurulGuveni = Math.max(0.0, Math.min(1.0,
            s.olasilik[s.eylem] * (0.4 + 0.6 * (gecerli / (double) Math.max(1, kararlar.length)))));

        s.gerekce = String.format("%d/%d üye · olasılık %.0f%% · anlaşma %.0f%%%s",
            s.destekleyen, gecerli, s.olasilik[s.eylem] * 100, s.anlasma * 100,
            s.kesifModu ? " · KEŞİF (kurul ayrıştı)" : "");
        return s;
    }

    /** Ağırlıkla orantılı bir üye çeker (roulette-wheel). */
    private Karar agirlikliSec(Karar[] kararlar, double toplam, int E) {
        double hedef = rng.nextDouble() * toplam;
        double birikim = 0;
        for (Karar k : kararlar) {
            if (k == null || k.gecersiz || k.deger == null || k.deger.length != E) continue;
            double a = k.agirlik();
            if (a <= 0) continue;
            birikim += a;
            if (birikim >= hedef) return k;
        }
        return null;
    }

    /** Box-Muller yerine ucuz yaklaşım: 4 düzgün örneğin toplamı ≈ normal. */
    private double gauss() {
        double t = 0;
        for (int i = 0; i < 4; i++) t += rng.nextDouble();
        return (t - 2.0) * 0.87;
    }

    private static int enIyiSerbest(double[] deger, boolean[] yasakli) {
        int en = -1;
        double enY = Double.NEGATIVE_INFINITY;
        for (int e = 0; e < deger.length; e++) {
            if (yasakli != null && e < yasakli.length && yasakli[e]) continue;
            if (deger[e] > enY) { enY = deger[e]; en = e; }
        }
        return en;
    }
}
