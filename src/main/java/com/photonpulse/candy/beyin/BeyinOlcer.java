package com.photonpulse.candy.beyin;

import com.photonpulse.candy.Candy;

/**
 * BeyinOlcer — beynin tick maliyetini ölçer. YALNIZCA {@code -Dcandy.debug} ile aktif.
 *
 * Hedef bütçe: oyuncu başına &lt; 50 µs/tick. Karşılaştırma: DerinAnaliz'in entity
 * taramaları oyuncu başına ~0,4-1,2 ms alıyor, yani beyin en pahalı sistemin onda biri
 * olmalı. Ölçüm olmadan bu iddia doğrulanamaz.
 */
public class BeyinOlcer {

    private static final boolean AKTIF = Boolean.getBoolean("candy.debug");
    private static final long RAPOR_ARALIGI_NS = 60_000_000_000L;   // 60 sn

    private static long toplamNs = 0L;
    private static long cagriSayisi = 0L;
    private static long islenenNoron = 0L;
    private static long sonRaporNs = 0L;
    private static long baslangicNs = 0L;

    public static boolean aktif() {
        return AKTIF;
    }

    public static void basla() {
        if (!AKTIF) return;
        baslangicNs = System.nanoTime();
    }

    public static void bitir(int noron) {
        if (!AKTIF || baslangicNs == 0L) return;
        toplamNs += System.nanoTime() - baslangicNs;
        cagriSayisi++;
        islenenNoron += noron;
        baslangicNs = 0L;
    }

    /** Periyodik özet — çağıran her tick çağırabilir, kendi aralığını yönetir. */
    public static void raporla(int aktifBeyin) {
        if (!AKTIF || cagriSayisi == 0) return;
        long simdi = System.nanoTime();
        if (sonRaporNs == 0L) { sonRaporNs = simdi; return; }
        if (simdi - sonRaporNs < RAPOR_ARALIGI_NS) return;
        sonRaporNs = simdi;

        double ortUs = (toplamNs / (double) cagriSayisi) / 1000.0;
        double noronCagri = islenenNoron / (double) cagriSayisi;
        Candy.LOG.info("[Candy] BeyinOlcer: {} cagri, ort {} us/cagri, {} noron/cagri, {} aktif beyin",
            cagriSayisi, String.format("%.1f", ortUs),
            String.format("%.0f", noronCagri), aktifBeyin);
        toplamNs = 0L;
        cagriSayisi = 0L;
        islenenNoron = 0L;
    }

    public static void sifirla() {
        toplamNs = 0L;
        cagriSayisi = 0L;
        islenenNoron = 0L;
        sonRaporNs = 0L;
        baslangicNs = 0L;
    }
}
