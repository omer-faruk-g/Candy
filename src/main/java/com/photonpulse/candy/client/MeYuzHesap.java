package com.photonpulse.candy.client;

/**
 * MeYuzHesap — {@link MeYuz}'un SAF piksel matematiği (Minecraft bağımlılığı YOK).
 *
 * <p>Skin'den yüzü çıkarıp hedef doku boyutuna ölçekleme mantığı burada, MC sınıflarından
 * ayrı tutulur; böylece headless olarak (gerçek client olmadan) birim testi yapılabilir —
 * asıl bug riski taşıyan yer burasıdır. GPU'ya yükleme {@link MeYuz}'ta kalır.</p>
 */
public final class MeYuzHesap {

    private MeYuzHesap() {}

    /**
     * Skin ARGB dizisinden, {@code w×h} boyutunda bir yüz dokusu üretir.
     *
     * <p>Minecraft skin düzeni: yüzün BAZ katmanı (8,8)-(15,15), ŞAPKA/overlay katmanı
     * (40,8)-(47,15). Şapka pikseli opaksa üstte gösterilir. Sonuç her zaman opaktır.</p>
     *
     * @param skin   skin pikselleri, ARGB, satır-öncelikli (uzunluk ≥ skinW*64)
     * @param skinW  skin genişliği (tipik 64)
     * @param w,h    hedef doku boyutu (tipik 16×16)
     */
    public static int[] yuzOlustur(int[] skin, int skinW, int w, int h) {
        int[] out = new int[w * h];
        if (skin == null || skinW < 64 || w <= 0 || h <= 0) return out;
        for (int y = 0; y < h; y++) {
            int fy = Math.min(7, y * 8 / h);
            for (int x = 0; x < w; x++) {
                int fx = Math.min(7, x * 8 / w);
                int baz   = skin[(8 + fy) * skinW + (8 + fx)];
                int sapka = skin[(8 + fy) * skinW + (40 + fx)];
                int renk = (((sapka >>> 24) & 0xFF) > 16) ? sapka : baz;
                out[y * w + x] = renk | 0xFF000000;      // her zaman opak
            }
        }
        return out;
    }
}
