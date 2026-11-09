package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C payload — server oyuncunun anlık korku/agresivlik değerini client'a gönderir.
 * Client mixinleri (chat/hud/camera) bu değeri okur.
 */
public record CandyIntensityPayload(double agresivlik, double korku, double ozguven)
    implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CandyIntensityPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "intensity"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CandyIntensityPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.DOUBLE, CandyIntensityPayload::agresivlik,
            ByteBufCodecs.DOUBLE, CandyIntensityPayload::korku,
            ByteBufCodecs.DOUBLE, CandyIntensityPayload::ozguven,
            CandyIntensityPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
