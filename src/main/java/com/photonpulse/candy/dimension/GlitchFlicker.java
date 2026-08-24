package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * GlitchFlicker (v3.0 Faz 1.1) — Mirror'da blok flicker.
 *
 * Her 40 tick (2 sn) player'ın 12 blok yakınında random 1-3 blok seç ve
 * "yanlış" bir blokla değiştir. 1 sn sonra orijinal blok geri yüklenir.
 *
 * Bu, glitch mixin gerektirmez — server-side blok state manipülasyonu ile
 * "bu blok bir anlığına yanlış texture" hissi verir.
 */
public class GlitchFlicker {

    // Faz 1.2 aggressive tuning: daha sık + daha fazla blok + biraz daha uzun
    private static final int TICK_ARALIGI = 20;      // 1 sn (was 2 sn)
    private static final int GERI_YUKLE_TICK = 30;   // 1.5 sn sonra restore (was 1 sn)
    private static final int YARICAP = 14;           // was 12
    private static final int MIN_BLOK = 3;           // was 1
    private static final int MAX_BLOK = 7;           // was 3
    private static final Random RNG = new Random();

    // Restore kuyruğu: pos.asLong → [worldKey, originalStateSerialized, restoreTick]
    private record Yedek(String worldKey, BlockPos pos, BlockState orijinal, long restoreTick) {}
    private static final Map<Long, Yedek> yedekler = new HashMap<>();

    // Yanlış texture için block havuzu
    private static final Block[] YANLIS_BLOKLAR = new Block[]{
        Blocks.CRYING_OBSIDIAN, Blocks.MAGMA_BLOCK, Blocks.SOUL_SAND,
        Blocks.END_STONE, Blocks.PURPUR_BLOCK, Blocks.NETHERRACK,
        Blocks.WARPED_NYLIUM, Blocks.CRIMSON_NYLIUM, Blocks.BONE_BLOCK,
        Blocks.SCULK, Blocks.MOSS_BLOCK, Blocks.PACKED_ICE
    };

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GlitchFlicker::onTick);
        Candy.LOG.info("[Candy] GlitchFlicker aktif — Mirror'da blok flicker (v3.0 Faz 1.1)");
    }

    private static void onTick(MinecraftServer server) {
        long simdi = server.getOverworld().getTime();

        // Restore kuyruğu
        var iter = yedekler.entrySet().iterator();
        while (iter.hasNext()) {
            var e = iter.next();
            if (simdi < e.getValue().restoreTick) continue;
            for (ServerWorld w : server.getWorlds()) {
                if (!w.getRegistryKey().getValue().toString().equals(e.getValue().worldKey)) continue;
                BlockPos p = e.getValue().pos;
                // Orijinal blok geri koy (sadece bizim yerleştirdiğimiz glitch değişmediyse)
                w.setBlockState(p, e.getValue().orijinal, 3);
                break;
            }
            iter.remove();
        }

        if (server.getTicks() % TICK_ARALIGI != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            var worldKey = player.getWorld().getRegistryKey();
            if (!worldKey.equals(CandyDimensions.MIRROR)) continue;

            ServerWorld world = (ServerWorld) player.getWorld();
            int adet = MIN_BLOK + RNG.nextInt(MAX_BLOK - MIN_BLOK + 1);
            for (int i = 0; i < adet; i++) {
                int dx = RNG.nextInt(YARICAP * 2 + 1) - YARICAP;
                int dy = RNG.nextInt(9) - 4;
                int dz = RNG.nextInt(YARICAP * 2 + 1) - YARICAP;
                BlockPos hedef = player.getBlockPos().add(dx, dy, dz);
                BlockState orijinal = world.getBlockState(hedef);

                if (orijinal.isAir()) continue;
                if (orijinal.getBlock().getHardness() < 0) continue; // bedrock
                if (yedekler.containsKey(hedef.asLong())) continue;
                // Block-entity (chest/furnace/sign vb.) glitch'lenirse içerik/veri kaybolur — atla
                if (world.getBlockEntity(hedef) != null) continue;

                Block yanlis = YANLIS_BLOKLAR[RNG.nextInt(YANLIS_BLOKLAR.length)];
                world.setBlockState(hedef, yanlis.getDefaultState(), 3);
                yedekler.put(hedef.asLong(), new Yedek(
                    worldKey.getValue().toString(),
                    hedef.toImmutable(),
                    orijinal,
                    simdi + GERI_YUKLE_TICK
                ));
            }
        }
    }

    /**
     * Sunucu kapanırken BEKLEYEN glitch bloklarını orijinaline geri yükler.
     * Aksi halde kapanış anında "flicker" halindeki 3-7 blok kalıcı olarak
     * yanlış blok olarak kalırdı (her çıkışta biriken gerçek grief).
     */
    public static void sunucuKapan(MinecraftServer server) {
        if (server != null) {
            int geri = 0;
            for (var e : yedekler.values()) {
                for (ServerWorld w : server.getWorlds()) {
                    if (!w.getRegistryKey().getValue().toString().equals(e.worldKey())) continue;
                    try {
                        w.setBlockState(e.pos(), e.orijinal(), 3);
                        geri++;
                    } catch (Throwable ignored) {}
                    break;
                }
            }
            if (geri > 0) {
                Candy.LOG.info("[Candy] GlitchFlicker: kapanışta {} blok orijinaline döndürüldü", geri);
            }
        }
        yedekler.clear();
    }

    /** Geriye dönük uyum — server referansı olmadan sadece temizler. */
    public static void sunucuKapan() {
        sunucuKapan(null);
    }
}
