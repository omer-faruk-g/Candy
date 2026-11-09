package com.photonpulse.candy.platform;

import com.photonpulse.candy.Candy;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kayit zamanlamasi — NeoForge uygulamasi.
 *
 * Fabric'te mod init registry'ler donmadan once calisir ve kayit dogrudan
 * yapilabilir. NeoForge'da mod kurucusu caliştiginda BuiltInRegistries ZATEN
 * DONMUSTUR; dogrudan Registry.register cagrisi
 *
 *     IllegalStateException: Registry is already frozen
 *
 * atar. Bu derlemede gorunmez: port 0 hata ile derlendi, dort jar uretti ve
 * yine de ilk acilista coktu. Yakalayan tek sey istemciyi acmak oldu.
 *
 * Cozum: cagrilar registry'ye gore siraya alinir ve o registry'nin kendi
 * RegisterEvent'inde bosaltilir. RegisterEvent sirasinda ilgili registry
 * cozulmus durumdadir — NeoForge'un kendi RegisterEvent.register'i de icerde
 * duz Registry.register(this.registry, ...) cagiriyor, yani mevcut register()
 * govdeleri HIC DEGISMEDEN yeniden kullanilabiliyor.
 *
 * Dinleyici anotasyonla degil, NeoForgeGiris'in aldigi veri yoluna ACIKCA
 * baglaniyor: @EventBusSubscriber'in hangi veri yoluna gittigi dogrulanmadi ve
 * yanlis veri yolu sessiz bir no-op olurdu — kayitlar hic yapilmaz, hicbir
 * hata da cikmazdi.
 */
public final class KayitKapisi {

    private static final Map<ResourceKey<? extends Registry<?>>, List<Runnable>> BEKLEYEN =
        new LinkedHashMap<>();

    private KayitKapisi() {}

    /** NeoForgeGiris mod veri yolunu buraya verir. */
    public static void baglan(IEventBus modVeriYolu) {
        modVeriYolu.addListener(KayitKapisi::onKayit);
    }

    public static void kaydet(ResourceKey<? extends Registry<?>> hangi, Runnable kayit) {
        synchronized (BEKLEYEN) {
            BEKLEYEN.computeIfAbsent(hangi, k -> new ArrayList<>()).add(kayit);
        }
    }

    private static void onKayit(RegisterEvent event) {
        List<Runnable> isler;
        synchronized (BEKLEYEN) {
            isler = BEKLEYEN.remove(event.getRegistryKey());
        }
        if (isler == null) {
            return;
        }
        // Sessiz basarisizlik bu portta zaten bir kez pahaliya geldi; ne zaman
        // ne bosaltildigi log'da gorunsun ki "kayit hic yapilmadi" durumu
        // davranis bozuklugu olarak degil log'da fark edilsin.
        // Anahtar dogrudan loglaniyor: ResourceKey uzerindeki ad metodu surumler
        // arasinda kaydi (location/identifier) ve iki MC dugumu de derlenmeli.
        Candy.LOG.info("[Candy] Kayit bosaltiliyor: {} ({} is)",
            event.getRegistryKey(), isler.size());
        isler.forEach(Runnable::run);
    }
}
