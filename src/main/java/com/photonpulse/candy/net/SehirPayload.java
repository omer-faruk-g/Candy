package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — client, IP-tabanlı geolocation ile bulduğu şehir adını sunucuya bildirir.
 * Veri hiçbir yerde diske yazılmaz, sadece RAM'de tutulur, tek bir private mesajda kullanılır.
 */
public record SehirPayload(String sehir) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SehirPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "sehir"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SehirPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SehirPayload::sehir,
            SehirPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
