package com.photonpulse.candy.platform;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Canli varlik olaylari — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi.
 *
 * ZAMANLAMA FARKI — AFTER_DEATH: Fabric'in AFTER_DEATH'i olum mantigindan
 * SONRA atesleniyor, NeoForge'un LivingDeathEvent'i die() BASINDA ve iptal
 * edilebilir. "Olum kesin" varsayimini korumak icin dinleyici LOWEST
 * oncelikte takiliyor ve iptal edilmis olay atlaniyor; boylece bizden once
 * karar veren her mod gorulmus oluyor. Bizden SONRA takilan bir mod hala
 * iptal edebilir — o artik kacinilamaz bir fark, MULTILOADER.md'de yazili.
 *
 * AFTER_DAMAGE → LivingDamageEvent.Post, gercek bir "sonra" olayi.
 */
@EventBusSubscriber
public final class CanliOlaylari {

    public static final Olum AFTER_DEATH = new Olum();
    public static final Hasar AFTER_DAMAGE = new Hasar();

    private CanliOlaylari() {}

    public static final class Olum {
        private final List<BiConsumer<LivingEntity, DamageSource>> dinleyiciler = new CopyOnWriteArrayList<>();

        private Olum() {}

        public void register(BiConsumer<LivingEntity, DamageSource> dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        void calistir(LivingEntity varlik, DamageSource kaynak) {
            for (BiConsumer<LivingEntity, DamageSource> d : dinleyiciler) {
                d.accept(varlik, kaynak);
            }
        }
    }

    public static final class Hasar {
        private final List<BiConsumer<LivingEntity, DamageSource>> dinleyiciler = new CopyOnWriteArrayList<>();

        private Hasar() {}

        public void register(BiConsumer<LivingEntity, DamageSource> dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        void calistir(LivingEntity varlik, DamageSource kaynak) {
            for (BiConsumer<LivingEntity, DamageSource> d : dinleyiciler) {
                d.accept(varlik, kaynak);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOlum(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        AFTER_DEATH.calistir(event.getEntity(), event.getSource());
    }

    @SubscribeEvent
    public static void onHasar(LivingDamageEvent.Post event) {
        AFTER_DAMAGE.calistir(event.getEntity(), event.getSource());
    }
}
