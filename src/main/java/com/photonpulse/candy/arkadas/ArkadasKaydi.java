package com.photonpulse.candy.arkadas;

import com.photonpulse.candy.Candy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import java.util.Map;

/**
 * ArkadasKaydi (v8.3) — "Arkadaş öldürüldü mü" bilgisinin <b>kalıcı</b> yeri.
 *
 * <h3>NİYE DÜNYA KAYDINDA, OYUNCUDA DEĞİL</h3>
 * Arkadaş dünyada <b>tek</b>. Bilgiyi {@code TruePersonality}'de tutmak iki yerde
 * yanlış olurdu:
 * <ul>
 *   <li>Çok oyunculuda: A öldürür, B'nin dosyasında iz kalmaz, B girince Arkadaş
 *       yeniden spawn edilir. Kalıcı ölüm kalıcı olmaz.</li>
 *   <li>Dünya silinip oyuncu dosyası kalırsa: yeni dünyada Arkadaş hiç gelmez.
 *       Ölüm yeni dünyaya sızar.</li>
 * </ul>
 *
 * <h3>NİYE CONFIG DEĞİL</h3>
 * {@code config/} makineye ait; dünya kopyalanıp arkadaşa verilse ölüm bilgisi
 * gitmezdi ve orada Arkadaş canlı çıkardı. Dünya kaydı kökü doğru yer: bilgi dünyayla
 * birlikte seyahat eder, dünyayla birlikte silinir.
 *
 * <h3>BİÇİM</h3>
 * Tek satır düz metin. JSON/Gson gereksiz: tek bir boolean için şema, sürüm ve göç
 * yükü taşımanın anlamı yok. Dosya yoksa "ölmedi" demek — en güvenli varsayılan.
 */
public final class ArkadasKaydi {

    private ArkadasKaydi() {}

    private static final String DOSYA = "candy-arkadas.txt";
    private static final String OLU_ISARET = "oldu";

    /** Dünya başına bellek içi önbellek — her tick disk okumayalım. */
    private static final Map<String, Boolean> onbellek = new ConcurrentHashMap<>();

    private static Path yol(ServerLevel world) {
        try {
            return world.getServer().getWorldPath(LevelResource.ROOT).resolve(DOSYA);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String anahtar(ServerLevel world) {
        Path p = yol(world);
        return p == null ? "?" : p.toString();
    }

    /** Arkadaş bu dünyada öldürüldü mü. */
    public static boolean olduMu(ServerLevel world) {
        if (world == null) return true;   // bilinmiyorsa spawn etme
        String k = anahtar(world);
        Boolean b = onbellek.get(k);
        if (b != null) return b;

        boolean sonuc = false;
        Path p = yol(world);
        if (p != null) {
            try {
                if (Files.exists(p)) {
                    String icerik = Files.readString(p, StandardCharsets.UTF_8).trim();
                    sonuc = icerik.startsWith(OLU_ISARET);
                }
            } catch (IOException e) {
                // Okunamadıysa ÖLÜ say: yanlış tarafa düşmek Arkadaş'ı gereksiz
                // spawn etmekten iyidir (öldürülmüş bir şeyin geri gelmesi mekaniğin
                // tek gerçek sözünü bozar).
                Candy.LOG.warn("[Candy] Arkadaş kaydı okunamadı, ölü sayılıyor: {}",
                    e.getMessage());
                sonuc = true;
            }
        }
        onbellek.put(k, sonuc);
        return sonuc;
    }

    /** Ölümü kalıcı olarak yazar. */
    public static void olduruldu(ServerLevel world) {
        if (world == null) return;
        String k = anahtar(world);
        onbellek.put(k, Boolean.TRUE);
        Path p = yol(world);
        if (p == null) return;
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, OLU_ISARET + System.lineSeparator(),
                StandardCharsets.UTF_8);
            Candy.LOG.info("[Candy] Arkadaş ölümü dünyaya yazıldı: {}", p);
        } catch (IOException e) {
            // Yazılamazsa önbellek yine ölü tutuyor; o oturum boyunca geri gelmiyor.
            Candy.LOG.warn("[Candy] Arkadaş ölümü yazılamadı: {}", e.getMessage());
        }
    }

    public static void sunucuKapan() {
        onbellek.clear();
    }
}
