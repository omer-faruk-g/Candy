package com.photonpulse.candy.beyin;

/**
 * ZamanIzi — BEYİNCİK lobunun belleği: "ne zaman?"
 *
 * Kehanet <i>ne yapacağını</i> söyler; bu iz <i>ne zaman</i> olduğunu söyler.
 * Süpürme başına bir örnek alınır (korku, etkinlik, niyet) ve {@link #KAPASITE}
 * süpürmelik bir halkada tutulur — tek oyuncu için ~10 dakikalık ritim penceresi.
 *
 * <h3>NEDEN OTOKORELASYON</h3>
 * Korku bir seviye değil, bir <b>eğridir</b>. Her oyuncunun kendi ritmi vardır:
 * gerilir, boşalır, rahatlar, tekrar gerilir. Gecikmeli otokorelasyon bu döngünün
 * periyodunu ölçer; Candy böylece "şu an neredeyim" değil, "bu eğrinin neresindeyim"
 * sorusuna cevap verebilir.
 *
 * <h3>REHAVET</h3>
 * Korkunun düşük OLMASI değil, <b>uzun süredir düşük kalması</b> önemlidir. Rahatlamış
 * oyuncu en savunmasız oyuncudur; bu skor ZihinselProva'nın sabit bekleme süresini
 * öğrenilen bir kapıya çevirir.
 *
 * Sıkıştırılmış tutulur (örnek başına 3 byte): 96 örnek = 288 byte, künyede taşınabilir.
 */
public class ZamanIzi {

    /** Halka boyu (süpürme). */
    public static final int KAPASITE = 96;
    /** Kaç gecikme için otokorelasyon hesaplanır. */
    public static final int GECIKME_SAYISI = 8;

    /** 0..255'e nicelenmiş örnekler (byte olarak saklanır, işaretsiz okunur). */
    public byte[] korku = new byte[KAPASITE];
    public byte[] etkinlik = new byte[KAPASITE];
    public byte[] niyet = new byte[KAPASITE];
    public int yazImleci = 0;
    public int dolu = 0;

    /** Korkunun düşük kaldığı ardışık süpürme sayısı. */
    public int rehavetSupurme = 0;

    public ZamanIzi() {}

    /**
     * Süpürme başına bir örnek.
     *
     * @param korku01     0..1 normalize korku
     * @param etkinlik01  0..1 normalize hareket/etkileşim yoğunluğu
     * @param niyetKodu   niyet sınıfı (0..7)
     */
    public void kaydet(float korku01, float etkinlik01, int niyetKodu) {
        korku[yazImleci] = nicele(korku01);
        etkinlik[yazImleci] = nicele(etkinlik01);
        niyet[yazImleci] = (byte) Math.max(0, Math.min(7, niyetKodu));
        yazImleci = (yazImleci + 1) % KAPASITE;
        if (dolu < KAPASITE) dolu++;

        if (Float.isFinite(korku01) && korku01 < 0.30f) rehavetSupurme++;
        else rehavetSupurme = 0;
    }

    /** i. en yeni korku örneği (0 = en son), 0..1. */
    public float sonKorku(int i) {
        return oku(korku, i);
    }

    /** i. en yeni etkinlik örneği (0 = en son), 0..1. */
    public float sonEtkinlik(int i) {
        return oku(etkinlik, i);
    }

    private float oku(byte[] dizi, int i) {
        if (dolu <= i || i < 0) return 0.5f;
        int idx = ((yazImleci - 1 - i) % KAPASITE + KAPASITE) % KAPASITE;
        return (dizi[idx] & 0xFF) / 255f;
    }

    /**
     * Korku serisinin belirli bir gecikmedeki otokorelasyonu (-1..1 → 0..1'e taşınmış).
     *
     * Payda sıfıra giderse (sabit seri) nötr 0,5 döner — {@code 0/0} NaN'ı buradan
     * sızarsa bağlı lobun tüm katmanlarını sessizce öldürürdü.
     */
    public float otokorelasyon(int gecikme) {
        if (gecikme < 1 || dolu < gecikme + 8) return 0.5f;
        int n = dolu - gecikme;
        double ort = 0;
        for (int i = 0; i < dolu; i++) ort += oku(korku, i);
        ort /= dolu;

        double pay = 0, payda = 0;
        for (int i = 0; i < dolu; i++) {
            double d = oku(korku, i) - ort;
            payda += d * d;
            if (i < n) pay += d * (oku(korku, i + gecikme) - ort);
        }
        if (payda < 1e-6) return 0.5f;
        double r = pay / payda;
        if (!Double.isFinite(r)) return 0.5f;
        return (float) (0.5 + 0.5 * Math.max(-1.0, Math.min(1.0, r)));
    }

    /** En güçlü periyodiklik (0..1) — ritim ne kadar belirgin? */
    public float periyodiklik() {
        float enIyi = 0.5f;
        for (int g = 1; g <= GECIKME_SAYISI; g++) {
            float r = otokorelasyon(g);
            if (r > enIyi) enIyi = r;
        }
        return enIyi;
    }

    /**
     * REHAVET (0..1): oyuncu ne kadardır rahat?
     *
     * 40 süpürme (~4-5 dk) sürekli düşük korku = tam rehavet. Candy'nin en çok
     * beklediği an budur.
     */
    public float rehavet() {
        return Math.max(0f, Math.min(1f, rehavetSupurme / 40f));
    }

    /** Korkunun 1. farkı (yükseliyor mu, düşüyor mu) — 0,5 nötr. */
    public float egim() {
        if (dolu < 3) return 0.5f;
        return 0.5f + 0.5f * Math.max(-1f, Math.min(1f, (sonKorku(0) - sonKorku(2)) * 3f));
    }

    /** Korkunun 2. farkı (ivme) — 0,5 nötr. */
    public float ivme() {
        if (dolu < 5) return 0.5f;
        float d1 = sonKorku(0) - sonKorku(2);
        float d2 = sonKorku(2) - sonKorku(4);
        return 0.5f + 0.5f * Math.max(-1f, Math.min(1f, (d1 - d2) * 4f));
    }

    public void onar() {
        if (korku == null || korku.length != KAPASITE) korku = new byte[KAPASITE];
        if (etkinlik == null || etkinlik.length != KAPASITE) etkinlik = new byte[KAPASITE];
        if (niyet == null || niyet.length != KAPASITE) niyet = new byte[KAPASITE];
        if (yazImleci < 0 || yazImleci >= KAPASITE) yazImleci = 0;
        if (dolu < 0 || dolu > KAPASITE) dolu = 0;
        if (rehavetSupurme < 0) rehavetSupurme = 0;
    }

    private static byte nicele(float x) {
        if (!Float.isFinite(x)) x = 0.5f;
        int v = Math.round(Math.max(0f, Math.min(1f, x)) * 255f);
        return (byte) v;
    }
}
