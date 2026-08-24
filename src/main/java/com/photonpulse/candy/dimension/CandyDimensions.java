package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * CandyDimensions (v3.0 Phase 1) — dimension registry keys.
 *
 * Actual dimension is defined in data/candy/dimension/*.json
 * data/candy/dimension_type/*.json — mirror + anti_mirror
 */
public class CandyDimensions {

    public static final RegistryKey<World> MIRROR = RegistryKey.of(
        RegistryKeys.WORLD, Identifier.of(Candy.MOD_ID, "mirror"));

    public static final RegistryKey<World> ANTI_MIRROR = RegistryKey.of(
        RegistryKeys.WORLD, Identifier.of(Candy.MOD_ID, "anti_mirror"));

    // v3.2 (idea 13) Ayna — birebir overworld kılığı. Mirror'ın aksine HİÇBİR ipucu yok:
    // F3 "overworld" der, gök/sis/ışık normal. Kopya + düzenek + takas mekaniği burada.
    public static final RegistryKey<World> AYNA = RegistryKey.of(
        RegistryKeys.WORLD, Identifier.of(Candy.MOD_ID, "ayna"));
}
