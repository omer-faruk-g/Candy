package com.photonpulse.candy.travma;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Random;
import java.util.Set;

/**
 * BaseSabotaji (v3.0 Phase 3.9) — player uzaktayken base'ini bozar.
 *
 * Base = son uyunan bed. Her 10 dk player 50+ blok uzaksa, base'in 10 blok yarıçapında
 * 5-6 random blok sessizce silinir (drop yok). Bed dahil — respawn point invalidate olabilir.
 */
public class BaseSabotaji {

    // -Dcandy.debug=true → test için hızlı + yakın + log
    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");
    private static final int TICK_ARALIGI = DEBUG ? 400 : 12000;         // debug 20sn / normal 10 dk
    private static final double MIN_UZAKLIK_KARE = DEBUG ? (10 * 10) : (50 * 50);
    private static final int YARICAP = 10;
    private static final int MIN_BLOK = 5;
    private static final int MAX_BLOK = 6;

    private static final Random RNG = new Random();

    /** Fonksiyonel + item taşıyan bloklar — sabotajdan korunur (envanter kaybı olmasın). */
    private static final Set<Block> KORUNAN_BLOKLAR = Set.of(
        Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.BARREL,
        Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
        Blocks.BREWING_STAND, Blocks.ENCHANTING_TABLE, Blocks.ANVIL,
        Blocks.LOOM, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE,
        Blocks.SMITHING_TABLE, Blocks.STONECUTTER, Blocks.GRINDSTONE, Blocks.LECTERN,
        Blocks.REPEATER, Blocks.COMPARATOR, Blocks.REDSTONE_WIRE, Blocks.REDSTONE_TORCH,
        Blocks.BEACON, Blocks.CONDUIT,
        Blocks.HOPPER, Blocks.DISPENSER, Blocks.DROPPER, Blocks.BEEHIVE, Blocks.BEE_NEST,
        Blocks.SPAWNER, Blocks.BEDROCK, Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME
    );

    public static void register() {
        // Bed konumunu takip — player uyuduğunda güncelle
        EntitySleepEvents.START_SLEEPING.register((entity, sleepPos) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            TruePersonality tp = PersonalityManager.get(player);
            tp.baseKonum = new int[]{sleepPos.getX(), sleepPos.getY(), sleepPos.getZ()};
            tp.sonUykuTick = player.getWorld().getTime();
        });

        ServerTickEvents.END_SERVER_TICK.register(BaseSabotaji::onTick);
        Candy.LOG.info("[Candy] BaseSabotaji aktif — {} (v3.0)",
            DEBUG ? "DEBUG (20sn, 10+ blok uzakta)" : "10 dk, 50+ uzakta, 5-6 blok sessiz");
    }

    private static void onTick(MinecraftServer server) {
        if (server.getTicks() % TICK_ARALIGI != 0) return;
        long simdi = server.getOverworld().getTime();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) {
                if (DEBUG) Candy.LOG.info("[Candy] BaseSabotaj SKIP: {} kurulum tamamlanmadı",
                    player.getName().getString());
                continue;
            }

            if (tp.bossOldurulmeTick > 0) continue; // WIN 30dk sessizlik

            BlockPos base = baseKonumunuAl(tp, server);
            if (base == null) {
                if (DEBUG) Candy.LOG.info("[Candy] BaseSabotaj SKIP: {} bed konumu yok (hiç uyumadın)",
                    player.getName().getString());
                continue;
            }

            // Player base'inden yeterince uzak mı?
            double dx = player.getX() - base.getX();
            double dz = player.getZ() - base.getZ();
            if (dx * dx + dz * dz < MIN_UZAKLIK_KARE) {
                if (DEBUG) Candy.LOG.info("[Candy] BaseSabotaj SKIP: {} base'e çok yakın (uzaklık²={}, gerekli={})",
                    player.getName().getString(), (int)(dx*dx+dz*dz), (int)MIN_UZAKLIK_KARE);
                continue;
            }

            // Sabotajın gerçekleşeceği world — spawn'ın olduğu overworld
            ServerWorld baseWorld = server.getOverworld();
            if (!baseWorld.isChunkLoaded(base.getX() >> 4, base.getZ() >> 4)) {
                if (DEBUG) Candy.LOG.info("[Candy] BaseSabotaj SKIP: {} base chunk yüklü değil",
                    player.getName().getString());
                continue;
            }

            int hedefSayi = MIN_BLOK + RNG.nextInt(MAX_BLOK - MIN_BLOK + 1);
            int kirilan = 0;
            int deneme = 0;

            while (kirilan < hedefSayi && deneme < 30) {
                deneme++;
                int rx = RNG.nextInt(YARICAP * 2 + 1) - YARICAP;
                int ry = RNG.nextInt(11) - 5;
                int rz = RNG.nextInt(YARICAP * 2 + 1) - YARICAP;
                BlockPos hedef = base.add(rx, ry, rz);
                BlockState state = baseWorld.getBlockState(hedef);

                if (state.isAir()) continue;
                if (KORUNAN_BLOKLAR.contains(state.getBlock())) continue;
                if (state.isIn(BlockTags.SIGNS) || state.isIn(BlockTags.WALL_SIGNS)) continue;
                if (state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.TRAPDOORS)) continue;
                if (state.isIn(BlockTags.SHULKER_BOXES)) continue;
                // Item taşıyan block-entity (chest/furnace/hopper vb.) — item kaybı olmasın.
                // Bed'in de block-entity'si var ama Inventory DEĞİL → bed kırılabilir kalır
                // (spec: "bed dahil, respawn point invalidate olabilir").
                if (baseWorld.getBlockEntity(hedef) instanceof net.minecraft.inventory.Inventory) continue;
                if (state.getBlock().getHardness() < 0) continue; // bedrock vs

                // Sessiz kaldır, drop yok
                baseWorld.setBlockState(hedef, Blocks.AIR.getDefaultState(), 3);
                kirilan++;
            }

            if (kirilan > 0) {
                tp.sonBaseSabotaj = simdi;
                Candy.LOG.info("[Candy] BaseSabotaji: {} base @{} — {} blok sessizce kayboldu",
                    player.getName().getString(), base, kirilan);

                // Achievement — bed'i kırık dön kontrolü sonraki uyanma anında yapılır.
                // Şimdilik sabotaj gerçekleşti diye evim_yok tetiklenmez.
                // Player base'e dönüp bed'in yok olduğunu görürse (bed protected değil,
                // sabotajda gidebilir) — o zaman tetiklenmesi lazım. Basitleştirme:
                // sabotaj sırasında bed konumu blok değilse achievement tetiklenir.
                BlockState bedYeri = baseWorld.getBlockState(base);
                if (!bedYeri.isIn(BlockTags.BEDS)) {
                    CandyBasarimlar.tetikle(player, "evim_yok");
                }
            }
        }
    }

    /**
     * tp.baseKonum → BlockPos. Sadece gerçek uyunan bed varsa döner.
     * World spawn fallback KULLANILMAZ — player hiç uyumadıysa sabotaj yapılmaz
     * (rastgele spawn bloklarını yok etmek + false evim_yok engellenir).
     */
    private static BlockPos baseKonumunuAl(TruePersonality tp, MinecraftServer server) {
        if (tp.baseKonum != null && tp.baseKonum.length == 3) {
            return new BlockPos(tp.baseKonum[0], tp.baseKonum[1], tp.baseKonum[2]);
        }
        return null;
    }
}
