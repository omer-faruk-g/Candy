package com.photonpulse.candy.beyin;

import java.util.Random;

/**
 * GolgeArsivi — GÖLGE lobunun belleği: bastırılan bağlantıların mezarlığı.
 *
 * <h3>NE YAPAR</h3>
 * {@link Plastisite#buda} bir sinapsı öldürmeden önce izini buraya gömer (hedef nöron,
 * kaynak, son ağırlık, ölüm süpürmesi). GÖLGE lobu etkinleştikçe
 * {@link Plastisite#norogenez} yeni rastgele bağlantı kurmak yerine <b>eski, bastırılmış
 * deseni yarı güçle diriltir</b>.
 *
 * <h3>NEDEN ANLAMLI</h3>
 * Nörogenez tek başına unutmanın telafisidir: ölen yerine rastgele yeni. Arşivle
 * birlikte, ölen bağlantı gerçekten <i>geri döner</i> — Candy'nin bir zamanlar
 * öğrenip terk ettiği örüntü, hiçbir yeni sebep olmadan yeniden ortaya çıkar.
 * Modun mevcut Bastirma/ShadowTree temasının sinaps düzeyindeki karşılığıdır:
 * <i>bastırılan geri döner.</i>
 *
 * <h3>BOYUT</h3>
 * {@link #KAPASITE} kayıt × 12 byte = 24 KB bellek. Künyeye JSON olarak yazılır; en
 * eski kayıtların üstüne yazan bir halkadır, sınırsız büyümez.
 */
public class GolgeArsivi {

    /** Arşiv halkasının kapasitesi. */
    public static final int KAPASITE = 2048;

    /** Sinapsın ait olduğu nöron. */
    public int[] hedef = new int[KAPASITE];
    /** Sinapsın kaynak adresi (duyu indeksi veya GIRDI_SAYISI+nöron). */
    public int[] kaynak = new int[KAPASITE];
    /** Öldüğü andaki ağırlık. */
    public float[] agirlik = new float[KAPASITE];

    public int yazImleci = 0;
    public int dolu = 0;
    /** Diriltme taraması nereden devam edecek. */
    public transient int diriltImleci = 0;

    /** İzleme: kaç bağlantı geri döndü. */
    public long dirilenToplam = 0L;

    public GolgeArsivi() {}

    /** Ölmekte olan bir sinapsı gömer. */
    public void gom(int hedefNoron, int kaynakAdres, float sonAgirlik) {
        if (hedefNoron < 0 || kaynakAdres < 0) return;
        hedef[yazImleci] = hedefNoron;
        kaynak[yazImleci] = kaynakAdres;
        agirlik[yazImleci] = Float.isFinite(sonAgirlik) ? sonAgirlik : 0f;
        yazImleci = (yazImleci + 1) % KAPASITE;
        if (dolu < KAPASITE) dolu++;
    }

    /**
     * Bastırılanı geri getirir: arşivi dönen imleçle tarar, kaydın hedef nöronunda boş
     * bir slot varsa bağlantıyı <b>yarı güçle</b> diriltir.
     *
     * Yarı güç kasıtlı: geri dönen şey aynısı değil, soluk bir hâlidir. Yeniden işe
     * yararsa Oja onu tekrar güçlendirir; yaramazsa yeniden budanır.
     *
     * @param kota bu çağrıda en fazla kaç diriltme
     * @return dirilen bağlantı sayısı
     */
    public int dirilt(Beyin b, int kota, Random rng) {
        if (dolu == 0 || kota <= 0 || b.agirlik == null) return 0;
        int dirilen = 0;
        int tarama = Math.min(dolu, Math.max(64, kota * 8));
        if (diriltImleci < 0 || diriltImleci >= dolu) diriltImleci = 0;

        for (int t = 0; t < tarama && dirilen < kota; t++) {
            int k = diriltImleci;
            if (++diriltImleci >= dolu) diriltImleci = 0;

            int h = hedef[k];
            if (h < 0 || h + 1 >= b.girisBasi.length || h >= b.noronSayisi) continue;
            if (b.katman[h] < 0) continue;                     // enjekte nöronun sinapsı yok
            int lid = b.lobId[h];
            if (b.loblar != null && lid >= 0 && lid < b.loblar.length
                && b.loblar[lid].koreldi) continue;
            int src = kaynak[k];
            if (src < 0 || src >= Beyin.GIRDI_SAYISI + b.noronSayisi) continue;

            for (int j = b.girisBasi[h]; j < b.girisBasi[h + 1]; j++) {
                if (Math.abs(b.agirlik[j]) > 0.0005f) continue;   // slot dolu
                b.kaynak[j] = src;
                // Yarı güç + küçük gürültü: aynısı değil, hatırlanmış hâli.
                float w = agirlik[k] * 0.5f + (float) ((rng.nextDouble() - 0.5) * 0.02);
                b.agirlik[j] = Float.isFinite(w) ? Math.max(-4f, Math.min(4f, w)) : 0f;
                if (b.sinapsIz != null) b.sinapsIz[j] = 0f;
                dirilen++;
                break;
            }
        }
        dirilenToplam += dirilen;
        return dirilen;
    }

    /** i. en son gömülen bağlantının ağırlık büyüklüğü (0..1) — "şu an neyi bastırıyorum?" */
    public float sonBuyukluk(int i) {
        if (dolu <= i || i < 0) return 0f;
        int idx = ((yazImleci - 1 - i) % KAPASITE + KAPASITE) % KAPASITE;
        return (float) Math.min(1.0, Math.abs(agirlik[idx]) / 0.05);
    }

    /** Arşivin ne kadar dolu olduğu (0..1). */
    public float doluluk() {
        return dolu / (float) KAPASITE;
    }

    /** Gömülü ağırlıkların ortalama büyüklüğü (0..1'e ölçekli). */
    public float ortBuyukluk() {
        if (dolu == 0) return 0f;
        double t = 0;
        for (int i = 0; i < dolu; i++) t += Math.abs(agirlik[i]);
        double o = t / dolu;
        return (float) Math.max(0.0, Math.min(1.0, o / 0.05));
    }

    /**
     * Her lobun arşivdeki payı (0..1) — "en çok neyi bastırdım?"
     *
     * TEK GEÇİŞ: lob başına ayrı tarama 10 x 2048 okuma demekti; kaydın lob'u zaten
     * {@code lobId}'den okunabildiği için tek geçiş yeter.
     *
     * @param cikti uzunluğu lob sayısı kadar, yeniden kullanılan tampon
     */
    public void lobPaylari(Beyin b, float[] cikti) {
        if (cikti == null) return;
        java.util.Arrays.fill(cikti, 0f);
        if (dolu == 0 || b.lobId == null) return;
        for (int i = 0; i < dolu; i++) {
            int h = hedef[i];
            if (h < 0 || h >= b.noronSayisi) continue;
            int lid = b.lobId[h];
            if (lid >= 0 && lid < cikti.length) cikti[lid] += 1f;
        }
        for (int i = 0; i < cikti.length; i++) cikti[i] /= dolu;
    }

    public void onar() {
        if (hedef == null || hedef.length != KAPASITE) hedef = new int[KAPASITE];
        if (kaynak == null || kaynak.length != KAPASITE) kaynak = new int[KAPASITE];
        if (agirlik == null || agirlik.length != KAPASITE) agirlik = new float[KAPASITE];
        for (int i = 0; i < KAPASITE; i++) {
            if (hedef[i] < 0) hedef[i] = 0;
            if (kaynak[i] < 0) kaynak[i] = 0;
            if (!Float.isFinite(agirlik[i])) agirlik[i] = 0f;
        }
        if (yazImleci < 0 || yazImleci >= KAPASITE) yazImleci = 0;
        if (dolu < 0 || dolu > KAPASITE) dolu = 0;
        diriltImleci = 0;
        if (dirilenToplam < 0) dirilenToplam = 0L;
    }
}
