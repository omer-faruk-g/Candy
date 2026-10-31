package com.photonpulse.candy.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

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
public record IkilikEkranPayload(int indeks, int sureMs, int kanal) implements CustomPayload {

    public static final int KANAL_TAM_EKRAN = 0;
    public static final int KANAL_KOSE = 1;

    public static final CustomPayload.Id<IkilikEkranPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "ikilik_ekran"));

    public static final PacketCodec<RegistryByteBuf, IkilikEkranPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.VAR_INT, IkilikEkranPayload::indeks,
            PacketCodecs.VAR_INT, IkilikEkranPayload::sureMs,
            PacketCodecs.VAR_INT, IkilikEkranPayload::kanal,
            IkilikEkranPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
