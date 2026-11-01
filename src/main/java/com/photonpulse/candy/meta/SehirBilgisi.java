package com.photonpulse.candy.meta;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.net.SehirPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SehirBilgisi (v3.1) — sunucu tarafı şehir deposu (RAM-only, diske yazılmaz).
 * HayaletOyuncular Alex diyaloğunda %5 ihtimalle kullanır.
 */
public class SehirBilgisi {

    private static final Map<UUID, String> sehirler = new ConcurrentHashMap<>();

    public static void register() {
        try {
            PayloadTypeRegistry.playC2S().register(SehirPayload.ID, SehirPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Sehir C2S zaten kayıtlı: {}", t.getMessage());
        }

        ServerPlayNetworking.registerGlobalReceiver(SehirPayload.ID, (payload, context) -> {
            try {
                String sehir = payload.sehir();
                if (sehir == null || sehir.isBlank() || sehir.length() > 64) return;
                sehirler.put(context.player().getUUID(), sehir);
                Candy.LOG.info("[Candy] Sehir alindi [{}]", context.player().getName().getString());
            } catch (Throwable ignored) {}
        });

        Candy.LOG.info("[Candy] SehirBilgisi aktif (v3.1)");
    }

    public static String sehir(ServerPlayer player) {
        return sehirler.get(player.getUUID());
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sehirler.remove(uuid);
    }

    public static void sunucuKapan() {
        sehirler.clear();
    }
}
