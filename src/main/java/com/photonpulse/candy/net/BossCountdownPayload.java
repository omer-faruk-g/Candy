package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — Faz 8 countdown. saniye = kalan sn (-1 = temizle/gizle).
 * Client sağ üstte kırmızı geri sayım gösterir.
 */
public record BossCountdownPayload(int saniye) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BossCountdownPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "boss_countdown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BossCountdownPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BossCountdownPayload::saniye,
            BossCountdownPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
