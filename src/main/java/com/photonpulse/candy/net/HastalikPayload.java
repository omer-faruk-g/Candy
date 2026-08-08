package com.photonpulse.candy.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C — hastalık adı client'a sync. Boş string = sağlıklı.
 * Client HUD bar üstünde bu string'i lang key ile çevirip yazar.
 */
public record HastalikPayload(String hastalik) implements CustomPayload {

    public static final CustomPayload.Id<HastalikPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "hastalik"));

    public static final PacketCodec<RegistryByteBuf, HastalikPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, HastalikPayload::hastalik,
            HastalikPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
