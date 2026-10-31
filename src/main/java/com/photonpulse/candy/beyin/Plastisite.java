package com.photonpulse.candy.beyin;

import java.util.Random;

/**
 * Plastisite — sinir yollarının sürekli kurulup bozulması.
 *
 * ÖĞRENME KURALI: <b>Oja</b> — {@code Δw = η·y·(x − y·w)}.
 * Saf Hebbian ("birlikte ateşlenen birlikte bağlanır") ağırlıkları sınırsız büyütür ve
 * ağı doyurur; Oja'nın çıkardığı {@code −y²w} terimi ağırlık vektörünün normunu kendi
 * kendine sınırlar. Yani patlama koruması ayrı bir yama değil, kuralın kendisinde.
 *
 * NÖROMODÜLASYON: KENAR (limbik) lobunun etkinliği global bir skaler üretir; duygusal
 * uyarılma yüksekken öğrenme hızlanır, sakinken yavaşlar. Gerçek beyinde dopamin/
 * noradrenalin bu işi yapar — burada Candy'nin "heyecanı" öğrenme hızıdır.
 *
 * İZ (eligibility trace): her sinaps son zamanlarda ne kadar iş gördüğünü biriktirir.
 * Budama kararını ve (Aşama 9) gecikmeli ödül kredisini bu belirler.
 *
 * BUDAMA / NÖROGENEZ: CSR dizisi yeniden inşa EDİLMEZ (çok pahalı). Bunun yerine zayıf
 * sinapsın ağırlığı sıfırlanır (fiilen ölür), ve nörogenez bu "ölü slotları" yeni
 * bağlantılarla doldurur. Böylece yapı sabit maliyetle sürekli yeniden şekillenir.
 */
public class Plastisite {

    /** Temel öğrenme hızı (modülatör ile çarpılır). */
    private static final float OJA_LR = 0.006f;
    /** İz sönümü — ~20 süpürmede yarılanır. */
    private static final float IZ_SONUM = 0.96f;
    /** Bu aktifliğin altındaki nöronlar öğrenmez (CPU tasarrufu ~%40). */
    private static final float SESSIZ_ESIK = 0.12f;
    /** Ağırlık mutlak tavanı (ikili niceleme aralığıyla uyumlu). */
    private static final float AGIRLIK_TAVAN = 4.0f;
    /** Bu değerin altındaki ağırlık + iz = budanmaya aday. */
    private static final float BUDAMA_ESIGI = 0.015f;
    private static final float BUDAMA_IZ_ESIGI = 0.02f;
    /** Ölü sayılan ağırlık. */
    private static final float OLU_ESIK = 0.0005f;
    /**
     * Norogenez kotasi — sinaps sayisina GORELI (1/{@value #KOTA_BOLENI}).
     *
     * Sabit 64'te olcek sorunu sessizdi ama olumcul: budama TUM diziyi tarar, yani
     * budanan sayisi agla birlikte buyur; norogenez sabit kalinca ag tek yonlu incelir.
     * Olculdu: 1,55M sinapsta dakikada ~1.700 budama karsisinda ~5 dogum.
     */
    private static final int KOTA_BOLENI = 2048;
    /** Bir norogenez cagrisinda taranacak noron orani (1/32 -> tam kapsama 32 cagri). */
    private static final int TARAMA_BOLENI = 32;
    /**
     * Yeni baglantinin kaynagi hedefin bu kadar yakinindan secilir.
     *
     * KRITIK: kuresel rastgele kaynak, yerel pencere topolojisinin sagladigi ilintiyi
     * yavas yavas yok ederdi (saatte ~%10 baglanti kurese donerdi) ve lob cikislari
     * yeniden 0,5'e yapismaya baslardi. Norogenez yapiyi yeniden sekillendirmeli,
     * COZMEMELI.
     */
    private static final int DOGUM_PENCERESI = 256;

    private Plastisite() {}

    /**
     * KENAR lobundan global nöromodülatör üretir (0.4 .. 3.0).
     * Şaşırma (öngörü hatası) da hızlandırır — beklenmedik şey daha çok öğretir.
     */
    public static float modulator(Beyin b, double sasirmaSeviyesi) {
        double kenar = b.lobOrtalamasi(Lob.KENAR);          // 0..1
        double sasirma = Math.max(0.0, Math.min(1.0, sasirmaSeviyesi / 100.0));
        // YANKI (v5): "gecmis kararlarim tutmadiysa daha hizli degis".
        // Retrospeksiyon lobu yuksekse — yani kararlarin sonucu tutarsiz/pismanlik
        // yuksekse — ogrenme hizlanir; kararlar oturmussa yavaslar. Duygusal uyarilma
        // (KENAR) "ne kadar onemli", YANKI ise "ne kadar yaniliyorum" der.
        double yanki = b.lobCikisi(Lob.YANKI);              // 0..1, notr 0,5
        double m = 0.4 + kenar * 1.6 + sasirma * 1.0 + (yanki - 0.5) * 0.8;
        if (!Double.isFinite(m)) m = 1.0;
        return (float) Math.max(0.4, Math.min(3.0, m));
    }

    /**
     * Oja güncellemesini bir nöron dilimine uygular (ileri beslemeyle aynı dilimleme).
     *
     * @param plastisiteCarpani dünyalar arası doğrulama modunda düşürülür (Aşama 8)
     * @return güncellenen sinaps sayısı
     */
    public static int guncelleDilim(Beyin b, int dilim, float modulator, float plastisiteCarpani) {
        if (b.agirlik == null || b.sinapsIz == null) return 0;
        if (b.sinapsIz.length != b.sinapsSayisi) b.sinapsIz = new float[b.sinapsSayisi];

        // ISINMA RAMPASI: 1,5M sinapsli taze bir agin yapiya oturmasi, kucuk agdakinden
        // cok daha uzun surer (bir sinaps supurme basina yalnizca 1 kez guncellenir).
        // Ilk 100 supurmede ogrenme hizi 2,5x baslar ve normale iner — yoksa oyuncunun
        // ilk oturumu "bos" hissettirir.
        float isinma = (b.toplamSupurme < 100)
            ? (2.5f - 1.5f * (b.toplamSupurme / 100f)) : 1.0f;
        final float lr = OJA_LR * isinma * modulator
            * Math.max(0f, Math.min(2f, plastisiteCarpani));
        final int bas = b.dilimBas(dilim);
        final int son = b.dilimSon(dilim);
        int guncellenen = 0;

        for (int i = bas; i < son; i++) {
            // Enjekte noronun gelen sinapsi yok (fan-in 0); yine de acikca atla ki
            // ileride birine "bos slot" verilse bile ic duyu kanali kirilmasin.
            if (b.katman[i] < 0) continue;
            // Körelmiş lob öğrenmez
            if (b.loblar != null) {
                int lid = b.lobId[i];
                if (lid >= 0 && lid < b.loblar.length && b.loblar[lid].koreldi) continue;
            }
            final float y = b.aktiflik[i];
            if (y < SESSIZ_ESIK) continue;   // sessiz nöron: ne öğrenir ne öğretir

            final int s = b.girisBasi[i];
            final int e = b.girisBasi[i + 1];
            // MERKEZLEME (kritik): girdiler ve aktivasyonlar 0..1 aralığında, yani HEP pozitif.
            // Ham Hebbian bu durumda tüm ağırlıkları pozitif yönde büyütür, net girdi sürekli
            // artar ve ağ 1.0'a doyar — bilgi taşımaz hale gelir. 0.5 etrafında merkezleyerek
            // hem pozitif hem negatif ağırlıkların oluşmasını sağlıyoruz (gerçek zıtlık öğrenimi).
            final float ym = y - 0.5f;
            for (int j = s; j < e; j++) {
                final float x = b.kaynakDegeri(b.kaynak[j]);
                final float xm = x - 0.5f;
                final float w = b.agirlik[j];
                // Oja: Hebbian + kendi kendini normalize eden sönüm terimi (merkezlenmiş)
                float dw = lr * ym * (xm - ym * w);
                float yeni = w + dw;
                if (!Float.isFinite(yeni)) yeni = 0f;
                b.agirlik[j] = Math.max(-AGIRLIK_TAVAN, Math.min(AGIRLIK_TAVAN, yeni));
                // İz: bu sinaps yakın geçmişte iş gördü mü?
                float iz = b.sinapsIz[j] * IZ_SONUM + Math.abs(xm * ym);
                b.sinapsIz[j] = Float.isFinite(iz) ? Math.min(50f, iz) : 0f;
                guncellenen++;
            }
        }
        return guncellenen;
    }

    /**
     * Budama: uzun süredir hem zayıf hem işlevsiz sinapsları öldürür.
     * Ağırlık sıfırlanır; slot nörogeneze havuz olur.
     *
     * @return budanan sinaps sayısı
     */
    public static int buda(Beyin b) {
        if (b.agirlik == null || b.sinapsIz == null || b.girisBasi == null) return 0;
        int budanan = 0;
        // NORON UZERINDEN: sinaps indeksi tek basina hangi norona ait oldugunu soylemez;
        // golge arsivi hedef noronu bilmek zorunda (diriltirken oraya geri baglayacak).
        // CSR'de noron -> sinaps yonu zaten sirali, yani bu ayni zamanda daha hizli.
        for (int i = 0; i < b.noronSayisi; i++) {
            if (b.katman[i] < 0) continue;               // enjekte noronun sinapsi yok
            final int son = b.girisBasi[i + 1];
            for (int j = b.girisBasi[i]; j < son; j++) {
                float w = b.agirlik[j];
                if (w == 0f) continue;                   // zaten ölü
                if (Math.abs(w) < BUDAMA_ESIGI && b.sinapsIz[j] < BUDAMA_IZ_ESIGI) {
                    // BASTIRMA: olmeden once izi golgeye gomulur (v5)
                    if (b.golgeArsivi != null) b.golgeArsivi.gom(i, b.kaynak[j], w);
                    b.agirlik[j] = 0f;
                    b.sinapsIz[j] = 0f;
                    budanan++;
                }
            }
        }
        b.budananToplam += budanan;
        return budanan;
    }

    /**
     * Nörogenez: ölü slotları yeni bağlantılarla doldurur.
     *
     * Kaynak seçimi rastgele değil — AKTİF nöronlar tercih edilir ("şu an konuşan
     * bölgeler yeni yollar kurar"). Böylece yapı, oyuncunun o dönemki davranışına göre
     * yeniden şekillenir.
     *
     * @return doğan bağlantı sayısı
     */
    public static int norogenez(Beyin b, Random rng) {
        if (b.agirlik == null || b.sinapsSayisi <= 0 || b.noronSayisi <= 0) return 0;
        final int n = b.noronSayisi;
        int kota = Math.max(64, b.sinapsSayisi / KOTA_BOLENI);
        final int pencere = Math.max(256, n / TARAMA_BOLENI);
        int dogan = 0;

        // ── BASTIRILAN GERI DONER (v5) ──
        // GOLGE lobu ne kadar aktifse, kotanin o kadar buyuk bir kismi YENI baglanti
        // yerine ESKI, budanmis desenin diriltilmesine harcanir. Norogenez unutmanin
        // telafisiyken, diriltme bilincdisinin geri donusudur.
        if (b.golgeArsivi != null && b.loblar != null) {
            LobDurum gd = b.loblar[Lob.GOLGE.ordinal()];
            if (gd == null || !gd.koreldi) {
                double golge = b.lobCikisi(Lob.GOLGE);      // 0..1
                int diriltKota = (int) (kota * 0.5 * golge);
                if (diriltKota > 0) kota -= b.golgeArsivi.dirilt(b, diriltKota, rng);
            }
        }
        if (kota <= 0) return 0;
        int i = b.norogenezImleci;
        if (i < 0 || i >= n) i = 0;

        // DONEN IMLEC: rastgele ornekleme yerine ardisik tarama. Hem olu slotlari
        // garantili bulur hem de gather L1'de kalir.
        for (int adim = 0; adim < pencere && dogan < kota; adim++) {
            final int hedef = i;
            if (++i >= n) i = 0;

            if (b.katman[hedef] < 0) continue;               // enjekte noronun sinapsi yok
            int lid = b.lobId[hedef];
            if (b.loblar != null && lid >= 0 && lid < b.loblar.length
                && b.loblar[lid].koreldi) continue;

            // Hedefin lob siniri — yeni baglanti lob disina tasmasin
            int lobBas = 0, lobSon = n;
            if (lid >= 0 && lid < Lob.LOBLAR.length) {
                Lob lob = Lob.LOBLAR[lid];
                lobBas = lob.baslangic();
                lobSon = Math.min(lob.bitis(), n);
            }
            final int pBas = Math.max(lobBas, hedef - DOGUM_PENCERESI);
            final int pAralik = Math.max(1, Math.min(lobSon, hedef + DOGUM_PENCERESI) - pBas);

            final int s = b.girisBasi[hedef];
            final int e = b.girisBasi[hedef + 1];
            for (int j = s; j < e && dogan < kota; j++) {
                if (Math.abs(b.agirlik[j]) > OLU_ESIK) continue;   // slot dolu

                // Kaynak: %25 duyu, %75 YEREL ve aktif bir nöron
                int kaynak;
                if (rng.nextInt(4) == 0) {
                    kaynak = rng.nextInt(Beyin.GIRDI_SAYISI);
                } else {
                    int aday = -1;
                    for (int t = 0; t < 6; t++) {
                        int c = pBas + rng.nextInt(pAralik);
                        if (c != hedef && b.aktiflik[c] > SESSIZ_ESIK) { aday = c; break; }
                    }
                    if (aday < 0) continue;
                    kaynak = Beyin.GIRDI_SAYISI + aday;
                }
                b.kaynak[j] = kaynak;
                b.agirlik[j] = (float) ((rng.nextDouble() * 2.0 - 1.0) * 0.12);
                if (b.sinapsIz != null) b.sinapsIz[j] = 0f;
                dogan++;
            }
        }
        b.norogenezImleci = i;
        b.doganToplam += dogan;
        return dogan;
    }

    /**
     * Seyrek NaN taramasi — supurme basina 256 rastgele sinaps.
     *
     * Buyuk agda tam tarama pahali; ama tek bir NaN tum beyni sessizce oldurur. Ornekleme
     * O(256) maliyetle sizintiyi erken yakalar.
     * @return NaN bulundu mu
     */
    public static boolean nanTaramasi(Beyin b, java.util.Random rng) {
        if (b.agirlik == null || b.sinapsSayisi <= 0) return false;
        boolean bulundu = false;
        for (int t = 0; t < 256; t++) {
            int j = rng.nextInt(b.sinapsSayisi);
            if (!Float.isFinite(b.agirlik[j])) { b.agirlik[j] = 0f; bulundu = true; }
        }
        return bulundu;
    }

    /**
     * Güvenlik ağı: bir lobun ağırlık normu patlarsa toplu küçültme.
     *
     * DerinAnaliz'in TETIKLEYICI_TAVAN deseninin (tavan aşılınca hepsini ×0.5) beyin
     * ölçeğindeki karşılığı — oranlar korunur, mutlak değerler taşmaz. Oja normalde
     * bunu gereksiz kılar; bu yalnızca sayısal kaza için.
     */
    public static boolean normKontrol(Beyin b, Lob lob) {
        int bas = lob.baslangic(), son = Math.min(lob.bitis(), b.noronSayisi);
        double toplam = 0;
        int adet = 0;
        for (int i = bas; i < son; i++) {
            int s = b.girisBasi[i], e = b.girisBasi[i + 1];
            for (int j = s; j < e; j++) { toplam += Math.abs(b.agirlik[j]); adet++; }
        }
        if (adet == 0) return false;
        double ort = toplam / adet;
        if (ort <= 1.2) return false;
        for (int i = bas; i < son; i++) {
            int s = b.girisBasi[i], e = b.girisBasi[i + 1];
            for (int j = s; j < e; j++) b.agirlik[j] *= 0.5f;
        }
        return true;
    }
}
