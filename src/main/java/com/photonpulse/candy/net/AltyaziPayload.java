package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — altyazı kanalı: hangi altyazı metni, kaç milisaniye.
 *
 * <p>Taşınan şey bir İNDEKS, metin değil. Metin istemcideki lang dosyasından gelir
 * ({@code candy.altyazi.N}), yani sunucu hiçbir serbest metin göndermiyor — ne oyuncu
 * adı, ne şehir, ne uygulama adı. v8.1'de {@code UygulamaPayload} için kurulan kuralın
 * aynısı: protokol yalnızca indeks taşır.</p>
 */
public record AltyaziPayload(int indeks, int sureMs) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AltyaziPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "altyazi"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AltyaziPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AltyaziPayload::indeks,
            ByteBufCodecs.VAR_INT, AltyaziPayload::sureMs,
            AltyaziPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
