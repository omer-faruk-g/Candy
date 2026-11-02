package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.BiConsumer;

/**
 * Canli varlik olaylari — Fabric uygulamasi.
 *
 * AFTER_DAMAGE yalnizca varlik ve hasar kaynagini tasiyor. Fabric'in verdigi
 * uc sayisal parametre (baseDamageTaken, damageTaken, blocked) kasten
 * dusuruldu: tek cagri yeri onlari kullanmiyor ve NeoForge tarafinda birebir
 * karsiliklari yok (blocked orada boolean degil float). Gerekce: MULTILOADER.md.
 */
public final class CanliOlaylari {

    public static final Olum AFTER_DEATH = new Olum();
    public static final Hasar AFTER_DAMAGE = new Hasar();

    private CanliOlaylari() {}

    public static final class Olum {
        private Olum() {}

        public void register(BiConsumer<LivingEntity, DamageSource> dinleyici) {
            ServerLivingEntityEvents.AFTER_DEATH.register(dinleyici::accept);
        }
    }

    public static final class Hasar {
        private Hasar() {}

        public void register(BiConsumer<LivingEntity, DamageSource> dinleyici) {
            ServerLivingEntityEvents.AFTER_DAMAGE.register(
                (entity, source, baseDamageTaken, damageTaken, blocked) ->
                    dinleyici.accept(entity, source));
        }
    }
}
