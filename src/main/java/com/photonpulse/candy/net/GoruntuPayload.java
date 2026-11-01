package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — Candy oyuncunun görüş ayarlarını geçici olarak değiştirir.
 *
 * {@code fov}/{@code render} = -1 → o ayara dokunulmaz. {@code geriAl} = true → kayıtlı
 * orijinal değerlere dönülür ve diğer alanlar yok sayılır.
 *
 * Ayarlar KALICI DEĞİL: oyundan çıkışta (ve çökme sonrası ilk açılışta) geri alınır.
 */
public record GoruntuPayload(int fov, int render, boolean geriAl) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GoruntuPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "goruntu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GoruntuPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GoruntuPayload::fov,
            ByteBufCodecs.VAR_INT, GoruntuPayload::render,
            ByteBufCodecs.BOOL, GoruntuPayload::geriAl,
            GoruntuPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
