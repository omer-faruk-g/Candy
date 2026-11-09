package com.photonpulse.candy.entity;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.boss.TrueRealCandyEntity;
import com.photonpulse.candy.platform.NitelikKayit;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Candy entity registry.
 *
 * Kayit register() icinde yapilir, static initializer'da DEGIL. Eskiden
 * alanlar "static final X = Registry.register(...)" seklindeydi ve kayit sinif
 * yuklenirken oluyordu. Fabric'te bu calisiyor cunku mod init registry'ler
 * donmadan once kosar; NeoForge'da mod kurucusu caliştiginda BuiltInRegistries
 * zaten donmustur ve ayni satir
 *
 *     IllegalStateException: Registry is already frozen
 *
 * atip modu acilista dusuruyordu. Kayit artik KayitKapisi uzerinden dogru anda
 * tetikleniyor (Fabric: hemen, NeoForge: ilgili RegisterEvent).
 *
 * Alanlar bu yuzden "final" degil. Okuma yerleri (24 tane) degismedi; hepsi
 * kayittan sonra okuyor.
 */
public class CandyEntities {

    public static final ResourceKey<EntityType<?>> DOPPEL_KEY = ResourceKey.create(
        Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Candy.MOD_ID, "doppel"));

    public static EntityType<CandyDoppelEntity> DOPPEL;

    public static final ResourceKey<EntityType<?>> TRUE_REAL_CANDY_KEY = ResourceKey.create(
        Registries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath(Candy.MOD_ID, "true_real_candy"));

    public static EntityType<TrueRealCandyEntity> TRUE_REAL_CANDY;

    // ── v8.3: Arkadaş ──
    // Oyuncu boyutlarında: silueti bir oyuncudan ayırt edilmesin. Teni siyah
    // (textures/entity/arkadas.png), adı başının üstünde.
    public static final ResourceKey<EntityType<?>> ARKADAS_KEY = ResourceKey.create(
        Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Candy.MOD_ID, "arkadas"));

    public static EntityType<com.photonpulse.candy.arkadas.ArkadasEntity> ARKADAS;

    public static void register() {
        DOPPEL = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, DOPPEL_KEY,
            EntityType.Builder.<CandyDoppelEntity>of(CandyDoppelEntity::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f)   // Player boyu
                .eyeHeight(1.62f)
                .clientTrackingRange(80)
                .updateInterval(3)
                .build(DOPPEL_KEY));

        TRUE_REAL_CANDY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, TRUE_REAL_CANDY_KEY,
            EntityType.Builder.<TrueRealCandyEntity>of(TrueRealCandyEntity::new, MobCategory.MONSTER)
                .sized(0.7f, 2.1f)
                .eyeHeight(1.85f)
                .clientTrackingRange(96)
                .updateInterval(2)
                .build(TRUE_REAL_CANDY_KEY));

        ARKADAS = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ARKADAS_KEY,
            EntityType.Builder.<com.photonpulse.candy.arkadas.ArkadasEntity>of(
                    com.photonpulse.candy.arkadas.ArkadasEntity::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f)
                .eyeHeight(1.62f)
                .clientTrackingRange(96)
                .updateInterval(2)
                .build(ARKADAS_KEY));

        // Nitelikler kayittan SONRA: NitelikKayit NeoForge'da bunlari kendi
        // olayina kadar bekletiyor, yani burada tip nesnesinin var olmasi yeter.
        NitelikKayit.register(DOPPEL, CandyDoppelEntity.createDoppelAttributes());
        NitelikKayit.register(TRUE_REAL_CANDY, TrueRealCandyEntity.createBossAttributes());
        NitelikKayit.register(ARKADAS,
            com.photonpulse.candy.arkadas.ArkadasEntity.createArkadasAttributes());
        Candy.LOG.info(
            "[Candy] Entity type registered: candy:doppel, candy:true_real_candy, candy:arkadas");
    }
}
