package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

/**
 * Varlik nitelik kaydi — Fabric uygulamasi.
 */
public final class NitelikKayit {

    private NitelikKayit() {}

    public static void register(EntityType<? extends LivingEntity> tip,
                                AttributeSupplier.Builder nitelikler) {
        FabricDefaultAttributeRegistry.register(tip, nitelikler);
    }
}
