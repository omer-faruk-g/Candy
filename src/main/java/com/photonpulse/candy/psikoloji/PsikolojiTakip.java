package com.photonpulse.candy.psikoloji;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
// Ozguven aynı package — import gerekmez
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * PsikolojiTakip — oyuncunun ruh sağlığını sürekli izleyen sistem.
 *
 * Kötüleştiriciler (agresivlik ↑ → Candy daha sık müdahale):
 *  - Mağarada + karanlıkta uzun süre kalma
 *  - Hostile mob'dan damage yeme
 *  - Yüksek korku skoru (>70)
 *
 * İyileştiriciler (agresivlik ↓ → Candy sakinleşir):
 *  - Elmas cevheri kazma (büyük ödül)
 *  - Çiçek koparma (küçük ödül, çevre etkileşimi)
 *  - Yüzeyde + gündüzde bulunma (yavaş iyileşme)
 *
 * DURAKLAMA: İyileşme sadece son kötüleşmeden 30 sn SONRA başlar.
 * Böylece sürekli tehdit altındayken iyileşme olmaz — sadece güvende hissedince.
 */
public class PsikolojiTakip {

    private static final int TICK_ARALIGI = 20;   // 1 saniye
    private static int tickSayaci = 0;

    // Duraklama — iyileşme öncesi minimum sakinlik süresi (tick)
    private static final long DURAKLAMA_TICK = 600L;   // 30 saniye

    // Kötüleştirici miktarlar
    private static final double MAGARA_KOTULESTIRME = 0.10;   // per sn
    private static final double KORKU_KOTULESTIRME  = 0.02;   // per sn
    private static final double MOB_DAMAGE_KOTULESTIRME = 2.0; // per hit

    // İyileştirici miktarlar
    private static final double ELMAS_IYILESTIRME  = 3.0;    // per blok
    private static final double CICEK_IYILESTIRME  = 1.0;    // per blok
    private static final double YUZEY_IYILESTIRME  = 0.05;   // per sn

    // Yüksek korku eşiği (bu üstündeyken kötüleşme uygulanır)
    private static final double YUKSEK_KORKU_ESIK = 70.0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PsikolojiTakip::onTick);

        // Mob'dan damage yeme
        ServerLivingEntityEvents.AFTER_DAMAGE.register(
            (entity, damageSource, baseDamageTaken, damageTaken, blocked) -> {
                if (!(entity instanceof ServerPlayer player)) return;
                if (!(damageSource.getEntity() instanceof Monster)) return;
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) return;
                kotulesmeUygula(tp, MOB_DAMAGE_KOTULESTIRME, player.level().getGameTime());
            });

        // Blok kırma — elmas / çiçek
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            TruePersonality tp = PersonalityManager.get(sp);
            if (!tp.kurulumTamam) return;

            Block b = state.getBlock();
            if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) {
                iyilesmeUygula(tp, ELMAS_IYILESTIRME);
                Candy.LOG.debug("[Candy] psikoloji iyileşti (elmas): {}", tp.psikolojiKotulugu);
            } else if (state.is(BlockTags.FLOWERS)) {
                iyilesmeUygula(tp, CICEK_IYILESTIRME);
                Candy.LOG.debug("[Candy] psikoloji iyileşti (çiçek): {}", tp.psikolojiKotulugu);
            }
        });

        Candy.LOG.info("[Candy] PsikolojiTakip aktif — kotuluk sürekli izleniyor");
    }

    private static void kotulesmeUygula(TruePersonality tp, double miktar, long simdi) {
        tp.psikolojiKotulugu = Math.min(100, tp.psikolojiKotulugu + miktar);
        tp.sonKotulesmeTick = simdi;
    }

    private static void iyilesmeUygula(TruePersonality tp, double miktar) {
        tp.psikolojiKotulugu = Math.max(0, tp.psikolojiKotulugu - miktar);
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI != 0) return;

        long simdi = server.overworld().getGameTime();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;

            ServerLevel world = (ServerLevel) player.level();
            var pos = player.blockPosition();

            // Cave detection: Y < seaLevel + block light düşük + üstü kapalı
            int isik = world.getBrightness(LightLayer.BLOCK, pos);
            int seaLevel = world.getSeaLevel();
            int yuzeyY = world.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
            boolean uzeriKapali = yuzeyY > player.getY() + 3;
            boolean magara = player.getY() < seaLevel && isik < 8 && uzeriKapali;

            long timeOfDay = world.getDayTime() % 24000L;
            boolean gunduz = timeOfDay < 12000L;

            // ── Kötüleştiriciler ──
            if (magara) {
                kotulesmeUygula(tp, MAGARA_KOTULESTIRME, simdi);
            }
            if (tp.korkuSkoru > YUKSEK_KORKU_ESIK) {
                kotulesmeUygula(tp, KORKU_KOTULESTIRME, simdi);
            }

            // ── İyileştirici (sadece duraklama sonrası) ──
            long duraklamaFark = simdi - tp.sonKotulesmeTick;
            if (duraklamaFark > DURAKLAMA_TICK && !magara && gunduz) {
                iyilesmeUygula(tp, YUZEY_IYILESTIRME);
                // Sakin oyuncu → Candy'nin özgüveni de azalır
                Ozguven.azalt(tp, 0.03);
            }
        }
    }
}
