package com.photonpulse.candy.client;

/**
 * AltyaziState (v8.2, istemci) — Candy'nin altyazı kanalı.
 *
 * <h3>NİYE AYRI BİR KANAL</h3>
 * Mod zaten chat'e yazıyor ({@code FakeMesajlar}, {@code CandyChatOkur}) ve HUD'a
 * flash çiziyor. Ama chat'e birkaç saat sonra güvenmeyi bırakmış oyuncu <b>chat'e
 * bakmıyor</b> — Candy'nin oraya yazdığı her şey filtrelenmiş bir kanala düşüyor.
 * Altyazı kutusu oyuncunun hiç şüphelenmediği yer: oraya yalnızca <i>oyunun kendi
 * sesleri</i> düşer. From The Fog'un altyazı enjeksiyonu bu yüzden çalışıyor.
 *
 * <p>Çizim {@code InGameHudMixin} içinde, vanilla altyazı kutusunun bulunduğu yerde
 * (sağ alt, hotbar üstü) ve vanilla görünümüyle yapılıyor. Vanilla {@code SubtitlesHud}
 * sınıfına mixin atmıyoruz: o sınıf kendi ses olaylarını yönetiyor, araya girmek
 * gerçek altyazıları bozma riski taşır. Kendi kutumuzu çizmek hem tam kontrol veriyor
 * hem de oyuncunun altyazıları kapalıysa bile görünüyor.</p>
 */
public final class AltyaziState {

    private AltyaziState() {}

    private static volatile long baslangicMs = 0L;
    private static volatile long sureMs = 0L;
    private static volatile int indeks = -1;

    /** Son 400 ms'de yumuşak sönüm — vanilla altyazısı da böyle kaybolur. */
    private static final long SONUM_MS = 400L;

    public static void tetikle(int idx, int sure) {
        indeks = idx;
        sureMs = Math.max(0, Math.min(30_000, sure));
        baslangicMs = System.currentTimeMillis();
    }

    public static void sifirla() {
        indeks = -1;
        sureMs = 0L;
        baslangicMs = 0L;
    }

    public static boolean aktif() {
        return indeks >= 0 && sureMs > 0
            && (System.currentTimeMillis() - baslangicMs) < sureMs;
    }

    public static int indeks() { return indeks; }

    /** Opaklık 0..1 — sonda yumuşak söner. */
    public static float alfa() {
        long kalan = sureMs - (System.currentTimeMillis() - baslangicMs);
        if (kalan <= 0) return 0f;
        if (kalan < SONUM_MS) return kalan / (float) SONUM_MS;
        return 1f;
    }
}
