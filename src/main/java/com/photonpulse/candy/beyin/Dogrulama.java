package com.photonpulse.candy.beyin;

/**
 * Dogrulama — tek bir dünyada beynin oyuncu modelinin ne kadar tuttuğunun kaydı.
 *
 * Kullanıcının isteği: "başka dünyalarda beynin doğruluğunu ölçmek."
 *
 * Makine öğrenmesinde bir modelin GERÇEKTEN öğrenip öğrenmediği, hiç görmediği bir
 * dağılımda test edilerek anlaşılır (held-out validation). Burada "yeni dağılım" =
 * yeni bir Minecraft dünyası. Model yeni dünyada da tutuyorsa Candy DÜNYAYI değil
 * KİŞİYİ öğrenmiştir.
 */
public class Dogrulama {

    /** Dünya kimliği: hash(level adı + seed), 16 hex. */
    public String dunyaId;
    public long girisSupurme;
    public long toplamSupurme;

    public int tahmin = 0;
    public int isabet = 0;
    /** Brier skoru toplamı: Σ(p − sonuç)². 0 = mükemmel, 0.25 = rastgele. */
    public double brierToplam = 0.0;

    /** Kalibrasyon kovaları: %0-10, %10-20 ... güven aralıkları. */
    public int[] kovaSayi = new int[10];
    public double[] kovaGuven = new double[10];
    public int[] kovaIsabet = new int[10];

    /** Bu dünyada model ne kadar genelledi (0..1, 1 = mükemmel transfer). */
    public double genellemeSkoru = 0.5;
    public boolean karariVerildi = false;

    public Dogrulama() {}

    public Dogrulama(String dunyaId, long supurme) {
        this.dunyaId = dunyaId;
        this.girisSupurme = supurme;
    }

    /**
     * Bir tahmini puanla.
     *
     * @param olasilik modelin doğru sınıfa verdiği olasılık
     * @param tuttu    tahmin gerçekten tuttu mu
     */
    public void puanla(double olasilik, boolean tuttu) {
        if (!Double.isFinite(olasilik)) return;
        olasilik = Math.max(0.0, Math.min(1.0, olasilik));
        tahmin++;
        if (tuttu) isabet++;
        double fark = olasilik - (tuttu ? 1.0 : 0.0);
        brierToplam += fark * fark;

        int k = Math.min(9, (int) (olasilik * 10));
        kovaSayi[k]++;
        kovaGuven[k] += olasilik;
        if (tuttu) kovaIsabet[k]++;
    }

    /** Brier skoru (düşük = iyi). Veri yoksa 0.25 (rastgele). */
    public double brier() {
        if (tahmin == 0) return 0.25;
        double b = brierToplam / tahmin;
        return Double.isFinite(b) ? b : 0.25;
    }

    public double isabetOrani() {
        return tahmin == 0 ? 0 : (double) isabet / tahmin;
    }

    /**
     * Beklenen Kalibrasyon Hatası (ECE): "%90 eminim" dediğinde gerçekten %90 tutuyor mu?
     *
     * Bir öz-model için bu, ham isabetten daha anlamlıdır — Candy'nin kendi güvenini
     * bilip bilmediğini ölçer.
     */
    public double ece() {
        if (tahmin == 0) return 0.5;
        double toplam = 0;
        for (int k = 0; k < 10; k++) {
            if (kovaSayi[k] == 0) continue;
            double ortGuven = kovaGuven[k] / kovaSayi[k];
            double gercek = (double) kovaIsabet[k] / kovaSayi[k];
            toplam += (kovaSayi[k] / (double) tahmin) * Math.abs(ortGuven - gercek);
        }
        return Double.isFinite(toplam) ? toplam : 0.5;
    }

    public void onar() {
        if (dunyaId == null) dunyaId = "?";
        if (kovaSayi == null || kovaSayi.length != 10) kovaSayi = new int[10];
        if (kovaGuven == null || kovaGuven.length != 10) kovaGuven = new double[10];
        if (kovaIsabet == null || kovaIsabet.length != 10) kovaIsabet = new int[10];
        if (!Double.isFinite(brierToplam)) brierToplam = 0;
        if (!Double.isFinite(genellemeSkoru)) genellemeSkoru = 0.5;
    }
}
