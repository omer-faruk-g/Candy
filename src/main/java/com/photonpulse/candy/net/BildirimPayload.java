package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C (v9.2.0) — "dış kanal" sinyali: sunucu, oyuncuyu oyun penceresinin DIŞINDA
 * korkutmaya değer bir anın geldiğine karar verdi ve kullanılacak fısıltı satırını
 * yolluyor.
 *
 * <h3>NEDEN SADECE ANAHTAR + ARGÜMAN GİDİYOR</h3>
 * {@link com.photonpulse.candy.arkadas.TanidikFisilti} ile birebir aynı içerik:
 * oyun-içi gözlem (tabela cevabı, uykusuz gece, dünya adı…). Serbest metin değil,
 * lang anahtarı + tek {@code %s} argümanı — istemci kendi diline çeviriyor. Yani bu
 * paket oyuncu hakkında şehir/donanım/dosya gibi YENİ bir veri taşımıyor; yalnızca
 * "hangi satır" bilgisini taşıyor.
 *
 * <h3>E-POSTA BU PAKETTE YOK — BİLEREK</h3>
 * Oyuncunun e-posta adresi <b>yalnızca istemcide</b> ({@code config/candy-mixin.json})
 * durur. Minecraft sunucusu onu hiç görmez: sunucu bu paketle sadece "şu satırı dış
 * kanaldan ver" der, e-postayı istemci kendi config'inden okuyup yazarın mektup
 * sunucusuna kendisi yollar ({@link com.photonpulse.candy.client.MektupGonder}). Böylece
 * adres, bağlanılan hiçbir oyun sunucusuna sızmaz.
 *
 * @param anahtar lang anahtarı (ör. {@code candy.tanidik.tabela})
 * @param arg     {@code %s} argümanı; yoksa boş dize
 */
public record BildirimPayload(String anahtar, String arg) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BildirimPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "bildirim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BildirimPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BildirimPayload::anahtar,
            ByteBufCodecs.STRING_UTF8, BildirimPayload::arg,
            BildirimPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
