package com.photonpulse.candy.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S — client, IP-tabanlı geolocation ile bulduğu şehir adını sunucuya bildirir.
 * Veri hiçbir yerde diske yazılmaz, sadece RAM'de tutulur, tek bir private mesajda kullanılır.
 */
public record SehirPayload(String sehir) implements CustomPayload {

    public static final CustomPayload.Id<SehirPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "sehir"));

    public static final PacketCodec<RegistryByteBuf, SehirPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, SehirPayload::sehir,
            SehirPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
