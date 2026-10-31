package com.photonpulse.candy.entity;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.boss.TrueRealCandyEntity;
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

    public static final RegistryKey<EntityType<?>> TRUE_REAL_CANDY_KEY = RegistryKey.of(
        RegistryKeys.ENTITY_TYPE, Identifier.of(Candy.MOD_ID, "true_real_candy"));

    public static final EntityType<TrueRealCandyEntity> TRUE_REAL_CANDY = Registry.register(
        Registries.ENTITY_TYPE, TRUE_REAL_CANDY_KEY,
        EntityType.Builder.<TrueRealCandyEntity>create(TrueRealCandyEntity::new, SpawnGroup.MONSTER)
            .dimensions(0.7f, 2.1f)
            .eyeHeight(1.85f)
            .maxTrackingRange(96)
            .trackingTickInterval(2)
            .build(TRUE_REAL_CANDY_KEY));

    // ── v8.3: Arkadaş ──
    // Oyuncu boyutlarında: silueti bir oyuncudan ayırt edilmesin. Teni siyah
    // (textures/entity/arkadas.png), adı başının üstünde.
    public static final RegistryKey<EntityType<?>> ARKADAS_KEY = RegistryKey.of(
        RegistryKeys.ENTITY_TYPE, Identifier.of(Candy.MOD_ID, "arkadas"));

    public static final EntityType<com.photonpulse.candy.arkadas.ArkadasEntity> ARKADAS =
        Registry.register(
            Registries.ENTITY_TYPE, ARKADAS_KEY,
            EntityType.Builder.<com.photonpulse.candy.arkadas.ArkadasEntity>create(
                    com.photonpulse.candy.arkadas.ArkadasEntity::new, SpawnGroup.MONSTER)
                .dimensions(0.6f, 1.95f)
                .eyeHeight(1.62f)
                .maxTrackingRange(96)
                .trackingTickInterval(2)
                .build(ARKADAS_KEY));

    public static void register() {
        // Static initializer yapar; attribute'ları explicit ekle
        FabricDefaultAttributeRegistry.register(DOPPEL, CandyDoppelEntity.createDoppelAttributes());
        FabricDefaultAttributeRegistry.register(TRUE_REAL_CANDY, TrueRealCandyEntity.createBossAttributes());
        FabricDefaultAttributeRegistry.register(ARKADAS,
            com.photonpulse.candy.arkadas.ArkadasEntity.createArkadasAttributes());
        Candy.LOG.info(
            "[Candy] Entity type registered: candy:doppel, candy:true_real_candy, candy:arkadas");
    }
}
