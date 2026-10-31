package com.photonpulse.candy.beyin.vagus;

/**
 * VagusSezgi — modelin tek çevrimde ürettiği sezgi. Değişmez.
 *
 * <h3>{@code tahmin} NEDİR</h3>
 * Kurulun seçeceği eylemin <b>normalize sonucu</b> (0..1), tam olarak
 * {@code Kurul.sonucOlcum}'un ölçtüğü ölçekte: {@code 0,25} = korkuda değişim yok,
 * {@code 0,5} ≈ +5, {@code 1,0} = +15 ve üstü, {@code 0,0} = -5 ve altı. Vagus bu sayı
 * üzerinden <b>oy kullanmayan 12. üye</b> gibi puanlanır.
 *
 * <h3>{@code guven} NEDEN KULLANILMIYOR</h3>
 * Yalnızca loglanır. Ölçümde model, yarım kalmış bozuk bir hipoteze {@code 0,95},
 * düzgün bir cevaba {@code 0,21} verdi — beyan güvenilir değil. "Yaklaşma" ölçütü
 * {@link com.photonpulse.candy.beyin.UyeSicili#kalibrasyon()} (Brier), modelin beyanı değil.
 *
 * @param hipotez    serbest metin — YALNIZCA loglanır, oyuncuya asla gösterilmez
 * @param tahmin     0..1 (clamp'li)
 * @param guven      0..1 (clamp'li, karara girmez)
 * @param surukleyen modelin işaret ettiği etken (kısa etiket)
 * @param tick       üretildiği dünya tick'i — bayatlık bu alandan ölçülür
 */
public record VagusSezgi(String hipotez, double tahmin, double guven,
                         String surukleyen, long tick) {

    /**
     * Clamp'li üretici. Ollama şemanın <b>tipini</b> uygular ama {@code minimum}/
     * {@code maximum}'u UYGULAMAZ (şema 0..1 derken ölçümde {@code 60} döndü) —
     * bu yüzden aralık kontrolü Java tarafında zorunludur.
     */
    public static VagusSezgi olustur(String hipotez, double tahmin, double guven,
                                     String surukleyen, long tick) {
        return new VagusSezgi(
            kirp(hipotez, 240), b01(tahmin, 0.25), b01(guven, 0.0),
            kirp(surukleyen, 48), tick);
    }

    /** Sezgi bu kadar tick'ten eskiyse puanlanmaz — ölçtüğü durum artık geçerli değil. */
    public boolean bayat(long simdi, long omur) {
        return simdi - tick > omur || simdi < tick;
    }

    private static double b01(double d, double varsayilan) {
        if (!Double.isFinite(d)) return varsayilan;
        return Math.max(0.0, Math.min(1.0, d));
    }

    private static String kirp(String s, int tavan) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').replace('\r', ' ').trim();
        return (t.length() <= tavan) ? t : t.substring(0, tavan);
    }
}
