package com.photonpulse.candy.item;

import com.photonpulse.candy.Candy;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

/**
 * CandyEfektler — modun özel status effect'leri.
 *
 * İmanın Gücü: gerçek kayıtlı bir efekt (HUD'da tek "İmanın Gücü" ikonu). Mekanik buff'lar
 * (Kuvvet V, Hız I, Emme IV, Yenilenme III) {@link KuranSistemi}'nde bu efektle AYNI süre
 * boyunca gizli (showIcon=false) vanilla efektlerle sağlanır — böylece HUD'da tek ikon görünür.
 */
public class CandyEfektler {

    public static RegistryEntry<StatusEffect> IMANIN_GUCU;

    /** İmanın Gücü — nimet kategorisi, altın renk. Kendisi işaretleyici; buff'lar dışarıdan. */
    private static final class ImaninGucuEfekti extends StatusEffect {
        private ImaninGucuEfekti() {
            super(StatusEffectCategory.BENEFICIAL, 0xFFD700); // altın
        }
    }

    public static void register() {
        IMANIN_GUCU = Registry.registerReference(Registries.STATUS_EFFECT,
            Identifier.of(Candy.MOD_ID, "imanin_gucu"), new ImaninGucuEfekti());
        Candy.LOG.info("[Candy] İmanın Gücü efekti kayıtlı");
    }
}
