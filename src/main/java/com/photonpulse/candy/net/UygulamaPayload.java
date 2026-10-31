package com.photonpulse.candy.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S — client, açık olan uygulamalardan <b>yalnızca bir tanesinin beyaz liste
 * indeksini</b> bildirir. Uygulama <i>adı</i> bile ağa çıkmaz: iki uçta aynı sabit
 * liste var ve taşınan tek şey o listedeki sıra numarası.
 *
 * <h3>NEDEN İNDEKS, AD DEĞİL</h3>
 * Serbest metin gönderilse client'ın gördüğü her şey sunucuya taşınabilir hâle gelirdi.
 * İndeks, protokolün taşıyabileceği bilgiyi fiziksel olarak beyaz listeyle sınırlıyor:
 * listede olmayan bir şey ifade edilemiyor. {@code -1} → eşleşme yok.
 *
 * Değer sunucuda yalnızca RAM'de durur, diske yazılmaz, Vagus prompt'una girmez.
 */
public record UygulamaPayload(int indeks) implements CustomPayload {

    public static final CustomPayload.Id<UygulamaPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "uygulama"));

    public static final PacketCodec<RegistryByteBuf, UygulamaPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.VAR_INT, UygulamaPayload::indeks,
            UygulamaPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
