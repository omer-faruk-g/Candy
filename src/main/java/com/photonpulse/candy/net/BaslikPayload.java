package com.photonpulse.candy.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C — pencere başlığı müdahalesi: hangi başlık metni, ya da geri al.
 *
 * <p>Yine İNDEKS. Başlık metinleri istemcide sabit bir dizide duruyor
 * ({@code client.PencereBasligi.BASLIKLAR}); sunucu serbest metin göndermiyor, yani
 * başlığa kişisel hiçbir veri konamaz. Oyuncunun şehri ya da uygulaması pencere
 * başlığına yazılırsa ekran paylaşımında/yayında herkese görünürdü — bu yol kapalı.</p>
 *
 * @param indeks başlık dizisindeki sıra; {@code geriAl} true ise okunmaz
 * @param geriAl true ise özgün başlığa dönülür
 */
public record BaslikPayload(int indeks, boolean geriAl) implements CustomPayload {

    public static final CustomPayload.Id<BaslikPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "baslik"));

    public static final PacketCodec<RegistryByteBuf, BaslikPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.VAR_INT, BaslikPayload::indeks,
            PacketCodecs.BOOLEAN, BaslikPayload::geriAl,
            BaslikPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
