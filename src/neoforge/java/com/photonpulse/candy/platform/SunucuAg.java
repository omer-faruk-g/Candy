package com.photonpulse.candy.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sunucu tarafi ag — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi; cagri yerleri iki tarafta da ayni
 * kodu yaziyor.
 *
 * {@link #registerGlobalReceiver} aliciyi {@link AgKayit} kuyruguna veriyor,
 * cunku NeoForge'da C2S tipi ve alicisi tek cagriya girmek zorunda
 * (playToServer'in alicisiz asiri yuklemesi yok). Bu yuzden ortak arayuzun
 * sozu "alici kaydi yalnizca mod yuklenirken" — Fabric'te bu sinir
 * teknik olarak yok, ama iki loader ayni sozu vermesi icin oraya da yazildi.
 * Init'ten sonra cagrilirsa AgKayit hata logluyor; sessizce yanlis calismiyor.
 */
public final class SunucuAg {

    private SunucuAg() {}

    /** Fabric'in {@code ServerPlayNetworking.Context}'inin tasidigi alanlar. */
    public interface Baglam {
        ServerPlayer player();
        MinecraftServer server();
    }

    public interface Alici<T extends CustomPacketPayload> {
        void receive(T payload, Baglam baglam);
    }

    private record NeoBaglam(ServerPlayer player, MinecraftServer server) implements Baglam {}

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(
            CustomPacketPayload.Type<T> tip, Alici<T> alici) {
        AgKayit.alici(tip, (payload, baglam) -> {
            // IPayloadContext.player() Player dondurur; C2S yalnizca sunucuda
            // islenir, yani burada her zaman ServerPlayer.
            if (!(baglam.player() instanceof ServerPlayer oyuncu)) return;
            alici.receive(payload, new NeoBaglam(oyuncu, oyuncu.level().getServer()));
        });
    }
}
