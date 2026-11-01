package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sunucu tarafi ag — Fabric uygulamasi.
 *
 * {@link #registerGlobalReceiver} iki loader'da da YALNIZCA mod yuklenirken
 * cagrilabilir: NeoForge'da tip ve alici tek cagriyla kaydedilmek zorunda
 * (playToServer'in alicisiz asiri yuklemesi YOK), o yuzden alici da kuyruga
 * giriyor. Fabric'te boyle bir sinir yok; sinir ortak arayuzun sinirina
 * cekildi ki iki loader ayni sozu versin.
 */
public final class SunucuAg {

    private SunucuAg() {}

    /** Fabric'in {@code ServerPlayNetworking.Context}'inden okunan alanlar. */
    public interface Baglam {
        ServerPlayer player();
        MinecraftServer server();
    }

    public interface Alici<T extends CustomPacketPayload> {
        void receive(T payload, Baglam baglam);
    }

    private record FabrikBaglam(ServerPlayer player, MinecraftServer server) implements Baglam {}

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(
            CustomPacketPayload.Type<T> tip, Alici<T> alici) {
        ServerPlayNetworking.registerGlobalReceiver(tip, (payload, baglam) ->
            alici.receive(payload, new FabrikBaglam(baglam.player(), baglam.server())));
    }
}
