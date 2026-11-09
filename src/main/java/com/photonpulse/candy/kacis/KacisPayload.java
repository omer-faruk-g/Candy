package com.photonpulse.candy.kacis;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — istemcinin ölçtüğü kaçış sinyali: F3 ekranı kaç tick açık kaldı.
 *
 * <p>Taşınan tek şey bir SAYI. Ne dosya yolu, ne kullanıcı adı, ne pencere başlığı, ne
 * yüklü mod listesi. Sunucu logu paylaşılabilir bir dosya olduğu için buraya kişisel
 * hiçbir şey konmuyor — v8.1'de {@code UygulamaPayload} için kurulan kuralın aynısı.</p>
 */
public record KacisPayload(int f3Tick) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<KacisPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "kacis"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KacisPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KacisPayload::f3Tick,
            KacisPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
