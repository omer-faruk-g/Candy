package com.photonpulse.candy.yuz;

import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HaritaPaleti (v9.1.0) — RGB → harita rengi baytı.
 *
 * <p>Vanilla haritası piksel başına 1 bayt tutar: taban renk (64 giriş) × parlaklık
 * (4 kademe). {@link YuzHaritasi} eskiden yalnızca {@code NORMAL} kademeyi kullanıyordu,
 * yani 64 renk; {@link com.photonpulse.candy.client.EkranGonder} bir ekran karesini
 * basacağı için dört kademe de palete giriyor — yaklaşık <b>240 kullanılabilir renk</b>.
 * Fark bir yüzde görülmez, bir ekran görüntüsünde çok görülür.</p>
 *
 * <h3>NEDEN ORTAK SINIF</h3>
 * Aynı dönüşüm iki tarafta gerekiyor: sunucuda yüz haritası, istemcide ekran karesi
 * (kare küçültme istemcide yapılır, sunucuya yalnızca 16 KB hazır bayt gider). Kopya
 * palet iki yerde ayrı ayrı sapardı.
 *
 * <h3>PARLAKLIK NEDEN ELDE HESAPLANIYOR</h3>
 * {@code MapColor.calculateARGBColor} var ama döndürdüğü kanal sırası sürümler arası
 * garanti değil; {@code col} alanının 0xRRGGBB olduğu bu kod tabanında zaten doğrulanmış
 * (yüz haritası yüz gibi görünüyor). {@code modifier} ile çarpmak vanilla'nın kendi
 * formülünün aynısı ve sıra belirsizliği taşımıyor.
 */
public final class HaritaPaleti {

    /** {r, g, b, bayt} — hazırlanana kadar null. */
    private static volatile int[][] palet;

    /** RGB → bayt önbelleği. Bir ekran karesinde en fazla 16384 farklı renk olur. */
    private static final Map<Integer, Byte> onbellek = new ConcurrentHashMap<>();

    /** Paletteki en koyu renk — ekran haritasında kenar bandı için. */
    private static volatile byte enKoyu = 0;

    private HaritaPaleti() {}

    /**
     * Paleti kur. Birden fazla çağrı zararsız; ilk çağrı dışındakiler hemen döner.
     * Kayıt sırasında çağırmak şart değil ({@link #bayt(int)} kendi kendine kuruyor),
     * ama ilk haritanın gecikmesini kayıt zamanına taşıyor.
     */
    public static void hazirla() {
        if (palet != null) return;
        kur();
    }

    private static synchronized void kur() {
        if (palet != null) return;

        List<int[]> liste = new ArrayList<>(256);
        long enKucukParlaklik = Long.MAX_VALUE;
        byte enKoyuAday = 0;

        for (int idx = 1; idx < 64; idx++) {
            MapColor mc;
            try {
                mc = MapColor.byId(idx);
            } catch (Throwable e) {
                continue;
            }
            if (mc == null || mc == MapColor.NONE) continue;

            int rgb = mc.col;                         // 0xRRGGBB taban
            int tr = (rgb >> 16) & 0xFF, tg = (rgb >> 8) & 0xFF, tb = rgb & 0xFF;

            for (MapColor.Brightness parlaklik : MapColor.Brightness.values()) {
                int m = parlaklik.modifier;           // vanilla: 180 / 220 / 255 / 135
                int r = tr * m / 255, g = tg * m / 255, b = tb * m / 255;
                byte bayt;
                try {
                    bayt = mc.getPackedId(parlaklik);
                } catch (Throwable e) {
                    continue;
                }
                liste.add(new int[]{r, g, b, bayt});

                long toplam = (long) r + g + b;
                if (toplam < enKucukParlaklik) {
                    enKucukParlaklik = toplam;
                    enKoyuAday = bayt;
                }
            }
        }

        enKoyu = enKoyuAday;
        palet = liste.toArray(new int[0][]);
    }

    /** Paletteki en koyu renk baytı (ekran haritasının kenar bandı). */
    public static byte koyu() {
        if (palet == null) kur();
        return enKoyu;
    }

    /**
     * 0xRRGGBB → en yakın harita rengi baytı. Önbellekli: önbellek olmadan her piksel
     * için ~240 karşılaştırma dönerdi (bir harita = 4 milyon karşılaştırma).
     */
    public static byte bayt(int rgb) {
        int[][] p = palet;
        if (p == null) {
            kur();
            p = palet;
            if (p == null) return 0;
        }

        int anahtar = rgb & 0xFFFFFF;
        Byte kayitli = onbellek.get(anahtar);
        if (kayitli != null) return kayitli;

        int r = (anahtar >> 16) & 0xFF, g = (anahtar >> 8) & 0xFF, b = anahtar & 0xFF;
        int enYakin = 0;
        long enKucuk = Long.MAX_VALUE;
        for (int[] giris : p) {
            long dr = r - giris[0], dg = g - giris[1], db = b - giris[2];
            long d = dr * dr + dg * dg + db * db;
            if (d < enKucuk) {
                enKucuk = d;
                enYakin = giris[3];
            }
        }

        byte sonuc = (byte) enYakin;
        if (onbellek.size() < 65536) onbellek.put(anahtar, sonuc);
        return sonuc;
    }
}
