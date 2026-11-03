package com.photonpulse.candy.platform;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * Kayit zamanlamasi — Fabric uygulamasi.
 *
 * Fabric'te mod init registry'ler donmadan once calisir, yani kayit cagrildigi
 * anda yapilabilir. Kapi burada sadece cagriyi gecirir; hangi registry oldugu
 * Fabric tarafinda onemsiz.
 *
 * NeoForge tarafinda ayni cagri siraya alinip RegisterEvent'te bosaltiliyor,
 * cunku orada mod kurucusu caliştiginda registry'ler zaten donmustur. Cagri
 * yerleri iki loader'da da birebir ayni kodu yaziyor.
 */
public final class KayitKapisi {

    private KayitKapisi() {}

    public static void kaydet(ResourceKey<? extends Registry<?>> hangi, Runnable kayit) {
        kayit.run();
    }
}
