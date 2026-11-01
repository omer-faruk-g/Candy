package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — client, açık olan uygulamalardan <b>yalnızca bir tanesinin beyaz liste
 * indeksini</b> bildirir. Uygulama <i>adı</i> bile ağa çıkmaz: iki uçta aynı sabit
 * liste var ve taşınan tek şey o listedeki sıra numarası.
 *
 * <h3>NEDEN İNDEKS, AD DEĞİL</h3>
 * Serbest metin gönderilse client'ın gördüğü her şey sunucuya taşınabilir hâle gelirdi.
 * İndeks, protokolün taşıyabileceği bilgiyi fiziksel olarak beyaz listeyle sınırlıyor:
 * listede olmayan bir şey ifade edilemiyor. {@code -1} → eşleşme yok.
 *
 * Değer sunucuda yalnızca RAM'de durur, diske yazılmaz, Vagus prompt'una girmez.
 */
public record UygulamaPayload(int indeks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UygulamaPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "uygulama"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UygulamaPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UygulamaPayload::indeks,
            UygulamaPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
