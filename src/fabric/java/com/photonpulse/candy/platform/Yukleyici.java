package com.photonpulse.candy.platform;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loader sorgulari — Fabric uygulamasi.
 *
 * AYAR DIZINI SINIRI: {@link #ayarDizini()} iki loader'da da ayni yere
 * cozuluyor, kaynaktan dogrulandi (varsayilmadi):
 *   Fabric  FabricLoaderImpl.setGameDir  ->  configDir = gameDir.resolve("config")
 *   NeoForge FMLPaths.CONFIGDIR("config") -> rootPath.resolve("config")
 * Yani Candy'nin "yalnizca config/candy-* ve dunya kaydina yaz" siniri
 * kaymiyor. Sarmalayici bu yolu DEGISTIRMIYOR, yalnizca soruyor.
 *
 * ATMA DAVRANISI DA AYNI: oyun disinda (headless dogrulama, birim testi)
 * Fabric'te getInstance() patliyor; cagri yerleri bunu try/catch ile yutup
 * oturumluk/varsayilan degerle devam ediyor (CihazKilidi, VagusAyar). NeoForge
 * tarafi null dondugu icin orada acikca atiliyor, boylece ayni catch bloklari
 * iki loader'da da ateslenir.
 */
public final class Yukleyici {

    private Yukleyici() {}

    /** Bir modun gorunur kimligi ve adi. Iki loader'da ayni kayit. */
    public record ModBilgisi(String id, String ad) {}

    public static Path ayarDizini() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static Path oyunDizini() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static boolean modYuklu(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    /** Minecraft surumu, loader'dan. Okunamazsa {@code null}. */
    public static String minecraftSurumu() {
        return FabricLoader.getInstance().getModContainer("minecraft")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse(null);
    }

    /**
     * Kurulu modlar — loader'in KENDI sentetik girdileri disinda.
     *
     * Fabric'te sentetik girdiler {@code "builtin"} tipiyle isaretli
     * ({@code minecraft}, {@code java}); "neyin builtin oldugu" bir loader
     * gercegi oldugu icin filtre burada, cagri yerinde degil. Candy'nin kendi
     * altyapi listesi (ModFarkindaligi.GIZLE) bunun ustune biniyor.
     */
    public static List<ModBilgisi> modlar() {
        List<ModBilgisi> sonuc = new ArrayList<>();
        for (var container : FabricLoader.getInstance().getAllMods()) {
            var md = container.getMetadata();
            String id = md.getId();
            if (id == null) continue;
            if ("builtin".equals(md.getType())) continue;
            String ad = md.getName();
            sonuc.add(new ModBilgisi(id, ad == null || ad.isBlank() ? id : ad));
        }
        return sonuc;
    }
}
