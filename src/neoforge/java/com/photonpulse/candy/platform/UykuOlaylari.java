package com.photonpulse.candy.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Uyku kancalari — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi, ayni imza. Iki kanal KASTEN ayri
 * yollarla cozuldu, cunku NeoForge'un uyku olaylari simetrik degil:
 *
 * UYANDI → PlayerWakeUpEvent, gercek karsilik. Olay sleepPos tasimiyor, ama
 *   TASIMASI gerekmiyor: NeoForge olayi Player.stopSleepInBed icinde
 *   super.stopSleeping() cagrisindan HEMEN ONCE atiyor, yani getSleepingPos()
 *   henuz temizlenmemis ve oyuncu hala yatakta. Fabric de ayni yerde duruyor
 *   (LivingEntityMixin, stopSleeping HEAD'inde, getSleepingPos() okuyarak).
 *   Iki tarafta da ayni an, ayni veri.
 *
 * UYKUYA_GIRDI → kenar tespiti (her tick isSleeping() gecisi).
 *   CanPlayerSleepEvent KULLANILMADI: o olay uyku *denemesi* aninda ve veto
 *   edilebilir bicimde atesleniyor. Baska bir mod (ya da vanilla kurali)
 *   uykuyu reddederse, olmamis bir uyku icin dinleyici tetiklenirdi — sessiz
 *   bir yanlis davranis, derleme hatasi vermeyen turden. Kenar tespitinin
 *   maliyeti en fazla 1 tick gecikme; iki cagri yeri icin onemsiz.
 *   Fabric START_SLEEPING'i LivingEntity.sleep RETURN'unde atiyor, yani orada
 *   da uyku konumu yazilmis durumda — ayni veri, en fazla bir tick sonra.
 *
 * Gerekce ve dogrulama dokumu: MULTILOADER.md.
 */
@EventBusSubscriber
public final class UykuOlaylari {

    public static final Giris UYKUYA_GIRDI = new Giris();
    public static final Cikis UYANDI = new Cikis();

    /** Gecen tick'te uyurken gorulen oyuncular — kenar tespitinin onceki durumu. */
    private static final Set<UUID> uyuyanlar = ConcurrentHashMap.newKeySet();

    private UykuOlaylari() {}

    public static final class Giris {
        private final List<BiConsumer<LivingEntity, BlockPos>> dinleyiciler = new CopyOnWriteArrayList<>();

        private Giris() {}

        public void register(BiConsumer<LivingEntity, BlockPos> dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        boolean bos() {
            return dinleyiciler.isEmpty();
        }

        void calistir(LivingEntity varlik, BlockPos uykuKonumu) {
            for (BiConsumer<LivingEntity, BlockPos> d : dinleyiciler) {
                d.accept(varlik, uykuKonumu);
            }
        }
    }

    public static final class Cikis {
        private final List<BiConsumer<LivingEntity, BlockPos>> dinleyiciler = new CopyOnWriteArrayList<>();

        private Cikis() {}

        public void register(BiConsumer<LivingEntity, BlockPos> dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        void calistir(LivingEntity varlik, BlockPos uykuKonumu) {
            for (BiConsumer<LivingEntity, BlockPos> d : dinleyiciler) {
                d.accept(varlik, uykuKonumu);
            }
        }
    }

    @SubscribeEvent
    public static void onSunucuTick(ServerTickEvent.Post event) {
        if (UYKUYA_GIRDI.bos()) return;

        for (ServerPlayer oyuncu : event.getServer().getPlayerList().getPlayers()) {
            UUID id = oyuncu.getUUID();
            boolean uyuyor = oyuncu.isSleeping();
            boolean onceUyuyordu = uyuyanlar.contains(id);

            if (uyuyor && !onceUyuyordu) {
                uyuyanlar.add(id);
                // isSleeping() true ise getSleepingPos() dolu: ikisi de ayni
                // alandan okunuyor (isSleeping() == getSleepingPos().isPresent()).
                // orElse yalnizca o degismezlik bozulursa devreye giriyor;
                // oyuncu o an yatakta oldugu icin kendi konumu makul yedek.
                BlockPos konum = oyuncu.getSleepingPos().orElseGet(oyuncu::blockPosition);
                UYKUYA_GIRDI.calistir(oyuncu, konum);
            } else if (!uyuyor && onceUyuyordu) {
                uyuyanlar.remove(id);
            }
        }
    }

    /**
     * Uyurken cikan oyuncu kenar tespitinde "hala uyuyor" olarak kalirdi; bir
     * sonraki uykusunda gecis yakalanmaz ve olay HIC ateslenmezdi. Cikista
     * durumu temizliyoruz.
     */
    @SubscribeEvent
    public static void onCikis(PlayerEvent.PlayerLoggedOutEvent event) {
        uyuyanlar.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onUyanma(PlayerWakeUpEvent event) {
        LivingEntity varlik = event.getEntity();
        // Kenar tespiti bu gecisi bir sonraki tick'te de gorurdu; durumu burada
        // temizleyerek yalnizca gercek olayin konusmasini sagliyoruz.
        uyuyanlar.remove(varlik.getUUID());

        // Olay super.stopSleeping()'den once atildigi icin konum hala duruyor.
        // Fabric de ayni ani yakaliyor ve ayni alani okuyor.
        varlik.getSleepingPos().ifPresent(konum -> UYANDI.calistir(varlik, konum));
    }
}
