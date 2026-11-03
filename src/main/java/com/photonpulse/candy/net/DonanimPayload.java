package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — client, gerçek donanım "parça" listesini sunucuya bildirir.
 * Parçalar tek string'de "" ile ayrılır (liste codec'i yerine — daha basit/güvenli).
 * Sunucu böler, HayaletOyuncular diyaloglarında kullanır ("Ben PC'de %s var.").
 *
 * Veri HİÇBİR YERE gönderilmez — sadece aynı oyuncuya oyun içi gösterilir.
 */
public record DonanimPayload(String veri) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DonanimPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "donanim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DonanimPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DonanimPayload::veri,
            DonanimPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
