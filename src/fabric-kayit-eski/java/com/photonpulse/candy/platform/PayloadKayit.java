package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * PayloadTypeRegistry'nin surume bagli iki statik metodu — 1.21.x.
 *
 * Bu dosyanin IKI kopyasi var ve build.gradle hangisinin derlenecegini secer:
 *   src/fabric-kayit-eski/java  -> 1.21.x   (playS2C / playC2S)
 *   src/fabric-kayit-26/java    -> 26.x     (clientboundPlay / serverboundPlay)
 *
 * Neden Stonecutter degil: src/fabric/java KOK yoldan ekleniyor, Stonecutter'in
 * uretilmis agacinda olmadigi icin orada `//? if` ve replacements calismiyor.
 * Dosya bilerek bu kadar kucuk: tek fark iki ad.
 */
public final class PayloadKayit {

    private PayloadKayit() {}

    /** Sunucu -> istemci yonu. */
    public static PayloadTypeRegistry<RegistryFriendlyByteBuf> istemciye() {
        return PayloadTypeRegistry.playS2C();
    }

    /** Istemci -> sunucu yonu. */
    public static PayloadTypeRegistry<RegistryFriendlyByteBuf> sunucuya() {
        return PayloadTypeRegistry.playC2S();
    }
}
