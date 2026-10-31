package com.photonpulse.candy.beyin;

/**
 * KararIzi — YANKI lobunun belleği: "daha önce verdiğim kararlar ne getirdi?"
 *
 * <h3>NE TUTAR</h3>
 * Son {@link #KAPASITE} kararın halkası (eylem, ödül, öngörü hatası) + eylem başına
 * çevrimiçi istatistik: ortalama ödül, ortalama hata, sıklık ve <b>tutarsızlık</b>
 * (aynı eylemin farklı zamanlarda farklı sonuç verme derecesi).
 *
 * <h3>NEDEN VARYANS ÖNEMLİ</h3>
 * "Bu eylem işe yaradı mı?" sorusunun cevabı ortalama değildir. Ortalaması iyi ama
 * varyansı yüksek bir eylem, Candy'nin oyuncuyu HENÜZ ANLAMADIĞI anlamına gelir —
 * öğrenmenin hızlanması gereken yer tam orasıdır. Ortalaması iyi ve varyansı düşük bir
 * eylem ise çözülmüş demektir; orada öğrenmeye devam etmek boşa enerjidir.
 *
 * <h3>ÇEVRİMİÇİ VARYANS</h3>
 * Welford yerine EMA tabanlı varyans kullanılır: eski kararların ağırlığı zamanla
 * sönmeli. Oyuncu değişir; üç saat önceki tutarsızlık bugünkü kararı bağlamamalı.
 *
 * Künyeye JSON olarak yazılır — küçüktür (~1 KB) ve dünyalar arasında taşınması
 * anlamlıdır: "bunu sende zaten denemiştim".
 */
public class KararIzi {

    /** Halka boyu — son bu kadar karar ayrıntısıyla saklanır. */
    public static final int KAPASITE = 32;
    /** ZihinselProva'nın eylem sayısı. */
    public static final int EYLEM_SAYISI = 6;
    /** İç duyuya yazılan son karar sayısı. */
    public static final int SON_KARAR = 6;

    // ── Halka ──
    public byte[] eylem = new byte[KAPASITE];
    public float[] odul = new float[KAPASITE];
    public float[] hata = new float[KAPASITE];
    public int yazImleci = 0;
    public int dolu = 0;

    // ── Eylem başına çevrimiçi istatistik (EMA) ──
    public float[] ortOdul = new float[EYLEM_SAYISI];
    public float[] ortHata = new float[EYLEM_SAYISI];
    public float[] varOdul = new float[EYLEM_SAYISI];
    public int[] sayi = new int[EYLEM_SAYISI];

    /** EMA hızı — ~30 kararda yarılanır. */
    private static final float EMA = 0.10f;

    public KararIzi() {}

    /**
     * Bir kararın sonucunu kaydeder.
     *
     * @param e     eylem sırası (0..5)
     * @param odul  gözlenen ödül (-0.5 .. 1.5 ölçeği)
     * @param hata  öngörü hatası (mutlak korku farkı)
     */
    public void kaydet(int e, float odul, float hata) {
        if (e < 0 || e >= EYLEM_SAYISI) return;
        if (!Float.isFinite(odul)) odul = 0f;
        if (!Float.isFinite(hata)) hata = 0f;

        eylem[yazImleci] = (byte) e;
        this.odul[yazImleci] = odul;
        this.hata[yazImleci] = hata;
        yazImleci = (yazImleci + 1) % KAPASITE;
        if (dolu < KAPASITE) dolu++;

        float fark = odul - ortOdul[e];
        ortOdul[e] += EMA * fark;
        // EMA varyans: eski tutarsızlık zamanla unutulur
        varOdul[e] = (1f - EMA) * (varOdul[e] + EMA * fark * fark);
        ortHata[e] += EMA * (hata - ortHata[e]);
        if (sayi[e] < Integer.MAX_VALUE - 1) sayi[e]++;
    }

    /** i. en yeni kararın ödülü (0 = en son). Kayıt yoksa 0. */
    public float sonOdul(int i) {
        if (dolu <= i) return 0f;
        int idx = ((yazImleci - 1 - i) % KAPASITE + KAPASITE) % KAPASITE;
        return odul[idx];
    }

    /** i. en yeni kararın eylemi (0 = en son). Kayıt yoksa -1. */
    public int sonEylem(int i) {
        if (dolu <= i) return -1;
        int idx = ((yazImleci - 1 - i) % KAPASITE + KAPASITE) % KAPASITE;
        return eylem[idx];
    }

    /**
     * PİŞMANLIK: geçmişte en iyi bilinen eylemin ödülü ile fiilen seçilenlerin
     * ortalaması arasındaki fark (0..1). "Daha iyisini yapabilirdim."
     */
    public float pismanlik() {
        if (dolu == 0) return 0f;
        float enIyi = Float.NEGATIVE_INFINITY;
        for (int e = 0; e < EYLEM_SAYISI; e++) {
            if (sayi[e] > 0 && ortOdul[e] > enIyi) enIyi = ortOdul[e];
        }
        if (!Float.isFinite(enIyi)) return 0f;
        float t = 0;
        for (int i = 0; i < dolu; i++) t += odul[i];
        float secilenOrt = t / dolu;
        return b01((enIyi - secilenOrt) / 1.5f);
    }

    /** Genel tutarsızlık (0..1): kararlarımın sonucu ne kadar öngörülemez? */
    public float tutarsizlik() {
        float t = 0;
        int n = 0;
        for (int e = 0; e < EYLEM_SAYISI; e++) {
            if (sayi[e] > 1) { t += varOdul[e]; n++; }
        }
        return n == 0 ? 0.5f : b01((float) Math.sqrt(t / n) / 0.6f);
    }

    /** Toplam karar sayısı. */
    public int toplamKarar() {
        int t = 0;
        for (int s : sayi) t += s;
        return t;
    }

    public void onar() {
        if (eylem == null || eylem.length != KAPASITE) eylem = new byte[KAPASITE];
        if (odul == null || odul.length != KAPASITE) odul = new float[KAPASITE];
        if (hata == null || hata.length != KAPASITE) hata = new float[KAPASITE];
        if (ortOdul == null || ortOdul.length != EYLEM_SAYISI) ortOdul = new float[EYLEM_SAYISI];
        if (ortHata == null || ortHata.length != EYLEM_SAYISI) ortHata = new float[EYLEM_SAYISI];
        if (varOdul == null || varOdul.length != EYLEM_SAYISI) varOdul = new float[EYLEM_SAYISI];
        if (sayi == null || sayi.length != EYLEM_SAYISI) sayi = new int[EYLEM_SAYISI];
        for (int i = 0; i < KAPASITE; i++) {
            if (eylem[i] < 0 || eylem[i] >= EYLEM_SAYISI) eylem[i] = 0;
            if (!Float.isFinite(odul[i])) odul[i] = 0f;
            if (!Float.isFinite(hata[i])) hata[i] = 0f;
        }
        for (int e = 0; e < EYLEM_SAYISI; e++) {
            if (!Float.isFinite(ortOdul[e])) ortOdul[e] = 0f;
            if (!Float.isFinite(ortHata[e])) ortHata[e] = 0f;
            if (!Float.isFinite(varOdul[e]) || varOdul[e] < 0f) varOdul[e] = 0f;
            if (sayi[e] < 0) sayi[e] = 0;
        }
        if (yazImleci < 0 || yazImleci >= KAPASITE) yazImleci = 0;
        if (dolu < 0 || dolu > KAPASITE) dolu = 0;
    }

    static float b01(float x) {
        if (!Float.isFinite(x)) return 0.5f;
        return Math.max(0f, Math.min(1f, x));
    }
}
