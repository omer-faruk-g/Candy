package com.photonpulse.candy.core;

import com.photonpulse.candy.Candy;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * JsonDepo — dört JSON manager'ının ortak <b>atomik</b> yazıcısı (v8).
 *
 * <h3>DÜZELTİLEN GERÇEK HATA</h3>
 * {@code PersonalityManager}, {@code TreeGrowth}, {@code SimulationManager} ve
 * {@code ShadowTreeManager} şunu yapıyordu:
 * <pre>Files.writeString(canliDosya, GSON.toJson(nesne));</pre>
 * İki sorun:
 * <ul>
 *   <li><b>Atomik değil.</b> Yazım canlı dosyanın üstüne yapılıyor; güç kesintisi ya da
 *       çökme hâlinde dosya yarım kalır ve bir sonraki açılışta <i>tüm</i> oyuncu hafızası
 *       okunamaz olur. Yedek yok, temp yok, rename yok.</li>
 *   <li><b>Ana thread'de senkron.</b> Disk I/O tick'i bekletir; autosave fazları
 *       ({@code %6000} → 0/1500/3000/4500) tam bu yüzden kaydırılmıştı.</li>
 * </ul>
 * Doğru kalıp modun kendi içinde zaten vardı — {@code beyin/BeyinDeposu}: ana thread'de
 * kısa bir anlık görüntü, gerçek yazım daemon thread'inde, ardından {@code ATOMIC_MOVE}.
 *
 * <h3>NEDEN BeyinDeposu'NUN TAMPON HAVUZU GEREKMİYOR</h3>
 * Beyin, yazıcıya <b>canlı nesne</b> veriyor, bu yüzden kopya havuzu şart. Buradaki
 * manager'lar zaten ana thread'de {@code GSON.toJson(...)} çağırıp <b>String</b> üretiyor;
 * String değişmezdir, yani anlık görüntü bedava ve yarım yazım riski yok. Değişen tek şey
 * String'in diske kimin yazdığı.
 *
 * <h3>SIRA GARANTİSİ</h3>
 * Tek thread'li executor → aynı dosyaya yapılan yazımlar FIFO sırada işlenir; yeni kayıt
 * eskisinin üstüne yazar, tersi olamaz. Kapanışta {@link #kapanisBekle()} bekleyen her
 * yazımın bitmesini bekler (veri kaybı olmasın).
 */
public final class JsonDepo {

    private JsonDepo() {}

    private static final ExecutorService YAZICI = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "candy-json-yazici");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean kapandi = false;

    /**
     * JSON'u arka planda atomik olarak yazar. Ana thread'de yalnızca kuyruğa ekler.
     *
     * @param hedef canlı dosya yolu
     * @param json  ana thread'de üretilmiş değişmez anlık görüntü
     */
    public static void yaz(Path hedef, String json) {
        if (hedef == null || json == null) return;
        if (kapandi) { yazSenkron(hedef, json, "kapanis sonrasi"); return; }
        try {
            YAZICI.execute(() -> yazSenkron(hedef, json, "arka plan"));
        } catch (Throwable t) {
            // Kuyruk reddetti (kapanış yarışı) — kaybetmemek için burada yaz.
            yazSenkron(hedef, json, "kuyruk reddi");
        }
    }

    private static void yazSenkron(Path hedef, String json, String kaynak) {
        Path tmp = hedef.resolveSibling(hedef.getFileName() + ".tmp");
        try {
            Path ebeveyn = hedef.getParent();
            if (ebeveyn != null) Files.createDirectories(ebeveyn);
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, hedef, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, hedef, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            Candy.LOG.error("[Candy] JSON yazılamadı ({}, {}): {}",
                hedef.getFileName(), kaynak, t.getMessage());
            try { Files.deleteIfExists(tmp); } catch (Throwable ignored) {}
        }
    }

    /** Sunucu kapanışında: bekleyen yazımların bitmesini bekle. */
    public static void kapanisBekle() {
        kapandi = true;
        try {
            YAZICI.shutdown();
            if (!YAZICI.awaitTermination(5, TimeUnit.SECONDS)) {
                Candy.LOG.warn("[Candy] JSON yazıcısı zamanında bitmedi");
            }
        } catch (Throwable ignored) {}
    }
}
