package com.photonpulse.candy.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.photonpulse.candy.Candy;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GoruntuMudahale (v8.1) — Candy oyuncunun FOV ve render mesafesini geçici değiştirir.
 *
 * <h3>ÇÖKME GÜVENLİ GERİ ALMA — TASARIMIN TAMAMI BU</h3>
 * "Oyundan çıkınca eski ayara dönsün" naif hâlde RAM'de bir yedekle yapılır ve oyun
 * çökerse oyuncunun ayarı <b>kalıcı olarak bozulmuş</b> kalır. Bir korku modunun
 * yapabileceği en affedilmez şey bu.
 *
 * <p>Bu yüzden orijinal değerler <b>ilk müdahaleden önce diske</b> yazılır
 * ({@code config/candy-goruntu-yedek.json}) ve üç ayrı noktada geri alınır:</p>
 * <ol>
 *   <li>sunucudan kopunca ({@code DISCONNECT}),</li>
 *   <li>oyun kapanırken ({@code CLIENT_STOPPING}),</li>
 *   <li><b>oyun açılırken</b> — yedek dosyası hâlâ ortadaysa demek ki geçen oturum
 *       çökmüştü; ayar sessizce eski hâline döner.</li>
 * </ol>
 * Yedek dosyası yalnızca başarılı geri almadan sonra silinir, yani dosyanın varlığı
 * "müdahale sürüyor" demektir. İdempotent: iki kez geri almak zarar vermez.
 *
 * <h3>YEDEK ÜZERİNE YAZILMAZ</h3>
 * İkinci bir müdahale geldiğinde yedek <i>güncellenmez</i>. Yoksa Candy'nin kendi
 * değiştirdiği değer "orijinal" sanılır ve geri dönüş yolu kaybolur.
 */
@Environment(EnvType.CLIENT)
public final class GoruntuMudahale {

    private GoruntuMudahale() {}

    /** Diskteki yedek biçimi. Alanlar NON-STATIC — Gson static'leri atlar. */
    private static final class Yedek {
        int fov = -1;
        int render = -1;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FOV_MIN = 30, FOV_MAKS = 110;
    private static final int RENDER_MIN = 2, RENDER_MAKS = 32;

    private static Path yol() {
        return FabricLoader.getInstance().getConfigDir().resolve("candy-goruntu-yedek.json");
    }

    /**
     * Oyun açılışında çağrılır: geçen oturum çökmüşse ayarı geri alır.
     * Yedek yoksa hiçbir şey yapmaz.
     */
    public static void acilistaOnar() {
        if (!Files.exists(yol())) return;
        Candy.LOG.info("[Candy] görüntü yedeği bulundu — geçen oturum temiz kapanmamış, geri alınıyor");
        geriAl();
    }

    /**
     * FOV ve/veya render mesafesini ayarlar. -1 verilen alana dokunulmaz.
     * Değerler vanilla sınırlarına kırpılır.
     */
    public static void uygula(int fov, int render) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Options o = mc.options;

        try {
            // Yedek YOKSA al — varsa dokunma (bkz. "yedek üzerine yazılmaz").
            if (!Files.exists(yol())) {
                Yedek y = new Yedek();
                y.fov = o.fov().get();
                y.render = o.renderDistance().get();
                Path p = yol();
                Files.createDirectories(p.getParent());
                Files.writeString(p, GSON.toJson(y), StandardCharsets.UTF_8);
            }
        } catch (Throwable t) {
            // Yedek alamadıysak müdahale ETMİYORUZ — geri dönüşü olmayan değişiklik yapmayız.
            Candy.LOG.warn("[Candy] görüntü yedeği yazılamadı, müdahale iptal: {}", t.getMessage());
            return;
        }

        try {
            if (fov >= 0) ayarla(o.fov(), kirp(fov, FOV_MIN, FOV_MAKS));
            if (render >= 0) ayarla(o.renderDistance(), kirp(render, RENDER_MIN, RENDER_MAKS));
            Candy.LOG.debug("[Candy] görüntü müdahalesi: fov={} render={}", fov, render);
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] görüntü ayarlanamadı: {}", t.getMessage());
        }
    }

    /** Kayıtlı orijinal değerlere döner ve yedeği siler. İdempotent. */
    public static void geriAl() {
        Path p = yol();
        Yedek y = null;
        try {
            if (Files.exists(p)) {
                y = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Yedek.class);
            }
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] görüntü yedeği okunamadı: {}", t.getMessage());
        }
        if (y == null) {
            // Yedek bozuk/okunamadı — dosyayı silmek yanlış olur, oyuncu elle bakabilir.
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null) {
            try {
                if (y.fov >= 0) ayarla(mc.options.fov(), kirp(y.fov, FOV_MIN, FOV_MAKS));
                if (y.render >= 0) {
                    ayarla(mc.options.renderDistance(), kirp(y.render, RENDER_MIN, RENDER_MAKS));
                }
                // options.json'a da yaz — oyuncu bir daha mod olmadan açsa bile düzgün olsun.
                mc.options.save();
            } catch (Throwable t) {
                Candy.LOG.warn("[Candy] görüntü geri alınamadı: {}", t.getMessage());
                return;   // dosyayı silme: bir sonraki açılışta tekrar denenir
            }
        }

        try {
            Files.deleteIfExists(p);
        } catch (Throwable ignored) {}
        KaranlikState.sifirla();
        Candy.LOG.info("[Candy] görüntü ayarları geri alındı (fov={} render={})", y.fov, y.render);
    }

    private static void ayarla(OptionInstance<Integer> opt, int deger) {
        if (opt == null) return;
        Integer mevcut = opt.get();
        if (mevcut != null && mevcut == deger) return;
        opt.set(deger);
    }

    private static int kirp(int v, int min, int maks) {
        return v < min ? min : (v > maks ? maks : v);
    }
}
