package com.photonpulse.candy.beyin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.photonpulse.candy.Candy;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * BeyinDeposu — beynin kalıcılığı. "Beyin o cihazda kalacak."
 *
 * KRİTİK FARK: modun diğer tüm verisi dünya-başınadır
 * ({@code <dünya>/candy/*.json}) — yeni dünya = sıfırdan başlayan Candy. Beyin ise
 * CİHAZ-başınadır:
 * <pre>
 *   .minecraft/candy_beyin/
 *     beyin_&lt;uuid&gt;.json       ~20 KB   künye (loblar, harita, öz-model, dünyalar)
 *     beyin_&lt;uuid&gt;.baglanti   ~300 KB  gzip ikili bağlantı haritası
 * </pre>
 * Oyuncu dünyayı silse, {@code saves/} klasörünü temizlese bile beyin durur. Onu silmek
 * için bu klasörü bulup silmesi gerekir — ve orada bulacağı şey soğuk bir JSON ile
 * okunamayan bir ikili dosyadır.
 *
 * DAYANIKLILIK: modun mevcut 4 manager'ı senkron + atomik olmayan yazım yapıyor (güç
 * kesintisinde dosya bozulur). Beyin bunu düzeltir: ana thread'de yalnızca kısa bir
 * kopya, gerçek yazım daemon thread'inde, ardından {@code ATOMIC_MOVE}.
 */
public class BeyinDeposu {

    /** Künye için AYRI Gson — pretty-printing KAPALI (boyut 3× fark eder). */
    private static final Gson GSON = new GsonBuilder().create();

    private static Path klasor = null;
    private static final ExecutorService YAZICI = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "candy-beyin-yazici");
        t.setDaemon(true);
        return t;
    });

    /**
     * SNAPSHOT TAMPON HAVUZU (v6).
     *
     * Eskiden TEK bir {@code tampon} ve TEK bir {@code yaziyor} bayragi vardi. Bu tasarim
     * TEK beyin icin dogruydu ama kurulda iki sessiz felakete yol acti:
     *   1) Ayni anda birden cok beyin kaydedilince {@code yaziyor} yalnizca ILKINI
     *      gecirir, kalan 10 uye SESSIZCE ATLANIR -> veri kaybi.
     *   2) Paylasilan {@code tampon}, arka plan yazici onu okurken ana thread ikinci bir
     *      kayit icin uzerine yazarsa -> diske YARIM/KARISIK beyin.
     *
     * Cozum: kucuk bir tampon havuzu. Her kayit havuzdan bir tampon alir, ana thread'de
     * doldurur, yaziciya verir; yazici bitince havuza iade eder. Havuz bossa (arka plan
     * yetisemiyor) PERIYODIK kayit atlanir (gelecek periyot yazar); KRITIK kayit
     * (ayrilma/kapanis) taze tahsisle mutlaka yazilir. Boylece ne veri kaybi ne cop patlamasi.
     */
    private static final int HAVUZ_MAX = 6;
    private static final java.util.concurrent.ConcurrentLinkedQueue<Beyin> havuz =
        new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final java.util.concurrent.atomic.AtomicInteger tahsisli =
        new java.util.concurrent.atomic.AtomicInteger(0);

    private BeyinDeposu() {}

    public static void sunucuBaslat() {
        try {
            klasor = FabricLoader.getInstance().getGameDir().resolve("candy_beyin");
            Files.createDirectories(klasor);
            Candy.LOG.info("[Candy] beyin deposu: {}", klasor);
        } catch (Throwable t) {
            klasor = null;
            Candy.LOG.error("[Candy] beyin deposu açılamadı — beyin bellekte kalacak", t);
        }
    }

    /**
     * Dosya adlari — UYE 0 ESKI ADI KORUR.
     *
     * {@code beyin_<uuid>.json} / {@code .baglanti} uye 0'in dosyalaridir; digerleri
     * {@code _u1.., _u10} ekiyle yanlarina yazilir. Bu sayede mevcut oyuncularin var olan
     * beyni GOVDE olarak dogrudan yuklenir — kurul, onun etrafinda dogar. Ayri bir goc
     * adimi, yeniden adlandirma ya da veri kaybi yok.
     */
    private static String ek(int uye) { return (uye <= 0) ? "" : "_u" + uye; }

    private static Path kunyeYolu(UUID id, int uye) {
        return klasor.resolve("beyin_" + id + ek(uye) + ".json");
    }

    private static Path baglantiYolu(UUID id, int uye) {
        return klasor.resolve("beyin_" + id + ek(uye) + ".baglanti");
    }

    /**
     * Diskten yükler. Dosya yoksa/bozuksa null döner (çağıran sıfırdan inşa eder).
     */
    public static Beyin yukle(UUID id) {
        return yukle(id, 0);
    }

    public static Beyin yukle(UUID id, int uye) {
        if (klasor == null) return null;
        Path kunye = kunyeYolu(id, uye);
        Path baglanti = baglantiYolu(id, uye);
        if (!Files.exists(kunye) || !Files.exists(baglanti)) return null;
        try {
            Beyin b = GSON.fromJson(Files.readString(kunye), Beyin.class);
            if (b == null) return null;
            b.oyuncuId = id;
            // Dosya adi otoritedir: kunye icindeki uyeNo bozuksa bile slot dogru kalir,
            // yoksa iki uye ayni sicili paylasir ve komite sessizce kendini kopyalar.
            b.uyeNo = uye;
            Kurul.mizacUygula(b, uye);

            boolean baglantiSaglam = false;
            try (InputStream in = new BufferedInputStream(Files.newInputStream(baglanti), 32768)) {
                baglantiSaglam = IkiliKodlayici.oku(b, in);
            } catch (Throwable t) {
                baglantiSaglam = false;
            }

            // GOC YOLU: baglanti okunamadi VEYA topoloji degisti (lob sayisi/olcek/sema).
            // Eskiden burada null donuluyordu ve KUNYE DE COPE GIDIYORDU — oyuncu modeli,
            // Q basligi, oz-model ve dunya gecmisi bosuna kaybediliyordu.
            if (!baglantiSaglam || !BeyinGocu.topolojiUyumlu(b)) {
                Candy.LOG.info("[Candy] üye {} topolojisi degisti — kunye goc ediyor: {}", uye, id);
                Beyin taze = BeyinInsaci.insaEt(id, uye);
                BeyinGocu.tasi(b, taze);
                if (!taze.onar()) return null;
                return taze;
            }

            if (!b.onar()) {
                Candy.LOG.warn("[Candy] beyin yapısı tutarsız, yeniden inşa: {}", id);
                return null;
            }
            Candy.LOG.debug("[Candy] üye {} yüklendi: {} nöron, {} sinaps, {} süpürme geçmişi",
                uye, b.noronSayisi, b.sinapsSayisi, b.toplamSupurme);
            return b;
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] beyin yüklenemedi ({}), yeniden inşa edilecek: {}", id, t.getMessage());
            return null;
        }
    }

    /**
     * Kaydeder. Ana thread'de yalnizca dizi kopyasi + kunye JSON'u alinir (~1-2 ms);
     * gzip + serilestirme arka planda yapilir ve atomik olarak yerine tasinir.
     *
     * @param senkron kapanis/ayrilmada true — veri kaybi olmamasi icin havuz bekletilmez,
     *                taze tahsisle mutlaka yazilir; ayrica cagiran thread'de tamamlanir.
     */
    public static void kaydet(Beyin b, boolean senkron) {
        if (klasor == null || b == null || b.oyuncuId == null) return;
        if (b.aktiflik == null || b.kaynak == null || b.agirlik == null) return;
        final UUID id = b.oyuncuId;
        final int uye = Math.max(0, b.uyeNo);

        Beyin anlik = havuzAl(b);
        if (anlik == null) {
            // Havuz dolu: periyodik kayit ATLA (gelecek periyot yazar). Kritik kayitta
            // (senkron) mutlaka yaz — taze bir kopya tahsis et.
            if (!senkron) return;
            anlik = yeniTampon(b);
        }
        doldur(anlik, b);

        final String json;
        try {
            json = GSON.toJson(b);
        } catch (Throwable t) {
            havuzBirak(anlik);
            Candy.LOG.error("[Candy] beyin kopyalanamadi: {} uye {}", id, uye, t);
            return;
        }

        final Beyin snap = anlik;
        Runnable is = () -> {
            try {
                Path kunye = kunyeYolu(id, uye);
                Path baglanti = baglantiYolu(id, uye);
                Path tmpK = kunye.resolveSibling(kunye.getFileName() + ".tmp");
                Path tmpB = baglanti.resolveSibling(baglanti.getFileName() + ".tmp");

                Files.writeString(tmpK, json);
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmpB), 32768)) {
                    IkiliKodlayici.yaz(snap, out);
                }
                tasi(tmpK, kunye);
                tasi(tmpB, baglanti);
            } catch (Throwable t) {
                Candy.LOG.error("[Candy] beyin yazilamadi: {} uye {}", id, uye, t);
            } finally {
                havuzBirak(snap);
            }
        };

        if (senkron) is.run();
        else {
            try { YAZICI.submit(is); }
            catch (Throwable t) { havuzBirak(snap); }   // kapanis sonrasi reddedilirse sizinti olmasin
        }
    }

    private static void tasi(Path kaynak, Path hedef) throws IOException {
        try {
            Files.move(kaynak, hedef, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            Files.move(kaynak, hedef, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Havuzdan bir tampon al; bossa ve tahsis limiti asilmadiysa yeni ac; yoksa null. */
    private static Beyin havuzAl(Beyin b) {
        Beyin k = havuz.poll();
        if (k != null) return k;
        while (true) {
            int n = tahsisli.get();
            if (n >= HAVUZ_MAX) return null;
            if (tahsisli.compareAndSet(n, n + 1)) return yeniTampon(b);
        }
    }

    /** Tamponu havuza iade et (limit ustundeyse birak, GC toplasin). */
    private static void havuzBirak(Beyin k) {
        if (k == null) return;
        if (havuz.size() < HAVUZ_MAX) havuz.offer(k);
        else tahsisli.decrementAndGet();
    }

    private static Beyin yeniTampon(Beyin b) {
        Beyin k = new Beyin();
        k.noronSayisi = b.noronSayisi;
        k.sinapsSayisi = b.sinapsSayisi;
        k.aktiflik = new float[b.noronSayisi];
        k.esik = new float[b.noronSayisi];
        k.lobId = new byte[b.noronSayisi];
        k.katman = new byte[b.noronSayisi];
        k.girisBasi = new int[b.noronSayisi + 1];
        k.kaynak = new int[b.sinapsSayisi];
        k.agirlik = new float[b.sinapsSayisi];
        return k;
    }

    /**
     * Beynin anlik goruntusunu tampona kopyalar (ana thread'de, ~1-2 ms saf memcpy).
     *
     * Boyut degistiyse (olcek farkli bir beyin ayni tamponu aldi) diziler yeniden ayrilir —
     * pratikte tum uyeler ayni boyutta oldugu icin bu neredeyse hic olmaz.
     */
    private static void doldur(Beyin k, Beyin b) {
        if (k.aktiflik == null || k.kaynak.length < b.sinapsSayisi
            || k.agirlik.length < b.sinapsSayisi || k.aktiflik.length < b.noronSayisi
            || k.girisBasi.length < b.noronSayisi + 1) {
            k.aktiflik = new float[b.noronSayisi];
            k.esik = new float[b.noronSayisi];
            k.lobId = new byte[b.noronSayisi];
            k.katman = new byte[b.noronSayisi];
            k.girisBasi = new int[b.noronSayisi + 1];
            k.kaynak = new int[b.sinapsSayisi];
            k.agirlik = new float[b.sinapsSayisi];
        }
        k.noronSayisi = b.noronSayisi;
        k.sinapsSayisi = b.sinapsSayisi;
        System.arraycopy(b.aktiflik, 0, k.aktiflik, 0, b.noronSayisi);
        System.arraycopy(b.esik, 0, k.esik, 0, b.noronSayisi);
        System.arraycopy(b.lobId, 0, k.lobId, 0, b.noronSayisi);
        System.arraycopy(b.katman, 0, k.katman, 0, b.noronSayisi);
        System.arraycopy(b.girisBasi, 0, k.girisBasi, 0, b.noronSayisi + 1);
        System.arraycopy(b.kaynak, 0, k.kaynak, 0, b.sinapsSayisi);
        System.arraycopy(b.agirlik, 0, k.agirlik, 0, b.sinapsSayisi);
        k.olcek = b.olcek;
        k.topolojiTohumu = b.topolojiTohumu;
        k.topolojiSurumu = b.topolojiSurumu;
    }

    /** Sunucu kapanışında: bekleyen yazımların bitmesini bekle (veri kaybı olmasın). */
    public static void kapanisBekle() {
        try {
            YAZICI.shutdown();
            if (!YAZICI.awaitTermination(5, TimeUnit.SECONDS)) {
                Candy.LOG.warn("[Candy] beyin yazıcısı zamanında bitmedi");
            }
        } catch (Throwable ignored) {}
    }

    public static boolean hazir() {
        return klasor != null;
    }
}
