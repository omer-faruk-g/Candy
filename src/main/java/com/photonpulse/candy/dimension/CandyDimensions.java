package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * CandyDimensions (v3.0 Phase 1) — dimension registry keys.
 *
 * Actual dimension is defined in data/candy/dimension/*.json
 * data/candy/dimension_type/*.json — mirror + anti_mirror
 */
public class CandyDimensions {

    public static final ResourceKey<Level> MIRROR = ResourceKey.create(
        Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Candy.MOD_ID, "mirror"));

    public static final ResourceKey<Level> ANTI_MIRROR = ResourceKey.create(
        Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Candy.MOD_ID, "anti_mirror"));

    // v3.2 (idea 13) Ayna — birebir overworld kılığı. Mirror'ın aksine HİÇBİR ipucu yok:
    // F3 "overworld" der, gök/sis/ışık normal. Kopya + düzenek + takas mekaniği burada.
    public static final ResourceKey<Level> AYNA = ResourceKey.create(
        Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Candy.MOD_ID, "ayna"));
}
