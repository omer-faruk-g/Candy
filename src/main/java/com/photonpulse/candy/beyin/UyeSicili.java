package com.photonpulse.candy.beyin;

/**
 * UyeSicili — bir üyenin <b>ölçülmüş</b> karnesi. Kullanıcının istediği "kanıt" budur.
 *
 * <h3>NE ÖLÇER — VE NEDEN "TAVSİYE İSABETİ" DEĞİL</h3>
 * İlk sürüm isabeti "üye bu eylemi önerdi mi ve iyi mi gitti" diye tanımlıyordu. Canlı
 * testte bu ÇÖKTÜ: üyeler hemfikirken (soğuk başlangıçta hep öyledir) hepsi aynı eylemi
 * önerir, dolayısıyla hepsi aynı puanı alır. Ölçülen şey üyenin becerisi değil, eylemin
 * TABAN BAŞARI ORANI olur — on bir üye de %6'da eşitlenir, ağırlıklar tekdüze kalır ve
 * hakem hiç kimseyi kimseden ayıramaz. Kanıt üretmeyen bir kanıt mekanizması.
 *
 * Doğrusu <b>öngörü isabeti</b>dir: üye, yapılan eylemin ne getireceğine bir DEĞER
 * biçmişti; gerçek ne çıktı? Bu, üyeler hemfikirken bile ayrıştırır (değerleri farklıdır),
 * uygun bir puanlama kuralıdır ve tam olarak hakemin ihtiyaç duyduğu şeyi ölçer:
 * <i>bu beynin dünya modeline ne kadar güvenebilirim?</i>
 * <ul>
 *   <li><b>isabet</b> — biçtiği değer gerçeğin {@value #ISABET_ESIGI} bandında mıydı?</li>
 *   <li><b>brier</b> — sapmanın karesi (kalibrasyon)</li>
 *   <li><b>dinlenenOdul</b> — hakem bu üyeyi dinlediğinde fiilen ne aldı?</li>
 * </ul>
 *
 * <h3>NEDEN LAPLACE YUMUŞATMASI</h3>
 * Üç tahmin yapıp üçünü de tutturan yeni bir üyenin sicili %100 olurdu ve hakem onu
 * anında baş tacı ederdi. {@code (isabet + 2) / (tahmin + 4)} bunu engeller: az veriyle
 * 0,5'e yakın kalır, kanıt biriktikçe gerçek orana yaklaşır. Kanıt sayısı da bir kanıttır.
 *
 * <h3>NEDEN SÖNÜMLÜ</h3>
 * Oyuncu değişir. Üç saat önce doğru olan bir üye bugün yanılıyor olabilir; sonsuz
 * hafızalı bir sicil bunu asla fark etmezdi. Sayaçlar periyodik olarak ×0,98 sönümlenir —
 * eski kanıt silinmez, ağırlığını yavaşça kaybeder.
 */
public class UyeSicili {

    /** Kaç kez değerlendirildi (sönümlü). */
    public double tahmin = 0.0;
    /** Kaç kez isabet etti (sönümlü). */
    public double isabet = 0.0;
    /** Brier skoru toplamı (sönümlü) — küçük iyi. */
    public double brierToplam = 0.0;
    /** Hakem bu üyeyi dinlediğinde ölçülen ortalama ödül. */
    public double dinlenenOdul = 0.0;
    /** Kaç kez hakem bu üyenin dediğini yaptı. */
    public long dinlendi = 0L;
    /** Ham (sönümsüz) toplam — izleme için. */
    public long toplamDegerlendirme = 0L;

    /** Sönüm çarpanı ve ne sıklıkla uygulanacağı. */
    private static final double SONUM = 0.98;
    private static final int SONUM_ARALIGI = 25;

    /**
     * Öngörü bu bant içindeyse isabet sayılır (normalize ödül ölçeğinde).
     *
     * ÇÖZÜNÜRLÜK NOTU: 0,25 ile başlanmıştı ve ÖLÇTÜ AMA AYIRMADI — üyelerin
     * öngörüleri birbirinden ~0,07 ayrılırken bant 0,25 olunca neredeyse hepsi aynı
     * tarafa düşüyor, on bir üye de aynı yüzdede eşitleniyordu. Bandın, ayırt etmesi
     * beklenen farkla aynı mertebede olması gerekir.
     */
    public static final double ISABET_ESIGI = 0.12;

    /**
     * Bir öngörüyü puanlar: hem isabet hem Brier tek yerden hesaplanır ki hakemin ve
     * üyelerin karnesi kesinlikle aynı kuralla tutulsun — farklı kural, "kurul tek
     * üyeden iyi mi" karşılaştırmasını anlamsız kılardı.
     *
     * @param tahminNorm biçilen değer, 0..1'e normalize
     * @param gercekNorm gözlenen ödül, 0..1'e normalize
     */
    public void ongoruPuanla(double tahminNorm, double gercekNorm) {
        if (!Double.isFinite(tahminNorm)) tahminNorm = 0.5;
        if (!Double.isFinite(gercekNorm)) gercekNorm = 0.5;
        double sapma = tahminNorm - gercekNorm;
        kaydet(Math.abs(sapma) < ISABET_ESIGI, sapma * sapma);
    }

    public UyeSicili() {}

    /**
     * Bir kararın sonucu ölçüldü.
     *
     * @param isabetliMi üyenin seçtiği eylem gerçekten iyi sonuç verdi mi
     * @param brier      biçtiği değer ile gerçek arasındaki kare hata (0..1)
     */
    public void kaydet(boolean isabetliMi, double brier) {
        if (!Double.isFinite(brier)) brier = 0.25;
        brier = Math.max(0.0, Math.min(1.0, brier));
        tahmin += 1.0;
        if (isabetliMi) isabet += 1.0;
        brierToplam += brier;
        toplamDegerlendirme++;

        if (toplamDegerlendirme % SONUM_ARALIGI == 0) {
            tahmin *= SONUM;
            isabet *= SONUM;
            brierToplam *= SONUM;
        }
    }

    /** Hakem bu üyenin dediğini yaptı ve şu ödülü aldı. */
    public void dinlendiKaydet(double odul) {
        if (!Double.isFinite(odul)) return;
        dinlendi++;
        dinlenenOdul += (odul - dinlenenOdul) * 0.10;
    }

    /**
     * ÖLÇÜLEN güvenilirlik (0..1) — Laplace yumuşatmalı isabet oranı.
     * Kanıt yokken 0,5: ne susturulur ne de körü körüne dinlenir.
     */
    public double guvenilirlik() {
        double d = tahmin + 4.0;
        double r = (isabet + 2.0) / d;
        return Double.isFinite(r) ? Math.max(0.0, Math.min(1.0, r)) : 0.5;
    }

    /**
     * KALİBRASYON (0..1) — öngörü kalitesinin BİRİNCİL ölçütü. 1 = kusursuz.
     *
     * <h3>NEDEN İSABET ORANI DEĞİL DE BU</h3>
     * Eşikli isabet oranı <b>uygunsuz</b> (improper) bir puanlama kuralıdır: tahmininize
     * varyans ekleyerek yükseltebilirsiniz. Canlı testte tam olarak bu görüldü —
     * saçılan üyeler bandın içine şansla düşüp yüksek isabet aldı, on bir tahminin
     * ortalamasını alan hakem ise merkeze yapıştığı için düşük aldı. Yani ölçüt,
     * <i>daha iyi tahmin edeni</i> değil <i>daha çok saçılanı</i> ödüllendiriyordu.
     *
     * Brier <b>uygun</b> (proper) bir kuraldır: beklenen skoru en iyi hâle getiren tek
     * strateji, gerçek inancınızı bildirmektir. Ortalama alma burada matematiksel
     * olarak kazanır (kare hata ortalamada minimize olur). Modun {@code DunyaDogrulayici}
     * bileşeni de aynı sebeple Brier kullanır — ölçüt birliği kasıtlıdır.
     *
     * Brier 0 → 1,0; Brier 0,25 (rastgele) → 0,5; Brier 0,5+ → 0.
     */
    public double kalibrasyon() {
        if (tahmin < 1.0) return 0.5;
        double b = brierToplam / tahmin;
        double k = 1.0 - 2.0 * b;
        return Double.isFinite(k) ? Math.max(0.0, Math.min(1.0, k)) : 0.5;
    }

    public void onar() {
        if (!Double.isFinite(tahmin) || tahmin < 0) tahmin = 0;
        if (!Double.isFinite(isabet) || isabet < 0) isabet = 0;
        if (isabet > tahmin) isabet = tahmin;
        if (!Double.isFinite(brierToplam) || brierToplam < 0) brierToplam = 0;
        if (!Double.isFinite(dinlenenOdul)) dinlenenOdul = 0;
        if (dinlendi < 0) dinlendi = 0;
        if (toplamDegerlendirme < 0) toplamDegerlendirme = 0;
    }

    @Override
    public String toString() {
        return String.format("%.0f%%/%.0f%% n=%d", guvenilirlik() * 100,
            kalibrasyon() * 100, toplamDegerlendirme);
    }
}
