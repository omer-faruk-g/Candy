package com.photonpulse.candy.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TruePersonality — Candy'nin oyuncu hakkında bildiği HER ŞEY.
 *
 * Tüm benlikler, klonlar, tabelalar bu tek kaynaktan okur.
 * Gecikme yok, ara katman yok, eğitim yok — anlık kayma ile güncellenir.
 *
 * Asymptotik güncelleme:  yeni = eski + (hedef - eski) * hiz
 * Hivemind kuralı: sabit sıçrama yok, sadece yumuşak yaklaşım.
 */
public class TruePersonality {

    // Kimlik
    public UUID oyuncuId;
    public long ilkKayitTick = 0;
    public long toplamGozlemTick = 0;

    // ── Hareket örüntüsü (0..1 arası ağırlıklar) ──
    public double kacOrani = 0.0;
    public double saldirOrani = 0.0;
    public double donOrani = 0.0;
    public double kesfetOrani = 0.0;

    // ── Ekipman tercihleri (item id -> kullanım skoru) ──
    public Map<String, Double> ekipmanKullanim = new HashMap<>();

    // ── Rota / mekan ──
    public List<int[]> sicakNoktalar = new ArrayList<>();
    public int[] enSonKonum = null;      // [x, y, z]
    public int[] geceKonumu = null;      // gece en çok bulunduğu yer
    public int[] gunduzKonumu = null;

    // ── Psikoloji vektörü ──
    public double panikEsigi   = 0.5;    // ne zaman panikliyor
    public double riskToleransi = 0.5;   // riske ne kadar açık
    public double kesfetmeIstegi = 0.5;
    public double inkarEgilimi = 0.0;    // "korkmuyorum" diyen tip
    public double aciklik      = 0.5;    // uzun cevap veriyor mu

    // ── Korku skoru ──
    // 0..100 arası. Kurulum aşamasından sonra 10'dan başlar (asla 0'dan değil).
    public double korkuSkoru = 10.0;

    // ── Etkileşim geçmişi ──
    public List<String> tabelaCevaplari = new ArrayList<>();
    public String ilkTepki = "bilinmiyor";  // varlığı ilk görünce ne yaptı

    // ── Kurulum durumu ──
    public boolean kurulumTamam = false;

    // ── Ayrılma / dönme takibi (RealCandy için) ──
    public long sonAyrilmaTick = 0;

    public TruePersonality() {}

    public TruePersonality(UUID oyuncuId) {
        this.oyuncuId = oyuncuId;
    }

    /**
     * Asymptotik kayma. Anında sıçrama YOK.
     * hedef: nereye yaklaşmak istiyoruz (0 veya 1)
     * hiz:   ne kadar hızlı yaklaşsın (0.01 - 0.05 arası mantıklı)
     */
    public static double kayma(double mevcut, double hedef, double hiz) {
        return mevcut + (hedef - mevcut) * hiz;
    }

    /**
     * Hareket eğilimlerini toplam 1'e normalize eder.
     * Bir eğilim artınca diğerleri otomatik azalır.
     */
    public void hareketNormalize() {
        double t = kacOrani + saldirOrani + donOrani + kesfetOrani;
        if (t < 0.001) return;
        kacOrani /= t;
        saldirOrani /= t;
        donOrani /= t;
        kesfetOrani /= t;
    }

    /**
     * En sık kullanılan ekipmanın id'sini döner.
     * Candy'nin "hangi araca güveniyor" cevabı.
     */
    public String enSikEkipman() {
        String enSik = null;
        double max = 0;
        for (var e : ekipmanKullanim.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                enSik = e.getKey();
            }
        }
        return enSik;
    }

    /**
     * Sıcak noktaya ekle. Aynı yer birden çok kez gelirse listede sadece bir kere durur
     * ama "sıklık" korku skorunu besler (dışarıda hesaplanır).
     */
    public void sicakNoktaEkle(int x, int y, int z) {
        for (int[] p : sicakNoktalar) {
            if (Math.abs(p[0] - x) < 5 && Math.abs(p[2] - z) < 5) return;
        }
        sicakNoktalar.add(new int[]{x, y, z});
        // hafızayı sınırlı tut — en eski 20 nokta
        if (sicakNoktalar.size() > 20) sicakNoktalar.remove(0);
    }
}
