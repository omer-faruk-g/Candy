package com.photonpulse.candy.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C — Faz 8 countdown. saniye = kalan sn (-1 = temizle/gizle).
 * Client sağ üstte kırmızı geri sayım gösterir.
 */
public record BossCountdownPayload(int saniye) implements CustomPayload {

    public static final CustomPayload.Id<BossCountdownPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "boss_countdown"));

    public static final PacketCodec<RegistryByteBuf, BossCountdownPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.VAR_INT, BossCountdownPayload::saniye,
            BossCountdownPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
