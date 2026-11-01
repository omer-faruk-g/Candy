package com.photonpulse.candy.meta;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.sign.SignSystem;
import com.photonpulse.candy.platform.SunucuTick;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * ModFarkindaligi (v3.0) — Candy kurulu modları biliyor.
 *
 * Oyuncunun gerçekten kurduğu modları okur ve tabelalarda dördüncü duvarı kırarak sorar:
 * "X modu ne işe yarıyor?", "Y'yi neden kullanıyorsun?" ...
 * Candy'nin bilmemesi gereken şeyi bilmesi = gözetleme korkusu.
 */
public class ModFarkindaligi {

    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");
    private static final int TICK_ARALIGI = DEBUG ? 400 : 4000;   // debug 20sn / normal ~3.3 dk
    private static final long COOLDOWN = DEBUG ? 400L : 6000L;    // debug 20sn / normal 5 dk
    private static final double IHTIMAL = DEBUG ? 1.0 : 0.2;

    private static final Random RNG = new Random();
    private static final int SORU_SAYISI = 6;

    // Candy'nin "görmezden geleceği" altyapı modları
    private static final Set<String> GIZLE = Set.of(
        "minecraft", "java", "fabricloader", "fabric-api", "fabric",
        "candy", "mixinextras", "fabric-language-kotlin"
    );

    // Kurulu kullanıcı modlarının görünen adları
    private static final List<String> MOD_ADLARI = new ArrayList<>();

    public static void register() {
        modlariTopla();
        SunucuTick.END_SERVER_TICK.register(ModFarkindaligi::onTick);
        Candy.LOG.info("[Candy] ModFarkindaligi aktif — {} kullanıcı modu görüldü ({})",
            MOD_ADLARI.size(), DEBUG ? "DEBUG 20sn" : "3.3 dk kontrol, 5 dk cooldown, %20");
    }

    private static void modlariTopla() {
        MOD_ADLARI.clear();
        for (var container : FabricLoader.getInstance().getAllMods()) {
            var md = container.getMetadata();
            String id = md.getId();
            if (id == null) continue;
            if (GIZLE.contains(id)) continue;
            if (id.startsWith("fabric-")) continue;   // Fabric API alt modülleri
            if ("builtin".equals(md.getType())) continue;
            String ad = md.getName();
            if (ad == null || ad.isBlank()) ad = id;
            MOD_ADLARI.add(ad);
        }
    }

    private static void onTick(MinecraftServer server) {
        if (MOD_ADLARI.isEmpty()) return;
        if (server.getTickCount() % TICK_ARALIGI != 0) return;
        long simdi = server.overworld().getGameTime();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;
            if (tp.bossOldurulmeTick > 0) continue; // WIN 30dk sessizlik
            if (simdi - tp.sonModSorusuTick < COOLDOWN) continue;
            if (RNG.nextDouble() >= IHTIMAL) continue;

            String mod = MOD_ADLARI.get(RNG.nextInt(MOD_ADLARI.size()));
            String key = "candy.mod.soru." + (1 + RNG.nextInt(SORU_SAYISI));
            Component soru = Component.translatable(key, mod);

            if (SignSystem.candyTabelaYerlestir(player, soru)) {
                tp.sonModSorusuTick = simdi;
                Candy.LOG.info("[Candy] ModFarkindaligi: {} → tabela sorusu '{}' (mod: {})",
                    player.getName().getString(), key, mod);
            }
        }
    }
}
