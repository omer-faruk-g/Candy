package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Istemci tarafi ag — Fabric uygulamasi.
 *
 * Yalnizca istemci JVM'inde yuklenir (sunucuda hicbir sey bu sinifa atif yapmiyor),
 * CandyClientNet'in zaten calistigi yol. {@link #registerGlobalReceiver} icin
 * SunucuAg'daki ayni init-zamani sinirlamasi gecerli.
 */
public final class IstemciAg {

    private IstemciAg() {}

    /** Fabric'in {@code ClientPlayNetworking.Context}'inden okunan alan. */
    public interface Baglam {
        Minecraft client();
    }

    public interface Alici<T extends CustomPacketPayload> {
        void receive(T payload, Baglam baglam);
    }

    private record FabrikBaglam(Minecraft client) implements Baglam {}

    public static void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    public static boolean canSend(CustomPacketPayload.Type<?> tip) {
        return ClientPlayNetworking.canSend(tip);
    }

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(
            CustomPacketPayload.Type<T> tip, Alici<T> alici) {
        ClientPlayNetworking.registerGlobalReceiver(tip, (payload, baglam) ->
            alici.receive(payload, new FabrikBaglam(baglam.client())));
    }
}
