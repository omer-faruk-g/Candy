package com.photonpulse.candy.platform;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Loader sorgulari — NeoForge uygulamasi.
 *
 * AYAR DIZINI SINIRI: Fabric'teki ile AYNI yere cozuluyor, iki tarafin
 * kaynagindan dogrulandi (varsayilmadi):
 *   Fabric  FabricLoaderImpl.setGameDir  ->  configDir = gameDir.resolve("config")
 *   NeoForge FMLPaths.CONFIGDIR("config") -> rootPath.resolve("config")
 * Candy'nin "yalnizca config/candy-* ve dunya kaydina yaz" siniri kaymiyor.
 *
 * FMLPaths.get() oyun disinda null donuyor (Fabric'te getInstance() patliyor).
 * Cagri yerleri Fabric'in ATMASINA gore yazilmis (try/catch -> oturumluk deger),
 * o yuzden burada null acikca atiliyor; ayni catch bloklari iki loader'da da
 * ateslenir.
 */
public final class Yukleyici {

    /**
     * NeoForge'un kendi girdileri. Fabric bunlari {@code "builtin"} tipiyle
     * isaretliyor, NeoForge'da tip alani YOK ({@code IModInfo}: getModId,
     * getDisplayName, getVersion, ... getType yok) -> kimlige gore filtre.
     */
    private static final Set<String> KENDI = Set.of("minecraft", "neoforge");

    private Yukleyici() {}

    /** Bir modun gorunur kimligi ve adi. Iki loader'da ayni kayit. */
    public record ModBilgisi(String id, String ad) {}

    public static Path ayarDizini() {
        return zorunlu(FMLPaths.CONFIGDIR.get(), "ayar dizini");
    }

    public static Path oyunDizini() {
        return zorunlu(FMLPaths.GAMEDIR.get(), "oyun dizini");
    }

    private static Path zorunlu(Path yol, String ne) {
        if (yol == null) {
            // Fabric'te getInstance() ayni durumda atiyor; cagri yerlerinin
            // catch bloklari iki loader'da da ayni sekilde ateslensin.
            throw new IllegalStateException("[Candy] " + ne + " henuz kurulmadi");
        }
        return yol;
    }

    public static boolean modYuklu(String id) {
        return ModList.get().isLoaded(id);
    }

    /** Minecraft surumu, loader'dan. Okunamazsa {@code null}. */
    public static String minecraftSurumu() {
        return ModList.get().getModContainerById("minecraft")
            .map(c -> c.getModInfo().getVersion().toString())
            .orElse(null);
    }

    /** Kurulu modlar — loader'in kendi girdileri disinda, bkz. {@link #KENDI}. */
    public static List<ModBilgisi> modlar() {
        List<ModBilgisi> sonuc = new ArrayList<>();
        for (var bilgi : ModList.get().getMods()) {
            String id = bilgi.getModId();
            if (id == null) continue;
            if (KENDI.contains(id)) continue;
            String ad = bilgi.getDisplayName();
            sonuc.add(new ModBilgisi(id, ad == null || ad.isBlank() ? id : ad));
        }
        return sonuc;
    }
}
