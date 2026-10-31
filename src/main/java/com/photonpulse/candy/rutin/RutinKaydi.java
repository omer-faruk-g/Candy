package com.photonpulse.candy.rutin;

import com.photonpulse.candy.core.TruePersonality;

/**
 * RutinKaydi (v8.2) — davranış imzasının <b>çözülebilir</b> hâli.
 *
 * <h3>NE PROBLEMİ ÇÖZÜYOR</h3>
 * {@code TruePersonality.davranisImzalari} bir hash ring'i: "oyuncu kendini kaç kez
 * tekrarladı" sorusunu cevaplıyor ama "<i>neyi</i> tekrarladı" sorusunu cevaplayamıyor,
 * çünkü hash tek yönlü. Candy oyuncunun rutinini ona geri oynatacaksa (aynı chunk, aynı
 * saat dilimi, aynı aktivite) bileşenlerin kendisi gerekiyor.
 *
 * <h3>PAKET DÜZENİ (long, 56 bit kullanılıyor)</h3>
 * <pre>
 *   bit  0..23  chunkX   (24 bit, işaretli — ±8,38M chunk; dünya sınırı ±1,88M)
 *   bit 24..47  chunkZ   (24 bit, işaretli)
 *   bit 48..50  saatDilimi (0..5)
 *   bit 51..53  aktiviteIdx (0..3)
 *   bit 54      DOLU biti — 0L "kayıt yok" demek olabilsin diye
 * </pre>
 * Paket 0 = bilinmeyen. v8.1 kayıtlarında bu dizi hiç yok; Gson null bırakır,
 * {@code imzalariOnar()} sıfırla doldurur, yeni imzalar geldikçe dolar. Göç adımı yok.
 */
public final class RutinKaydi {

    private RutinKaydi() {}

    /** Aktivite adları — {@code DerinAnaliz.baskinAktivite} çıktısıyla aynı küme. */
    public static final String[] AKTIVITELER = {"kesfet", "kac", "saldir", "don"};

    private static final long DOLU = 1L << 54;
    private static final int KOORD_BIT = 24;
    private static final long KOORD_MASKE = (1L << KOORD_BIT) - 1L;

    public static int aktiviteIndeksi(String ad) {
        if (ad == null) return 0;
        for (int i = 0; i < AKTIVITELER.length; i++) if (AKTIVITELER[i].equals(ad)) return i;
        return 0;
    }

    /** Bileşenleri tek long'a paketler. */
    public static long paketle(int chunkX, int chunkZ, int saatDilimi, String aktivite) {
        long x = ((long) chunkX) & KOORD_MASKE;
        long z = ((long) chunkZ) & KOORD_MASKE;
        long s = Math.max(0, Math.min(5, saatDilimi)) & 0x7L;
        long a = aktiviteIndeksi(aktivite) & 0x7L;
        return DOLU | x | (z << KOORD_BIT) | (s << 48) | (a << 51);
    }

    public static boolean dolu(long paket) { return (paket & DOLU) != 0L; }

    /** 24 bitlik alanı işaretli int'e geri açar. */
    private static int isaretliAc(long ham) {
        int v = (int) (ham & KOORD_MASKE);
        // En üst bit 1 ise negatif — işaret uzatması
        if ((v & (1 << (KOORD_BIT - 1))) != 0) v |= ~((1 << KOORD_BIT) - 1);
        return v;
    }

    public static int chunkX(long paket) { return isaretliAc(paket); }
    public static int chunkZ(long paket) { return isaretliAc(paket >>> KOORD_BIT); }
    public static int saatDilimi(long paket) { return (int) ((paket >>> 48) & 0x7L); }
    public static int aktiviteIdx(long paket) { return (int) ((paket >>> 51) & 0x7L); }

    public static String aktivite(long paket) {
        int i = aktiviteIdx(paket);
        return (i >= 0 && i < AKTIVITELER.length) ? AKTIVITELER[i] : AKTIVITELER[0];
    }

    /**
     * EN SIK rutin — ring'de hash'i en çok tekrarlanan slotun paketi.
     *
     * <p>Hash üzerinden sayıyor, paket üzerinden değil: tekrar ölçütü diskteki eski
     * imzalarla uyumlu kalmak zorunda ve hash formülü {@code DerinAnaliz}'de donmuş
     * durumda. Paket yalnızca "o hash neydi" sorusunu cevaplıyor.</p>
     *
     * @return paket, ya da yeterli kanıt yoksa {@code 0L}
     */
    public static long enSik(TruePersonality tp, int minTekrar) {
        if (tp == null) return 0L;
        int[] h = tp.davranisImzalari;
        long[] p = tp.rutinPaketleri;
        if (h == null || p == null) return 0L;
        int n = Math.min(tp.imzaAdet, Math.min(h.length, p.length));
        if (n <= 0) return 0L;

        long enIyi = 0L;
        int enCok = 0;
        for (int i = 0; i < n; i++) {
            if (!dolu(p[i])) continue;      // paketi bilinmeyen slot sayılmaz
            int c = 0;
            for (int j = 0; j < n; j++) if (h[j] == h[i]) c++;
            if (c > enCok) { enCok = c; enIyi = p[i]; }
        }
        return enCok >= minTekrar ? enIyi : 0L;
    }

    /** Kaç tekrarla? {@link #enSik} ile aynı tarama, sayıyı döner (tabela atfı için). */
    public static int enSikTekrar(TruePersonality tp) {
        if (tp == null) return 0;
        int[] h = tp.davranisImzalari;
        long[] p = tp.rutinPaketleri;
        if (h == null || p == null) return 0;
        int n = Math.min(tp.imzaAdet, Math.min(h.length, p.length));
        int enCok = 0;
        for (int i = 0; i < n; i++) {
            if (!dolu(p[i])) continue;
            int c = 0;
            for (int j = 0; j < n; j++) if (h[j] == h[i]) c++;
            if (c > enCok) enCok = c;
        }
        return enCok;
    }
}
