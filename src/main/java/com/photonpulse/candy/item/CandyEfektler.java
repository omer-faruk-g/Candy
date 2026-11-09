package com.photonpulse.candy.item;

import com.photonpulse.candy.Candy;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * CandyEfektler — modun özel status effect'leri.
 *
 * İmanın Gücü: gerçek kayıtlı bir efekt (HUD'da tek "İmanın Gücü" ikonu). Mekanik buff'lar
 * (Kuvvet V, Hız I, Emme IV, Yenilenme III) {@link KuranSistemi}'nde bu efektle AYNI süre
 * boyunca gizli (showIcon=false) vanilla efektlerle sağlanır — böylece HUD'da tek ikon görünür.
 */
public class CandyEfektler {

    public static Holder<MobEffect> IMANIN_GUCU;

    /** İmanın Gücü — nimet kategorisi, altın renk. Kendisi işaretleyici; buff'lar dışarıdan. */
    private static final class ImaninGucuEfekti extends MobEffect {
        private ImaninGucuEfekti() {
            super(MobEffectCategory.BENEFICIAL, 0xFFD700); // altın
        }
    }

    public static void register() {
        IMANIN_GUCU = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath(Candy.MOD_ID, "imanin_gucu"), new ImaninGucuEfekti());
        Candy.LOG.info("[Candy] İmanın Gücü efekti kayıtlı");
    }
}
