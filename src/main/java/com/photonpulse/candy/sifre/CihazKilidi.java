package com.photonpulse.candy.sifre;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.platform.Yukleyici;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * CihazKilidi (v8.1) — Candy'nin ürettiği her şifrenin <b>cihaza bağlı</b> olmasını sağlar.
 *
 * <h3>NE YAPAR</h3>
 * Kurulumda bir kez 32 baytlık rastgele anahtar üretir ve <b>dünya kaydının DIŞINA</b>,
 * {@code config/candy-cihaz.key} dosyasına yazar. Candy'nin bütün şifre jetonları bu
 * anahtardan türetilir.
 *
 * <h3>NEDEN BÖYLE</h3>
 * Dünya klasörünü başka bir bilgisayara kopyalarsan anahtar gelmez — o makinede yeni bir
 * anahtar doğar, dolayısıyla <b>aynı dosyadan çıkan cevap orada yanlıştır</b>. İstenen
 * davranış tam olarak bu: içerik cihaz dışında bozulur. Aynı sebeple şifre cevapları
 * internette paylaşılamaz; her kurulumda farklıdır.
 *
 * <h3>NE TOPLAMAZ</h3>
 * Donanım parmak izi, kullanıcı adı, IP, MAC — <b>hiçbiri</b>. Anahtar
 * {@link SecureRandom}'dan gelir, yani kişisel hiçbir şeyden türetilmiyor. Toplanmayan
 * veri sızamaz.
 *
 * <h3>KALICI KURAL</h3>
 * Kişisel veri (şehir, donanım, uygulama adı) <b>hiçbir zaman</b> kalıcı bir yere
 * yazılmaz — ne tabelaya, ne kitaba, ne .txt'ye, ne dünya kaydına. O veriler yalnızca
 * RAM'de durur ve yalnızca tek bir özel sohbet mesajında görünür
 * ({@code meta/SehirBilgisi}, {@code meta/DonanimBilgisi} bunu zaten böyle yapıyor).
 * Bu sınıftaki şifreleme, kalıcı dosyaların <i>cihaz dışında</i> işe yaramamasını
 * sağlamak için var; kişisel veriyi saklamak için değil — çünkü saklanan kişisel veri yok.
 */
public final class CihazKilidi {

    private CihazKilidi() {}

    private static final int ANAHTAR_BOYUT = 32;
    private static volatile byte[] anahtar = null;

    /**
     * Anahtar dosyasının yolu, ya da {@code null}.
     *
     * Ayar dizini oyun dışında (headless doğrulama, birim testi) yoktur ve
     * {@link Yukleyici#ayarDizini()} patlar — iki loader'da da (Fabric'te
     * {@code getInstance()} attığı için, NeoForge'da {@code FMLPaths} henüz
     * kurulmadığı için). Bu yüzden hata yutulup {@code null} dönüyor;
     * çağıran oturumluk anahtarla devam ediyor. {@code VagusAyar.yukle()} aynı sebeple
     * aynı şeyi yapıyor.
     */
    private static Path yol() {
        try {
            return Yukleyici.ayarDizini().resolve("candy-cihaz.key");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Cihaz anahtarı; ilk çağrıda üretip diske yazar. Hata hâlinde oturumluk anahtar. */
    public static byte[] anahtar() {
        byte[] a = anahtar;
        if (a != null) return a;
        synchronized (CihazKilidi.class) {
            if (anahtar != null) return anahtar;
            anahtar = yukleVeyaUret();
            return anahtar;
        }
    }

    private static byte[] yukleVeyaUret() {
        Path p = yol();
        try {
            if (p != null && Files.exists(p)) {
                byte[] okunan = Files.readAllBytes(p);
                if (okunan.length == ANAHTAR_BOYUT) return okunan;
                Candy.LOG.warn("[Candy] cihaz anahtarı bozuk ({} bayt), yenisi üretiliyor",
                    okunan.length);
            }
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] cihaz anahtarı okunamadı: {}", t.getMessage());
        }
        byte[] yeni = new byte[ANAHTAR_BOYUT];
        new SecureRandom().nextBytes(yeni);
        if (p == null) return yeni;         // oyun dışı — oturumluk anahtar
        try {
            Path ebeveyn = p.getParent();
            if (ebeveyn != null) Files.createDirectories(ebeveyn);
            Files.write(p, yeni);
            Candy.LOG.info("[Candy] cihaz anahtarı üretildi (bu makineye özel)");
        } catch (Throwable t) {
            // Yazamadık — oturumluk anahtarla devam. Şifreler bu oturumda tutarlı,
            // yeniden başlatınca değişir. Çökmekten iyidir.
            Candy.LOG.warn("[Candy] cihaz anahtarı yazılamadı, oturumluk kullanılıyor: {}",
                t.getMessage());
        }
        return yeni;
    }

    /**
     * SHA-256 sayaç-kipi anahtar akışı. Deterministik: aynı (anahtar, tuz, uzunluk) her
     * zaman aynı akışı verir. Yanlış anahtar → tamamen farklı akış → çıktı bozulur.
     *
     * <p>Gerçek şifreleme değil, kasıtlı olarak: amaç gizlilik değil, <b>cihaz dışında
     * bozulma</b>. AES/GCM yanlış anahtarda hata atar; biz hatayı değil çöp çıktıyı
     * istiyoruz çünkü oyuncunun gördüğü şey saçma görünmeli.</p>
     */
    public static byte[] akis(String tuz, int uzunluk) {
        byte[] cikti = new byte[Math.max(0, uzunluk)];
        if (cikti.length == 0) return cikti;
        byte[] k = anahtar();
        byte[] t = tuz == null ? new byte[0] : tuz.getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            int yazilan = 0;
            int sayac = 0;
            while (yazilan < cikti.length) {
                md.reset();
                md.update(k);
                md.update(t);
                md.update((byte) (sayac >>> 24));
                md.update((byte) (sayac >>> 16));
                md.update((byte) (sayac >>> 8));
                md.update((byte) sayac);
                byte[] blok = md.digest();
                int n = Math.min(blok.length, cikti.length - yazilan);
                System.arraycopy(blok, 0, cikti, yazilan, n);
                yazilan += n;
                sayac++;
            }
        } catch (Throwable t2) {
            // SHA-256 her JDK'da var; buraya düşmemeli. Düşerse sabit akış — çalışsın.
            Candy.LOG.error("[Candy] SHA-256 yok: {}", t2.getMessage());
        }
        return cikti;
    }
}
