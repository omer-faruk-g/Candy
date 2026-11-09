package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.BiConsumer;

/**
 * Uyku kancalari — Fabric uygulamasi.
 *
 * Iki kanal da Fabric'in EntitySleepEvents imzasiyla birebir ayni
 * (varlik + uyku konumu), boylece cagri yerlerinde yalnizca import satiri
 * degisiyor. NeoForge tarafi ayni iki degeri veriyor ama iki kanali AYRI
 * yollarla cozuyor; gerekce ve dogrulama dokumu MULTILOADER.md'de.
 */
public final class UykuOlaylari {

    public static final Giris UYKUYA_GIRDI = new Giris();
    public static final Cikis UYANDI = new Cikis();

    private UykuOlaylari() {}

    public static final class Giris {
        private Giris() {}

        public void register(BiConsumer<LivingEntity, BlockPos> dinleyici) {
            EntitySleepEvents.START_SLEEPING.register(dinleyici::accept);
        }
    }

    public static final class Cikis {
        private Cikis() {}

        public void register(BiConsumer<LivingEntity, BlockPos> dinleyici) {
            EntitySleepEvents.STOP_SLEEPING.register(dinleyici::accept);
        }
    }
}
