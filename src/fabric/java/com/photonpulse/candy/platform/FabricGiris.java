package com.photonpulse.candy.platform;

import com.photonpulse.candy.Candy;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric giris noktasi.
 *
 * Candy.java'nin kendisi artik hicbir yukleyici arayuzu uygulamiyor; init
 * govdesi iki loader'da da ayni tek metottur.
 */
public final class FabricGiris implements ModInitializer {

    @Override
    public void onInitialize() {
        Candy.onInitialize();
    }
}
