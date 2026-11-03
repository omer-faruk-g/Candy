package com.photonpulse.candy.platform;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Varlik nitelik kaydi — NeoForge uygulamasi.
 *
 * Fabric'te kayit dogrudan yapilabilir; NeoForge'da nitelikler mod veri yolunda
 * atilan EntityAttributeCreationEvent icinde verilmek zorunda. O yuzden cagrilar
 * burada toplanip olay geldiginde aktariliyor — cagri yerinin sirasi degismiyor.
 *
 * Fabric'in register(EntityType, AttributeSupplier.Builder) asiri yuklemesi
 * icerde build() cagiriyor; burada ayni sey acikca yapiliyor.
 */
public final class NitelikKayit {

    private static final Map<EntityType<? extends LivingEntity>, AttributeSupplier.Builder>
        BEKLEYEN = new LinkedHashMap<>();

    private NitelikKayit() {}

    /**
     * NeoForgeGiris mod veri yolunu buraya verir.
     *
     * Anotasyon yerine acik baglama: yanlis veri yoluna giden abonelik sessiz
     * no-op olur ve nitelikler hic kaydedilmez. Bu, ana menude gorunmez --
     * ancak bir varlik dogdugunda ortaya cikar. KayitKapisi ile ayni yaklasim.
     */
    public static void baglan(IEventBus modVeriYolu) {
        modVeriYolu.addListener(NitelikKayit::onNitelikOlusturma);
    }

    public static void register(EntityType<? extends LivingEntity> tip,
                                AttributeSupplier.Builder nitelikler) {
        synchronized (BEKLEYEN) {
            BEKLEYEN.put(tip, nitelikler);
        }
    }

    private static void onNitelikOlusturma(EntityAttributeCreationEvent event) {
        synchronized (BEKLEYEN) {
            BEKLEYEN.forEach((tip, nitelikler) -> event.put(tip, nitelikler.build()));
        }
    }
}
