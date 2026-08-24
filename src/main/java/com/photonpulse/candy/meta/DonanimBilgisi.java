package com.photonpulse.candy.meta;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.net.DonanimPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DonanimBilgisi (v3.0) — sunucu tarafi donanim parca deposu.
 *
 * Client DonanimPayload gonderir -> burada UUID basina saklanir.
 * HayaletOyuncular rastgele bir parcayi "Ben PC'de %s var." diyalogunda kullanir.
 * Client mod'suz (vanilla) ise liste bos kalir -> sunucu-yani fallback (OS, cekirdek).
 *
 * Veri HICBIR YERE gonderilmez — sadece ayni oyuncuya oyun ici gosterilir.
 */
public class DonanimBilgisi {

    // Client ile ortak ayirici — donanim adlarinda newline olmaz.
    public static final String AYIRICI = "\n";

    private static final Random RNG = new Random();
    // UUID -> parca listesi (GPU, CPU, OS, RAM...)
    private static final Map<UUID, List<String>> parcalar = new ConcurrentHashMap<>();

    public static void register() {
        // C2S payload tipini sunucu tarafinda da kaydet (client zaten kaydediyor)
        try {
            PayloadTypeRegistry.playC2S().register(DonanimPayload.ID, DonanimPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Donanim C2S zaten kayitli: {}", t.getMessage());
        }

        ServerPlayNetworking.registerGlobalReceiver(DonanimPayload.ID, (payload, context) -> {
            try {
                String veri = payload.veri();
                if (veri == null || veri.isBlank()) return;
                List<String> liste = new ArrayList<>();
                for (String s : veri.split(AYIRICI)) {
                    String t = s.trim();
                    if (!t.isEmpty() && t.length() <= 64) liste.add(t);
                }
                if (!liste.isEmpty()) {
                    parcalar.put(context.player().getUuid(), liste);
                    Candy.LOG.info("[Candy] Donanim alindi [{}]: {} parca",
                        context.player().getName().getString(), liste.size());
                }
            } catch (Throwable ignored) {}
        });

        Candy.LOG.info("[Candy] DonanimBilgisi aktif — donanim farkindaligi (v3.0)");
    }

    /**
     * Oyuncu icin rastgele bir parca adi doner. Client parca gondermisse ondan;
     * yoksa sunucu-yani fallback (OS + cekirdek sayisi).
     */
    public static String rastgeleParca(ServerPlayerEntity player) {
        List<String> liste = parcalar.get(player.getUuid());
        if (liste != null && !liste.isEmpty()) {
            return liste.get(RNG.nextInt(liste.size()));
        }
        // Fallback — client mod'suz. Sunucu JVM'inden ne varsa.
        List<String> fb = new ArrayList<>();
        try {
            String os = System.getProperty("os.name");
            if (os != null) fb.add(os);
        } catch (Throwable ignored) {}
        try {
            fb.add(Runtime.getRuntime().availableProcessors() + " cekirdek");
        } catch (Throwable ignored) {}
        if (fb.isEmpty()) return null;
        return fb.get(RNG.nextInt(fb.size()));
    }

    public static void oyuncuAyrildi(UUID uuid) {
        parcalar.remove(uuid);
    }

    public static void sunucuKapan() {
        parcalar.clear();
    }
}
