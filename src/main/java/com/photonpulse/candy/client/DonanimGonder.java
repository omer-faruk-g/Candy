package com.photonpulse.candy.client;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.systems.RenderSystem;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.net.DonanimPayload;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import com.photonpulse.candy.platform.IstemciBaglanti;
import com.photonpulse.candy.platform.IstemciAg;
import com.photonpulse.candy.platform.AgKayit;

import java.util.ArrayList;
import java.util.List;

/**
 * DonanimGonder (v3.0, client) — gercek donanim parcalarini toplar ve sunucuya yollar.
 *
 * GPU: RenderSystem.getDevice().getRenderer()  (LWJGL/GL — sadece client'ta var)
 * CPU: GLX._getCpuInfo()
 * OS : System property
 * RAM: JVM'e ayrilan max bellek (yaklasik)
 *
 * Join'de bir kez gonderilir. Veri hicbir yere gitmez — sunucu (ayni oyuncuya)
 * "Ben PC'de %s var." diyalogunda gosterir.
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
public class DonanimGonder {

    public static void register() {
        // C2S payload tipini client tarafinda kaydet
        try {
            AgKayit.playC2S().register(DonanimPayload.ID, DonanimPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Donanim C2S zaten kayitli (client): {}", t.getMessage());
        }

        // Sunucuya baglaninca bir kez gonder
        IstemciBaglanti.JOIN.register(client -> {
            client.execute(() -> {
                try {
                    // Sunucu bu payload'i taniyor mu? (vanilla sunucuya yollamayalim)
                    if (!IstemciAg.canSend(DonanimPayload.ID)) return;
                    String veri = donanimTopla();
                    if (veri.isBlank()) return;
                    IstemciAg.send(new DonanimPayload(veri));
                    Candy.LOG.info("[Candy] Donanim gonderildi");
                } catch (Throwable t) {
                    Candy.LOG.debug("[Candy] Donanim gonderilemedi: {}", t.getMessage());
                }
            });
        });
    }

    private static String donanimTopla() {
        List<String> parcalar = new ArrayList<>();

        // GPU
        try {
            var dev = RenderSystem.tryGetDevice();
            if (dev != null) {
                String r = dev.getRenderer();
                if (r != null && !r.isBlank()) parcalar.add(gpuTemizle(r));
            }
        } catch (Throwable ignored) {}

        // CPU
        try {
            String cpu = GLX._getCpuInfo();
            if (cpu != null && !cpu.isBlank()) parcalar.add(cpuTemizle(cpu));
        } catch (Throwable ignored) {}

        // OS
        try {
            String os = System.getProperty("os.name");
            String ver = System.getProperty("os.version");
            if (os != null) parcalar.add((ver != null ? os + " " + ver : os).trim());
        } catch (Throwable ignored) {}

        // RAM (JVM max — yaklasik)
        try {
            long gb = Math.round(Runtime.getRuntime().maxMemory() / (1024.0 * 1024 * 1024));
            if (gb > 0) parcalar.add(gb + " GB RAM");
        } catch (Throwable ignored) {}

        return String.join("\n", parcalar);
    }

    /** "NVIDIA GeForce RTX 3060/PCIe/SSE2" -> "NVIDIA GeForce RTX 3060" */
    private static String gpuTemizle(String s) {
        int slash = s.indexOf('/');
        if (slash > 0) s = s.substring(0, slash);
        return kes(s.trim(), 48);
    }

    /** "8x 11th Gen Intel(R) Core(TM) i5-11400F @ 2.60GHz" -> sadelestir */
    private static String cpuTemizle(String s) {
        s = s.replace("(R)", "").replace("(TM)", "").replaceAll("\\s+", " ");
        // "8x " onekini koru — cekirdek sayisi ilginc
        return kes(s.trim(), 48);
    }

    private static String kes(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max).trim();
    }
}
