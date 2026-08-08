package com.photonpulse.candy.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C — Candy player'a camscreen jump scare gönderir.
 * textIndex: hangi Candy sözü çıkacak (0-3, lang key ile)
 * durationMs: overlay ne kadar süre görünecek
 */
public record CamscreenPayload(int textIndex, int durationMs) implements CustomPayload {

    public static final CustomPayload.Id<CamscreenPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "camscreen"));

    public static final PacketCodec<RegistryByteBuf, CamscreenPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.VAR_INT, CamscreenPayload::textIndex,
            PacketCodecs.VAR_INT, CamscreenPayload::durationMs,
            CamscreenPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
