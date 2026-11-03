package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — hastalık adı client'a sync. Boş string = sağlıklı.
 * Client HUD bar üstünde bu string'i lang key ile çevirip yazar.
 */
public record HastalikPayload(String hastalik) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HastalikPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "hastalik"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HastalikPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, HastalikPayload::hastalik,
            HastalikPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
