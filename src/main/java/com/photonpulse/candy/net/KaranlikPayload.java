package com.photonpulse.candy.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C — dünyanın genel ışık kademesi kaç basamak düşük görünecek (0..10).
 *
 * Çözülen her şifre bu değeri bir artırır. Client tarafı lightmap'e sabit bir karanlık
 * tabanı ekler, yani tüm blok aydınlatması etkilenir — meşale de güneş de.
 */
public record KaranlikPayload(int kademe) implements CustomPayload {

    public static final CustomPayload.Id<KaranlikPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "karanlik"));

    public static final PacketCodec<RegistryByteBuf, KaranlikPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.VAR_INT, KaranlikPayload::kademe,
            KaranlikPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
