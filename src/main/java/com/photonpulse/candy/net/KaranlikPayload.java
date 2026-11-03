package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — dünyanın genel ışık kademesi kaç basamak düşük görünecek (0..10).
 *
 * Çözülen her şifre bu değeri bir artırır. Client tarafı lightmap'e sabit bir karanlık
 * tabanı ekler, yani tüm blok aydınlatması etkilenir — meşale de güneş de.
 */
public record KaranlikPayload(int kademe) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<KaranlikPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "karanlik"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KaranlikPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KaranlikPayload::kademe,
            KaranlikPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
