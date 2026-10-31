package com.photonpulse.candy.beyin;

/**
 * OyuncuHaritasi — Candy'nin oyuncu hakkındaki ÖNGÖRÜCÜ modeli ("beyin haritası").
 *
 * İki katman:
 * <pre>
 *   1) NİYET SINIFLAYICI  — 7 niyet × 48 özellik doğrusal softmax.
 *      "Bu duyu örüntüsünden sonra ne yapacak?" Online SGD ile eğitilir; hedef
 *      IcDusunce'nin gözlediği gerçek niyettir. Kalibre edilmiş olasılık üretir.
 *
 *   2) PROTOTİP HARİTASI  — 24 prototip × 12 özet boyut + 24×24 geçiş matrisi.
 *      Oyuncunun zihinsel "durum uzayı": hangi hâllerde bulunuyor ve hangi hâlden
 *      hangisine geçiyor. Rakip öğrenme (online k-means) ile kendi kendine oluşur.
 * </pre>
 *
 * HATA PENCERESİ: son 40 tahminin ortalama hatası saklanır — Aşama 5'teki ablasyon testi
 * ("bu lobu kıssam ne kaybederim?") tam olarak bu sayıyı okur.
 */
public class OyuncuHaritasi {

    public static final int NIYET_SAYISI = 7;
    public static final int PROTOTIP_SAYISI = 24;
    public static final int PROTOTIP_BOYUT = 12;
    private static final int PENCERE = 40;

    /** Niyet adları — indeks sırası sabittir (kalıcılık uyumu). */
    public static final String[] NIYETLER = {
        "kaziyor", "insa_ediyor", "kesfediyor", "bekliyor", "donuyor", "savasiyor", "kaciyor"
    };

    /** Doğrusal sınıflayıcı ağırlıkları [niyet][özellik]. */
    public float[][] w = new float[NIYET_SAYISI][Beyin.GIRDI_SAYISI];
    public float[] bias = new float[NIYET_SAYISI];

    /** Prototipler (oyuncunun zihinsel hâlleri) + geçiş matrisi. */
    public float[][] prototip = new float[PROTOTIP_SAYISI][PROTOTIP_BOYUT];
    public float[] prototipZiyaret = new float[PROTOTIP_SAYISI];
    public float[][] gecis = new float[PROTOTIP_SAYISI][PROTOTIP_SAYISI];
    public int sonPrototip = -1;
    public boolean prototiplerKuruldu = false;

    /** Kayan hata penceresi (Brier benzeri: 1 − doğru sınıfa verilen olasılık). */
    public float[] hataPenceresi = new float[PENCERE];
    public int hataYazar = 0;
    public int hataDolu = 0;

    /** Toplam istatistik. */
    public long tahminSayisi = 0L;
    public long isabetSayisi = 0L;

    public OyuncuHaritasi() {}

    // ─────────────────────────────────────────────────────────
    // NİYET TAHMİNİ
    // ─────────────────────────────────────────────────────────

    /** Softmax olasılıkları üretir (uzunluk NIYET_SAYISI). */
    public float[] tahminEt(float[] girdi) {
        float[] p = new float[NIYET_SAYISI];
        tahminEt(girdi, p);
        return p;
    }

    /**
     * Aynı softmax, <b>çağıranın tamponuna</b> yazar (v8).
     *
     * Dizi döndüren sürüm her çağrıda {@code float[7]} tahsis ediyordu ve bu yol ölçüm
     * adımında üye başına çalışıyor. Dönen diziyi saklayan bir çağıran yok, ama imza
     * kamuya açık olduğu için eski sürüm ince bir sarmalayıcı olarak korundu.
     */
    public void tahminEt(float[] girdi, float[] p) {
        if (p == null || p.length < NIYET_SAYISI) return;
        if (girdi == null) { java.util.Arrays.fill(p, 1f / NIYET_SAYISI); return; }
        float max = Float.NEGATIVE_INFINITY;
        for (int c = 0; c < NIYET_SAYISI; c++) {
            float net = bias[c];
            float[] wc = w[c];
            for (int i = 0; i < Beyin.GIRDI_SAYISI && i < girdi.length; i++) net += wc[i] * girdi[i];
            if (!Float.isFinite(net)) net = 0f;
            p[c] = net;
            if (net > max) max = net;
        }
        float toplam = 0f;
        for (int c = 0; c < NIYET_SAYISI; c++) {
            p[c] = (float) Math.exp(Math.max(-30.0, Math.min(30.0, p[c] - max)));
            toplam += p[c];
        }
        if (toplam <= 0f || !Float.isFinite(toplam)) {
            java.util.Arrays.fill(p, 1f / NIYET_SAYISI);
            return;
        }
        for (int c = 0; c < NIYET_SAYISI; c++) p[c] /= toplam;
    }

    /** Olasılık ve özet tamponları (v8) — sıcak yol tahsis etmesin. */
    private static final ThreadLocal<float[]> OLASILIK =
        ThreadLocal.withInitial(() -> new float[NIYET_SAYISI]);
    private static final ThreadLocal<float[]> OZET =
        ThreadLocal.withInitial(() -> new float[PROTOTIP_BOYUT]);

    /** Paylaşılan olasılık tamponu — dönen diziyi SAKLAMA, bir sonraki çağrı üzerine yazar. */
    public static float[] olasilikTamponu() {
        return OLASILIK.get();
    }

    /**
     * Bir gözlemle eğit (online SGD, çapraz entropi).
     *
     * @param gercekNiyet IcDusunce'nin gözlediği niyet adı
     * @return bu tahminin hatası (0 = mükemmel, 1 = tamamen ıska)
     */
    public double egit(float[] girdi, String gercekNiyet, float ogrenmeHizi) {
        int hedef = niyetIndeksi(gercekNiyet);
        if (hedef < 0 || girdi == null) return -1;

        float[] p = OLASILIK.get();
        tahminEt(girdi, p);
        double hata = 1.0 - p[hedef];

        // Çapraz entropi gradyanı: (p − onehot) · x
        for (int c = 0; c < NIYET_SAYISI; c++) {
            float g = p[c] - (c == hedef ? 1f : 0f);
            float lr = ogrenmeHizi * g;
            float[] wc = w[c];
            for (int i = 0; i < Beyin.GIRDI_SAYISI && i < girdi.length; i++) {
                float yeni = wc[i] - lr * girdi[i];
                wc[i] = Float.isFinite(yeni) ? Math.max(-6f, Math.min(6f, yeni)) : 0f;
            }
            float yb = bias[c] - lr;
            bias[c] = Float.isFinite(yb) ? Math.max(-6f, Math.min(6f, yb)) : 0f;
        }

        // İstatistik + kayan pencere
        tahminSayisi++;
        int enIyi = 0;
        for (int c = 1; c < NIYET_SAYISI; c++) if (p[c] > p[enIyi]) enIyi = c;
        if (enIyi == hedef) isabetSayisi++;
        hataPenceresi[hataYazar] = (float) hata;
        hataYazar = (hataYazar + 1) % PENCERE;
        if (hataDolu < PENCERE) hataDolu++;
        return hata;
    }

    /** Son penceredeki ortalama hata — ABLASYON TESTİNİN okuduğu sayı. */
    public double sonPencereHatasi() {
        if (hataDolu == 0) return 0.5;
        double t = 0;
        for (int i = 0; i < hataDolu; i++) t += hataPenceresi[i];
        double o = t / hataDolu;
        return Double.isFinite(o) ? o : 0.5;
    }

    public double isabetOrani() {
        return tahminSayisi == 0 ? 0 : (double) isabetSayisi / tahminSayisi;
    }

    public static int niyetIndeksi(String ad) {
        if (ad == null) return -1;
        for (int i = 0; i < NIYETLER.length; i++) if (NIYETLER[i].equals(ad)) return i;
        return -1;
    }

    // ─────────────────────────────────────────────────────────
    // PROTOTİP HARİTASI (zihinsel durum uzayı)
    // ─────────────────────────────────────────────────────────

    /**
     * 48 boyutlu duyuyu 12 boyutlu özete indirger (prototip uzayı).
     * Seçilen boyutlar davranışsal olarak en ayırt edici olanlardır.
     */
    private static void ozet(float[] girdi, float[] hedef) {
        // kac, saldir, don, kesfet, korku, ozguven, ongoru, cozulme, paranoya, merak, sikinti, meta
        final int[] idx = {0, 1, 2, 3, 8, 9, 14, 16, 21, 29, 30, 22};
        for (int i = 0; i < PROTOTIP_BOYUT; i++) {
            int k = idx[i];
            hedef[i] = (k < girdi.length) ? girdi[k] : 0f;
        }
    }

    /**
     * Oyuncunun anlık hâlini haritaya işler: en yakın prototipi bulur, ona doğru çeker
     * (rakip öğrenme) ve önceki hâlden bu hâle geçişi kaydeder.
     *
     * @return içinde bulunulan prototip indeksi
     */
    public int haritayaIsle(float[] girdi, float ogrenmeHizi) {
        if (girdi == null) return -1;
        final float[] o = OZET.get();      // ozet() 12 slotun tamamını yazar
        ozet(girdi, o);

        if (!prototiplerKuruldu) {
            // İlk kurulum: prototipleri gözlemin etrafına saç
            java.util.Random r = new java.util.Random(4242);
            for (int p = 0; p < PROTOTIP_SAYISI; p++) {
                for (int i = 0; i < PROTOTIP_BOYUT; i++) {
                    prototip[p][i] = (float) Math.max(0, Math.min(1,
                        o[i] + (r.nextDouble() - 0.5) * 0.4));
                }
            }
            prototiplerKuruldu = true;
        }

        // En yakın prototip
        int enIyi = 0;
        double enKisa = Double.MAX_VALUE;
        for (int p = 0; p < PROTOTIP_SAYISI; p++) {
            double d = 0;
            for (int i = 0; i < PROTOTIP_BOYUT; i++) {
                double f = o[i] - prototip[p][i];
                d += f * f;
            }
            if (d < enKisa) { enKisa = d; enIyi = p; }
        }

        // Kazanan prototipi gözleme doğru çek
        for (int i = 0; i < PROTOTIP_BOYUT; i++) {
            float yeni = prototip[enIyi][i] + ogrenmeHizi * (o[i] - prototip[enIyi][i]);
            prototip[enIyi][i] = Float.isFinite(yeni) ? Math.max(0f, Math.min(1f, yeni)) : 0f;
        }
        prototipZiyaret[enIyi] = Math.min(1e9f, prototipZiyaret[enIyi] + 1f);

        // Geçiş matrisi (bir hâlden diğerine)
        if (sonPrototip >= 0 && sonPrototip < PROTOTIP_SAYISI && sonPrototip != enIyi) {
            gecis[sonPrototip][enIyi] = Math.min(1e6f, gecis[sonPrototip][enIyi] + 1f);
        }
        sonPrototip = enIyi;
        return enIyi;
    }

    /** Bu prototipten en olası sonraki hâl (öngörü için). */
    public int enOlasiSonraki(int prototipIdx) {
        if (prototipIdx < 0 || prototipIdx >= PROTOTIP_SAYISI) return -1;
        int enIyi = -1;
        float max = 0;
        for (int j = 0; j < PROTOTIP_SAYISI; j++) {
            if (gecis[prototipIdx][j] > max) { max = gecis[prototipIdx][j]; enIyi = j; }
        }
        return enIyi;
    }

    /** Kaç farklı hâl gerçekten ziyaret edildi (haritanın zenginliği). */
    public int kesfedilenHalSayisi() {
        int c = 0;
        for (float v : prototipZiyaret) if (v > 2f) c++;
        return c;
    }

    /** Gson ile yüklenen eksik/bozuk alanları onarır. */
    public void onar() {
        if (w == null || w.length != NIYET_SAYISI) w = new float[NIYET_SAYISI][Beyin.GIRDI_SAYISI];
        for (int c = 0; c < NIYET_SAYISI; c++) {
            // v8 GOC: GIRDI_SAYISI 48 -> 52 buyudu. Eskiden burada satir YENIDEN
            // ayrilip ogrenilmis agirliklar sessizce sifirlaniyordu; niyet siniflayici
            // her sema buyumesinde bastan ogrenmek zorunda kalirdi. Artik Beyin.onar()'in
            // Q basligi kalibi gibi eski agirliklar KORUNUYOR, yalnizca yeni kanallar 0.
            if (w[c] == null) {
                w[c] = new float[Beyin.GIRDI_SAYISI];
            } else if (w[c].length != Beyin.GIRDI_SAYISI) {
                float[] yeni = new float[Beyin.GIRDI_SAYISI];
                System.arraycopy(w[c], 0, yeni, 0, Math.min(w[c].length, yeni.length));
                w[c] = yeni;
            }
            for (int i = 0; i < w[c].length; i++) if (!Float.isFinite(w[c][i])) w[c][i] = 0f;
        }
        if (bias == null || bias.length != NIYET_SAYISI) bias = new float[NIYET_SAYISI];
        if (prototip == null || prototip.length != PROTOTIP_SAYISI)
            prototip = new float[PROTOTIP_SAYISI][PROTOTIP_BOYUT];
        for (int p = 0; p < PROTOTIP_SAYISI; p++)
            if (prototip[p] == null || prototip[p].length != PROTOTIP_BOYUT)
                prototip[p] = new float[PROTOTIP_BOYUT];
        if (prototipZiyaret == null || prototipZiyaret.length != PROTOTIP_SAYISI)
            prototipZiyaret = new float[PROTOTIP_SAYISI];
        if (gecis == null || gecis.length != PROTOTIP_SAYISI)
            gecis = new float[PROTOTIP_SAYISI][PROTOTIP_SAYISI];
        for (int p = 0; p < PROTOTIP_SAYISI; p++)
            if (gecis[p] == null || gecis[p].length != PROTOTIP_SAYISI)
                gecis[p] = new float[PROTOTIP_SAYISI];
        if (hataPenceresi == null || hataPenceresi.length != PENCERE)
            hataPenceresi = new float[PENCERE];
        if (hataYazar < 0 || hataYazar >= PENCERE) hataYazar = 0;
        if (hataDolu < 0 || hataDolu > PENCERE) hataDolu = 0;
    }
}
