package com.photonpulse.candy.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C — altyazı kanalı: hangi altyazı metni, kaç milisaniye.
 *
 * <p>Taşınan şey bir İNDEKS, metin değil. Metin istemcideki lang dosyasından gelir
 * ({@code candy.altyazi.N}), yani sunucu hiçbir serbest metin göndermiyor — ne oyuncu
 * adı, ne şehir, ne uygulama adı. v8.1'de {@code UygulamaPayload} için kurulan kuralın
 * aynısı: protokol yalnızca indeks taşır.</p>
 */
public record AltyaziPayload(int indeks, int sureMs) implements CustomPayload {

    public static final CustomPayload.Id<AltyaziPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "altyazi"));

    public static final PacketCodec<RegistryByteBuf, AltyaziPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.VAR_INT, AltyaziPayload::indeks,
            PacketCodecs.VAR_INT, AltyaziPayload::sureMs,
            AltyaziPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
