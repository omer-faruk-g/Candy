package com.photonpulse.candy.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S — client, gerçek donanım "parça" listesini sunucuya bildirir.
 * Parçalar tek string'de "" ile ayrılır (liste codec'i yerine — daha basit/güvenli).
 * Sunucu böler, HayaletOyuncular diyaloglarında kullanır ("Ben PC'de %s var.").
 *
 * Veri HİÇBİR YERE gönderilmez — sadece aynı oyuncuya oyun içi gösterilir.
 */
public record DonanimPayload(String veri) implements CustomPayload {

    public static final CustomPayload.Id<DonanimPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "donanim"));

    public static final PacketCodec<RegistryByteBuf, DonanimPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, DonanimPayload::veri,
            DonanimPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
