package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — ikilik fısıltı: hangi metin, kaç milisaniye, hangi kanal.
 *
 * <p>Yine <b>indeks</b>, metin değil. İkilik dizinin kendisini göndermek teknik olarak
 * zararsız görünür (içinde kişisel veri yok) ama protokol kuralını gevşetmek demek
 * olurdu: "serbest metin yollanmaz" kuralı tam da bu tür istisnalarla aşınır. İstemci
 * {@link com.photonpulse.candy.sifre.IkilikMetin#FISILTI} dizisinden metni alıp
 * kendisi çeviriyor — dizi ortak kodda olduğu için iki taraf ayrışamaz.</p>
 *
 * @param indeks {@code IkilikMetin.FISILTI} içindeki sıra
 * @param sureMs ekranda kalma süresi
 * @param kanal  0 = tam ekran (camscreen kalıbı), 1 = köşe (altyazı kalıbı)
 */
public record IkilikEkranPayload(int indeks, int sureMs, int kanal) implements CustomPacketPayload {

    public static final int KANAL_TAM_EKRAN = 0;
    public static final int KANAL_KOSE = 1;

    public static final CustomPacketPayload.Type<IkilikEkranPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "ikilik_ekran"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IkilikEkranPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, IkilikEkranPayload::indeks,
            ByteBufCodecs.VAR_INT, IkilikEkranPayload::sureMs,
            ByteBufCodecs.VAR_INT, IkilikEkranPayload::kanal,
            IkilikEkranPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
