package com.photonpulse.candy.entity;

import com.photonpulse.candy.Candy;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Candy entity registry.
 * Sunucu init sırasında register çağrılır (client init'ten önce).
 */
public class CandyEntities {

    public static final RegistryKey<EntityType<?>> DOPPEL_KEY = RegistryKey.of(
        RegistryKeys.ENTITY_TYPE, Identifier.of(Candy.MOD_ID, "doppel"));

    public static final EntityType<CandyDoppelEntity> DOPPEL = Registry.register(
        Registries.ENTITY_TYPE, DOPPEL_KEY,
        EntityType.Builder.<CandyDoppelEntity>create(CandyDoppelEntity::new, SpawnGroup.MONSTER)
            .dimensions(0.6f, 1.95f)   // Player boyu
            .eyeHeight(1.62f)
            .maxTrackingRange(80)
            .trackingTickInterval(3)
            .build(DOPPEL_KEY));

    public static void register() {
        // Static initializer yapar; attribute'ları explicit ekle
        FabricDefaultAttributeRegistry.register(DOPPEL, CandyDoppelEntity.createDoppelAttributes());
        Candy.LOG.info("[Candy] Entity type registered: candy:doppel");
    }
}
