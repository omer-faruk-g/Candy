package com.photonpulse.candy.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C payload — server oyuncunun anlık korku/agresivlik değerini client'a gönderir.
 * Client mixinleri (chat/hud/camera) bu değeri okur.
 */
public record CandyIntensityPayload(double agresivlik, double korku, double ozguven)
    implements CustomPayload {

    public static final CustomPayload.Id<CandyIntensityPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "intensity"));

    public static final PacketCodec<RegistryByteBuf, CandyIntensityPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.DOUBLE, CandyIntensityPayload::agresivlik,
            PacketCodecs.DOUBLE, CandyIntensityPayload::korku,
            PacketCodecs.DOUBLE, CandyIntensityPayload::ozguven,
            CandyIntensityPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
