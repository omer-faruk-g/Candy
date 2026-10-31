package com.photonpulse.candy.kacis;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S — istemcinin ölçtüğü kaçış sinyali: F3 ekranı kaç tick açık kaldı.
 *
 * <p>Taşınan tek şey bir SAYI. Ne dosya yolu, ne kullanıcı adı, ne pencere başlığı, ne
 * yüklü mod listesi. Sunucu logu paylaşılabilir bir dosya olduğu için buraya kişisel
 * hiçbir şey konmuyor — v8.1'de {@code UygulamaPayload} için kurulan kuralın aynısı.</p>
 */
public record KacisPayload(int f3Tick) implements CustomPayload {

    public static final CustomPayload.Id<KacisPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "kacis"));

    public static final PacketCodec<RegistryByteBuf, KacisPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.VAR_INT, KacisPayload::f3Tick,
            KacisPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
