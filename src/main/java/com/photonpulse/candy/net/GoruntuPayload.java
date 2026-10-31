package com.photonpulse.candy.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C — Candy oyuncunun görüş ayarlarını geçici olarak değiştirir.
 *
 * {@code fov}/{@code render} = -1 → o ayara dokunulmaz. {@code geriAl} = true → kayıtlı
 * orijinal değerlere dönülür ve diğer alanlar yok sayılır.
 *
 * Ayarlar KALICI DEĞİL: oyundan çıkışta (ve çökme sonrası ilk açılışta) geri alınır.
 */
public record GoruntuPayload(int fov, int render, boolean geriAl) implements CustomPayload {

    public static final CustomPayload.Id<GoruntuPayload> ID =
        new CustomPayload.Id<>(Identifier.of("candy", "goruntu"));

    public static final PacketCodec<RegistryByteBuf, GoruntuPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.VAR_INT, GoruntuPayload::fov,
            PacketCodecs.VAR_INT, GoruntuPayload::render,
            PacketCodecs.BOOLEAN, GoruntuPayload::geriAl,
            GoruntuPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
