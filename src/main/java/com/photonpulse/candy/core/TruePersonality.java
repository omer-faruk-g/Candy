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

    // ── v1.8.0: Psikolojik hastalık sistemi ──
    // Boş string = sağlıklı. Aksi halde PsikolojikHastalik enum name (lowercase).
    public String aktifHastalik = "";
    public long hastalikBaslangicTick = 0L;

    // ── v1.9.2: Ending — Candy tamamen player oldu ──
    // candyOzguven 100'e ulaşınca tetiklenir, tek sefer.
    public boolean endingTetiklendi = false;
    // Toplam kaptığı farklı hastalıklar (Her Şeyi Gördüm başarımı için)
    public java.util.Set<String> kaptigiHastaliklar = new java.util.HashSet<>();

    // ── v1.9.3: Başarım sayaçları ──
    public int kirilanMesaleSayisi = 0;
    public int envanterKaristirmaSayisi = 0;
    public boolean hayaletChatGorulduMu = false;
    public int oldurulenKlonSayisi = 0;
    public long ilkKarartmaBaslangicTick = 0L;
    public boolean karanliktaBasarilar = false;

    // ── v1.9.17: Klon evrimi — karşılaştığı klon sayısı ──
    // >4: klon blok kırıp koyabilir (adaptive)
    // >3 + %15: elite klon envanteri tarayıp kendine ekipman üretir
    public int karsilastigiKlonSayisi = 0;

    // ── Ayrılma / dönme takibi (RealCandy için) ──
    public long sonAyrilmaTick = 0;

    // ── v0.3: ShadowState ──
    // Candy oyuncuyu sadece izlerse burada baskı birikir; 100'e ulaşınca patlar (ani klon)
    public double bastirilmisBaski = 0.0;
    // Son "aktif müdahale" tick'i — klon spawn, tabela yerleştirme buraya damga vurur
    public long sonMudahaleTick = 0;

    // ── v0.3: Discontent ──
    // Ana ağacın bilgi artışı yavaşladıkça birikir (0..100). Yüksekse bastırma → ShadowTree
    public double memnuniyetsizlik = 0.0;

    // ── v0.9+: Psikoloji ──
    // Kalıcı psikoloji kötülüğü (0..100). PsikolojiTakip tarafından güncellenir.
    // Kötüleştiriciler: cave, mob damage, high fear
    // İyileştiriciler: diamond, flower, surface + gündüz (sadece duraklama sonrası)
    public double psikolojiKotulugu = 0.0;
    // Son kötüleşme tick'i — iyileşme "duraklama" süresi hesaplaması için
    public long sonKotulesmeTick = 0;

    // ── v1.3: Candy özgüveni ──
    // Kişi kaçıp saklandıkça, ani hareketler yapınca Candy'nin özgüveni artar.
    // Yüksek özgüven → agresivlik carpani + 0-2, tüm olaylar 2-3x hızlanır.
    // Sakin oyuncu → yavaş azalır.
    public double candyOzguven = 0.0;

    // ── v1.3: Denek 239 kitabı verildi mi? ──
    // İlk join'de oyuncuya kitap ver, sonraki join'lerde tekrarlama.
    public boolean denek239Verildi = false;

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
