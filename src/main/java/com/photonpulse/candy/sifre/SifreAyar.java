package com.photonpulse.candy.sifre;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.photonpulse.candy.Candy;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SifreAyar — {@code config/candy-sifre.json}. İkilik şifre sisteminin anahtarları.
 *
 * <h3>MASAÜSTÜ NEDEN AYRI BAYRAK</h3>
 * Dünya kaydına ve kitaba yazmak oyunun içinde kalır. Masaüstüne dosya bırakmak oyunun
 * dışına taşar, o yüzden kendi bayrağı var ve kapatılabilir. Bırakılan dosyada kişisel
 * hiçbir şey yok — yalnızca {@code 0}/{@code 1} ve çözümünde anlamsız karakterler. Candy
 * kendi bıraktığı dosyaları sayar, {@code maksMasaustuDosya}'yı aşarsa en eskisini siler;
 * başka hiçbir dosyaya dokunmaz.
 *
 * <h3>SINIR</h3>
 * Terminal açmak, OS komutu çalıştırmak, pencere açmak, IP göstermek — <b>hiçbiri yok</b>
 * ve eklenmeyecek. Candy pencerenin içinde kalır; tek istisnası okunabilir bir metin dosyası.
 */
public class SifreAyar {

    /** Şifre sistemi açık mı. */
    public boolean acik = true;
    /** Dünya kaydı klasörüne .txt bırakılsın mı ("haritaya"). */
    @SerializedName("harita_dosyasi")
    public boolean haritaDosyasi = true;
    /** Oyuncuya ikilik kodlu yazılı kitap verilsin mi. */
    public boolean kitap = true;
    /** Masaüstüne .txt bırakılsın mı. Oyun penceresinin dışına taşan tek davranış. */
    @SerializedName("masaustu_dosyasi")
    public boolean masaustuDosyasi = true;
    /** Masaüstünde en fazla kaç Candy dosyası birikebilir. */
    @SerializedName("maks_masaustu_dosya")
    public int maksMasaustuDosya = 3;
    /** Masaüstü yolu; boşsa otomatik bulunur. */
    @SerializedName("masaustu_yolu")
    public String masaustuYolu = "";
    /** İki şifre dağıtımı arası en az kaç dakika geçer. */
    @SerializedName("aralik_dk")
    public int aralikDk = 25;
    /** Çözülen her şifrenin düşürdüğü ışık kademesi. */
    @SerializedName("isik_dusumu")
    public int isikDusumu = 1;
    /** Işık düşümünün toplam üst sınırı — dünya tamamen kararmasın. */
    @SerializedName("maks_isik_dusumu")
    public int maksIsikDusumu = 6;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile SifreAyar onbellek = null;

    /** Ayarı okur (önbellekli); dosya yoksa varsayılanı yazar. Asla exception atmaz. */
    public static SifreAyar al() {
        SifreAyar a = onbellek;
        if (a != null) return a;
        synchronized (SifreAyar.class) {
            if (onbellek != null) return onbellek;
            onbellek = yukle();
            return onbellek;
        }
    }

    private static SifreAyar yukle() {
        SifreAyar ayar = new SifreAyar();
        Path yol;
        try {
            yol = FabricLoader.getInstance().getConfigDir().resolve("candy-sifre.json");
        } catch (Throwable t) {
            ayar.dogrula();
            return ayar;      // ayar dizini yok (headless doğrulama)
        }
        try {
            if (Files.exists(yol)) {
                SifreAyar okunan = GSON.fromJson(
                    Files.readString(yol, StandardCharsets.UTF_8), SifreAyar.class);
                if (okunan != null) ayar = okunan;
            } else {
                Files.createDirectories(yol.getParent());
                Files.writeString(yol, GSON.toJson(ayar), StandardCharsets.UTF_8);
                Candy.LOG.info("[Candy] Şifre ayarı oluşturuldu: {}", yol.getFileName());
            }
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] Şifre ayarı okunamadı ({}) — varsayılan kullanılıyor",
                t.getMessage());
            ayar = new SifreAyar();
        }
        ayar.dogrula();
        return ayar;
    }

    private void dogrula() {
        aralikDk = Math.max(2, Math.min(240, aralikDk));
        isikDusumu = Math.max(0, Math.min(4, isikDusumu));
        maksIsikDusumu = Math.max(0, Math.min(10, maksIsikDusumu));
        maksMasaustuDosya = Math.max(1, Math.min(10, maksMasaustuDosya));
        if (masaustuYolu == null) masaustuYolu = "";
    }

    public long aralikTick() {
        return aralikDk * 60L * 20L;
    }
}
