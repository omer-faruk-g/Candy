package com.photonpulse.candy.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.photonpulse.candy.Candy;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CandyMixinConfig — client-side mixin efektlerinin on/off toggle'ı.
 * Config dosyası: .minecraft/config/candy-mixin.json
 *
 * Her mixin başlangıçta ilgili flag'ini kontrol eder.
 */
public class CandyMixinConfig {

    public static boolean chatCorruption = true;
    public static boolean hudFlash = true;
    public static boolean deathScreen = true;
    public static boolean cameraShake = true;
    public static boolean camscreen = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        try {
            Path yol = FabricLoader.getInstance().getConfigDir()
                .resolve("candy-mixin.json");
            if (!Files.exists(yol)) {
                save();
                return;
            }
            String json = Files.readString(yol);
            CandyMixinConfig okundu = GSON.fromJson(json, CandyMixinConfig.class);
            if (okundu != null) {
                chatCorruption = okundu.chatCorruption;
                hudFlash = okundu.hudFlash;
                deathScreen = okundu.deathScreen;
                cameraShake = okundu.cameraShake;
                camscreen = okundu.camscreen;
            }
            Candy.LOG.info("[Candy] Mixin config yüklendi: chat={} hud={} death={} camera={}",
                chatCorruption, hudFlash, deathScreen, cameraShake);
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] Config yüklenemedi, defaults: {}", t.getMessage());
        }
    }

    public static void save() {
        try {
            Path yol = FabricLoader.getInstance().getConfigDir()
                .resolve("candy-mixin.json");
            Files.createDirectories(yol.getParent());
            CandyMixinConfig snapshot = new CandyMixinConfig();
            snapshot.chatCorruption = chatCorruption;
            snapshot.hudFlash = hudFlash;
            snapshot.deathScreen = deathScreen;
            snapshot.cameraShake = cameraShake;
            snapshot.camscreen = camscreen;
            Files.writeString(yol, GSON.toJson(snapshot));
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] Config yazılamadı: {}", t.getMessage());
        }
    }
}
