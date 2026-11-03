package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * AnomalyPatches (v3.0 Phase 3.5) — Overworld'de %10 chunk anomaly.
 *
 * Her 30 sn player etrafındaki yeni chunkları tara. %10 ihtimalle bir anomaly bırak:
 *  1. Çöl ↔ Buzul yamağı (sıcak/soğuk blok karışımı)
 *  2. Nether soulsand patch
 *  3. End texture patch (endstone/purpur)
 *  4. Y-inverted patch (üstte bedrock)
 *
 * İşaretlenen chunk'lar Set'te tutulur — tekrar dokunulmaz.
 */
public class AnomalyPatches {

    private static final int TICK_ARALIGI = 600; // 30 sn
    private static final int TARAMA_YARICAP = 4; // 4 chunk yarıçap
    private static final double IHTIMAL = 0.10;

    private static final Random RNG = new Random();
    private static final Set<Long> islenenChunklar = new HashSet<>();

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(AnomalyPatches::onTick);
        Candy.LOG.info("[Candy] AnomalyPatches aktif — %10 chunk anomaly (v3.0)");
    }

    private static void onTick(MinecraftServer server) {
        if (server.getTickCount() % TICK_ARALIGI != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel world = (ServerLevel) player.level();
            if (!world.dimension().equals(Level.OVERWORLD)) continue;

            int pcx = player.chunkPosition().x;
            int pcz = player.chunkPosition().z;

            for (int cx = pcx - TARAMA_YARICAP; cx <= pcx + TARAMA_YARICAP; cx++) {
                for (int cz = pcz - TARAMA_YARICAP; cz <= pcz + TARAMA_YARICAP; cz++) {
                    long key = ChunkPos.asLong(cx, cz);
                    if (islenenChunklar.contains(key)) continue;
                    // Yüklü değilse İŞARETLEME — sonraki taramada yüklenince değerlendirilsin
                    if (!world.hasChunk(cx, cz)) continue;
                    islenenChunklar.add(key);

                    if (RNG.nextDouble() > IHTIMAL) continue;

                    int aksiyon = RNG.nextInt(4);
                    switch (aksiyon) {
                        case 0 -> colBuzul(world, cx, cz);
                        case 1 -> netherPatch(world, cx, cz);
                        case 2 -> endPatch(world, cx, cz);
                        case 3 -> yInverted(world, cx, cz);
                    }
                    CandyBasarimlar.tetikle(player, "anomali_gordum");
                    Candy.LOG.info("[Candy] Anomaly[{}] chunk ({}, {})", aksiyon, cx, cz);
                }
            }

            // Set büyümesin — 500'ü aştıysa temizle (eski chunk'lar unutulur, kabul)
            if (islenenChunklar.size() > 500) {
                islenenChunklar.clear();
            }
        }
    }

    private static void colBuzul(ServerLevel world, int cx, int cz) {
        // Chunk merkezinde 4x4 alan: yarısı ice/snow, yarısı sand/sandstone
        int bx = cx * 16 + 8;
        int bz = cz * 16 + 8;
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                int x = bx + dx;
                int z = bz + dz;
                int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                BlockPos p = new BlockPos(x, y, z);
                if (dx < 2) {
                    world.setBlock(p, Blocks.ICE.defaultBlockState(), 3);
                    if (RNG.nextBoolean()) {
                        world.setBlock(p.above(), Blocks.SNOW.defaultBlockState(), 3);
                    }
                } else {
                    world.setBlock(p, Blocks.SAND.defaultBlockState(), 3);
                    world.setBlock(p.below(), Blocks.SANDSTONE.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void netherPatch(ServerLevel world, int cx, int cz) {
        int bx = cx * 16 + RNG.nextInt(12);
        int bz = cz * 16 + RNG.nextInt(12);
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                BlockPos p = new BlockPos(bx + dx, y, bz + dz);
                world.setBlock(p, RNG.nextBoolean()
                    ? Blocks.SOUL_SAND.defaultBlockState()
                    : Blocks.SOUL_SOIL.defaultBlockState(), 3);
                // Not: soul_fire eklenmez — anomali "sadece rahatsızlık", hasar vermemeli.
            }
        }
    }

    private static void endPatch(ServerLevel world, int cx, int cz) {
        int bx = cx * 16 + RNG.nextInt(12);
        int bz = cz * 16 + RNG.nextInt(12);
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                BlockPos p = new BlockPos(bx + dx, y, bz + dz);
                world.setBlock(p, RNG.nextBoolean()
                    ? Blocks.END_STONE.defaultBlockState()
                    : Blocks.PURPUR_BLOCK.defaultBlockState(), 3);
            }
        }
    }

    private static void yInverted(ServerLevel world, int cx, int cz) {
        // Küçük alan: normal yüzeyin 2 blok üstüne bedrock kap
        int bx = cx * 16 + RNG.nextInt(14);
        int bz = cz * 16 + RNG.nextInt(14);
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos p = new BlockPos(bx + dx, y + 2, bz + dz);
                world.setBlock(p, Blocks.BEDROCK.defaultBlockState(), 3);
            }
        }
    }

    public static void sunucuKapan() {
        islenenChunklar.clear();
    }
}
