package com.photonpulse.candy.platform;

import com.photonpulse.candy.Candy;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge giris noktasi.
 *
 * Candy.java'nin kendisi artik hicbir yukleyici arayuzu uygulamiyor; init
 * govdesi iki loader'da da ayni tek metottur.
 */
@Mod(Candy.MOD_ID)
public final class NeoForgeGiris {

    public NeoForgeGiris(IEventBus modVeriYolu, ModContainer kap) {
        Candy.onInitialize();
    }
}
