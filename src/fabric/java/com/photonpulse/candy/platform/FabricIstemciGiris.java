package com.photonpulse.candy.platform;

import com.photonpulse.candy.client.CandyClient;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric istemci giris noktasi.
 */
public final class FabricIstemciGiris implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CandyClient.onInitializeClient();
    }
}
