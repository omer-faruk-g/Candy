package com.photonpulse.candy.simulation;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;

/**
 * SimulationEngine — Candy'nin iç simülasyonu.
 *
 * Gerçek TruePersonality'nin ZITTINI, hayali SimulatedPersonality'de biriktirir.
 * Asymptotik kayma — anlık sıçrama yok.
 *
 * Zıt eşleştirme tablosu (v0.3 spesifikasyonu):
 *   kacOrani    ↔  saldirOrani
 *   donOrani    ↔  kesfetOrani
 *   aciklik     ↔  inkarEgilimi
 *   kesfetmeIstegi ↔  (1 - kesfetmeIstegi)
 *   panikEsigi  ↔  (1 - panikEsigi)
 */
public class SimulationEngine {

    private static final int TICK_ARALIGI = 100;   // 5 saniye
    private static final int AUTOSAVE_ARALIGI = 6000;
    private static int tickSayaci = 0;

    // Kayma hızları — düşük, uzun vadede birikmesi için
    private static final double HIZ_HAREKET = 0.03;
    private static final double HIZ_PSIKO = 0.02;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SimulationEngine::onTick);
        Candy.LOG.info("[Candy] SimulationEngine aktif");
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI == 0) {
            for (Map.Entry<UUID, TruePersonality> e : PersonalityManager.tumOyuncular().entrySet()) {
                TruePersonality tp = e.getValue();
                if (!tp.kurulumTamam) continue;
                SimulatedPersonality sp = SimulationManager.get(e.getKey());
                simuleEt(tp, sp);
            }
        }
        // v4.0 FAZ KAYDIRMA: 4 autosave sistemi de %6000==0 idi -> ayni tick'te
        // 4 senkron disk yazimi (10 oyuncuda ~380KB tek tick = gorunur stutter).
        // Bu sistem simulation_*.json icin 3000 fazina kaydirildi.
        if (tickSayaci % AUTOSAVE_ARALIGI == 3000) {
            SimulationManager.hepsiniKaydet();
        }
    }

    /**
     * Gerçek kişiliğin zıddını hayaliye pushla. TruePersonality DEĞİŞMEZ.
     */
    public static void simuleEt(TruePersonality tp, SimulatedPersonality sp) {
        sp.saldirOrani  = TruePersonality.kayma(sp.saldirOrani,  tp.kacOrani,       HIZ_HAREKET);
        sp.kacOrani     = TruePersonality.kayma(sp.kacOrani,     tp.saldirOrani,    HIZ_HAREKET);
        sp.kesfetOrani  = TruePersonality.kayma(sp.kesfetOrani,  tp.donOrani,       HIZ_HAREKET);
        sp.donOrani     = TruePersonality.kayma(sp.donOrani,     tp.kesfetOrani,    HIZ_HAREKET);

        sp.inkarEgilimi = TruePersonality.kayma(sp.inkarEgilimi, tp.aciklik,        HIZ_PSIKO);
        sp.aciklik      = TruePersonality.kayma(sp.aciklik,      tp.inkarEgilimi,   HIZ_PSIKO);

        sp.kesfetmeIstegi = TruePersonality.kayma(sp.kesfetmeIstegi, 1.0 - tp.kesfetmeIstegi, HIZ_PSIKO);
        sp.panikEsigi     = TruePersonality.kayma(sp.panikEsigi,     1.0 - tp.panikEsigi,     HIZ_PSIKO);
    }
}
