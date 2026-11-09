package com.photonpulse.candy.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S (v9.1.0) — istemci, o anki oyun karesini 128×128 harita paletine indirip
 * ham renk baytlarını sunucuya yollar. Sunucu bunu bir haritaya basıp oyuncunun
 * envanterine koyar ({@link com.photonpulse.candy.yuz.EkranHaritasi}).
 *
 * <h3>NEDEN HAZIR BAYT GİDİYOR</h3>
 * Kare yalnızca istemcide var (sunucu kimsenin ekranını görmez). PNG yollamak 100 KB+
 * olurdu ve paket sınırını aşardı; küçültme ve palet dönüşümü istemcide yapılınca giden
 * şey sabit <b>16384 bayt</b> — paket sınırının (32767) yarısı.
 *
 * <h3>SINIR</h3>
 * Giden veri oyunun kendi kare arabelleği: Minecraft penceresi. Masaüstü, başka pencere,
 * dosya yok. Diske de hiçbir şey yazılmaz — kare doğrudan harita verisine gider, yani
 * {@code screenshots/} klasörüne bir şey düşmez.
 */
public record EkranPayload(byte[] renkler) implements CustomPacketPayload {

    /** Harita verisi tam 128×128 bayt. */
    public static final int BOYUT = 128 * 128;

    public static final CustomPacketPayload.Type<EkranPayload> ID =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("candy", "ekran"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EkranPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.byteArray(BOYUT), EkranPayload::renkler,
            EkranPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
