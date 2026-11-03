package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — Candy player'a camscreen jump scare gönderir.
 * textIndex: hangi Candy sözü çıkacak (0-3, lang key ile)
 * durationMs: overlay ne kadar süre görünecek
 */
public record CamscreenPayload(int textIndex, int durationMs) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CamscreenPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "camscreen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CamscreenPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CamscreenPayload::textIndex,
            ByteBufCodecs.VAR_INT, CamscreenPayload::durationMs,
            CamscreenPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
