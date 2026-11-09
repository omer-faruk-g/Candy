package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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
public record BaslikPayload(int indeks, boolean geriAl) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BaslikPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "baslik"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BaslikPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BaslikPayload::indeks,
            ByteBufCodecs.BOOL, BaslikPayload::geriAl,
            BaslikPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
