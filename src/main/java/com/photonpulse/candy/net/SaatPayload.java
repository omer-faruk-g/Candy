package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S (v9.1.0) — istemci, oyuncunun GERÇEK duvar saatini sunucuya bildirir:
 * gün içi dakika (0–1439) ve saat dilimi kimliği ("Europe/Istanbul").
 *
 * <h3>NEDEN SUNUCU KENDİ SAATİNE BAKMIYOR</h3>
 * {@code DerinAnaliz} bugüne kadar sunucu JVM'inin saatini okuyordu; tek oyunculuda
 * bu oyuncunun saatidir, ama gerçek bir sunucuda <b>sunucunun</b> saatidir. Korku
 * "sen gecenin üçünde oynuyorsun" cümlesinden geliyor, barındırıcının saat diliminden
 * değil. O yüzden saat, şehir ve donanım gibi istemciden geliyor.
 *
 * <h3>NE GİTMİYOR</h3>
 * Tarih yok, yıl yok, takvim yok, çalışma süresi yok. Giden tek şey günün hangi
 * dakikasında olunduğu ve dilim adı — hiçbir yere kaydedilmez, yalnızca o oturum
 * boyunca RAM'de durur ({@link com.photonpulse.candy.meta.SaatBilgisi}).
 */
public record SaatPayload(int dakika, String dilim) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaatPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "saat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaatPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SaatPayload::dakika,
            ByteBufCodecs.STRING_UTF8, SaatPayload::dilim,
            SaatPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
