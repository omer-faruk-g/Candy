package com.photonpulse.candy.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Istemci tarafi ag — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi. {@link #registerGlobalReceiver}
 * icin SunucuAg'daki ayni init-zamani sinirlamasi gecerli (bkz. AgKayit).
 *
 * canSend: Fabric'in {@code ClientPlayNetworking.canSend(type)} karsiligi
 * NeoForge'da baglantinin kendisinde — {@code ICommonPacketListener.hasChannel}.
 * Baglanti yoksa (menude) Fabric de false donuyor, burada da oyle.
 */
public final class IstemciAg {

    private IstemciAg() {}

    /** Fabric'in {@code ClientPlayNetworking.Context}'inin tasidigi alan. */
    public interface Baglam {
        Minecraft client();
    }

    public interface Alici<T extends CustomPacketPayload> {
        void receive(T payload, Baglam baglam);
    }

    private record NeoBaglam(Minecraft client) implements Baglam {}

    public static void send(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    public static boolean canSend(CustomPacketPayload.Type<?> tip) {
        ClientPacketListener baglanti = Minecraft.getInstance().getConnection();
        return baglanti != null && baglanti.hasChannel(tip);
    }

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(
            CustomPacketPayload.Type<T> tip, Alici<T> alici) {
        AgKayit.alici(tip, (payload, baglam) ->
            alici.receive(payload, new NeoBaglam(Minecraft.getInstance())));
    }
}
