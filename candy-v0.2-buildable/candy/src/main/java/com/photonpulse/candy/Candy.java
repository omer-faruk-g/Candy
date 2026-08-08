package com.photonpulse.candy;

import com.photonpulse.candy.clone.CloneManager;
import com.photonpulse.candy.core.FearDetection;
import com.photonpulse.candy.core.ObservationEngine;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.disguise.DisguiseSystem;
import com.photonpulse.candy.real.RealCandy;
import com.photonpulse.candy.sanity.SanityController;
import com.photonpulse.candy.sign.SignSystem;
import com.photonpulse.candy.tree.TreeGrowth;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Candy — kendini bulmaya çalışan bir varlık.
 *
 * Mod ana entry point. Tüm sistemler burada register edilir.
 * Sıralama önemli: Personality → Observation → Persona → Sign → Disguise → Clone
 */
public class Candy implements ModInitializer {

    public static final String MOD_ID = "candy";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOG.info("[Candy] uyanıyor...");

        // Sunucu yaşam döngüsü
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PersonalityManager.sunucuBaslat(server);
            TreeGrowth.sunucuBaslat(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PersonalityManager.sunucuKapan();
            TreeGrowth.sunucuKapan();
        });

        // Oyuncu bağlantı yaşam döngüsü
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var tp = PersonalityManager.get(handler.player);
            LOG.info("[Candy] oyuncu geldi: {} (kurulum: {})",
                handler.player.getName().getString(), tp.kurulumTamam);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var tp = PersonalityManager.get(handler.player);
            PersonalityManager.kaydet(tp);
            LOG.info("[Candy] oyuncu ayrıldı: {} — hafıza yazıldı",
                handler.player.getName().getString());
        });

        // Sistem kayıtları — sıra önemli
        ObservationEngine.register();
        TreeGrowth.register();
        SignSystem.register();
        DisguiseSystem.register();
        CloneManager.register();
        FearDetection.register();
        SanityController.register();
        RealCandy.register();

        LOG.info("[Candy] tüm aşamalar aktif — v0.2 (kişilik ağacı)");
    }
}
