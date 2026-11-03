package com.photonpulse.candy.platform;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.client.CandyClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge istemci giris noktasi.
 *
 * Fabric'in ClientModInitializer'inin karsiligi @Mod'un dist bayragi:
 * sinif yalnizca istemcide yuklenir, sunucuda hic ornenmez.
 */
@Mod(value = Candy.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeIstemciGiris {

    public NeoForgeIstemciGiris(IEventBus modVeriYolu, ModContainer kap) {
        CandyClient.onInitializeClient();
    }
}
