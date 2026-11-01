package com.photonpulse.candy.evler;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.lore.YirtikSayfa;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Dikit (v1.9.8) — Candy'nin dikit dedikleri şey.
 *
 * Player'ın chunk'ı dahil 3x3 chunk alanında (9 chunk) rastgele spawn.
 * Deepslate 40-60 blok yüksek dikit + tabanda "Blood" (redstone_wire) + yırtık sayfa
 * + amethyst shard drop.
 *
 * Player yakınında dikit belirir → uzaktan görür → yaklaşır → temeli araştırır.
 */
public class Dikit {

    private static final int KONTROL_ARALIGI = 3000;  // 2.5 dk kontrol
    private static final long MIN_ARA = 6000L;         // 5 dk cooldown
    private static final Random RNG = new Random();
    private static final Set<Long> yapilanPozisyonlar = new HashSet<>();
    private static long sonYapim = -100000L;

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % KONTROL_ARALIGI != 0) return;
            long simdi = server.overworld().getGameTime();
            if (simdi - sonYapim < MIN_ARA) return;

            var players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) return;
            ServerPlayer player = players.get(RNG.nextInt(players.size()));
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) return;

            ServerLevel world = (ServerLevel) player.level();
            if (world.dimension() != Level.OVERWORLD) return;

            // v1.9.8: 3x3 chunk alanı — player chunk dahil (±1 chunk)
            int playerChunkX = player.getBlockX() >> 4;
            int playerChunkZ = player.getBlockZ() >> 4;
            int chunkX = playerChunkX + RNG.nextInt(3) - 1;
            int chunkZ = playerChunkZ + RNG.nextInt(3) - 1;
            int mx = (chunkX << 4) + RNG.nextInt(16);
            int mz = (chunkZ << 4) + RNG.nextInt(16);
            if (!world.hasChunk(chunkX, chunkZ)) return;

            int my = world.getHeight(Heightmap.Types.WORLD_SURFACE, mx, mz);
            BlockPos temel = new BlockPos(mx, my, mz);
            if (yapilanPozisyonlar.contains(temel.asLong())) return;

            try {
                insaEt(world, temel);
                yapilanPozisyonlar.add(temel.asLong());
                sonYapim = simdi;
                Candy.LOG.info("[Candy] Dikit inşa @ {}", temel);
            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] Dikit inşa hata: {}", t.getMessage());
            }
        });
        Candy.LOG.info("[Candy] Dikit generator aktif — 3x3 chunk alanı");
    }

    private static void insaEt(ServerLevel world, BlockPos temel) {
        // 40-60 blok deepslate dikit
        int yukseklik = 40 + RNG.nextInt(21);
        for (int y = 0; y < yukseklik; y++) {
            world.setBlockAndUpdate(temel.above(y), Blocks.DEEPSLATE.defaultBlockState());
        }

        // Blood — redstone_wire tabanda 6-10 tane, yüzey Y'sine snap
        int bloodSayi = 6 + RNG.nextInt(5);
        for (int i = 0; i < bloodSayi; i++) {
            int dx = RNG.nextInt(11) - 5;
            int dz = RNG.nextInt(11) - 5;
            if (dx == 0 && dz == 0) continue; // dikit taban
            // v1.9.11: snap yüzey Y'sine — eğimli arazide havada/gömülü kalmasın
            int wx = temel.getX() + dx;
            int wz = temel.getZ() + dz;
            int wy = world.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
            BlockPos p = new BlockPos(wx, wy, wz);
            if (world.getBlockState(p).isAir()
                && world.getBlockState(p.below()).isRedstoneConductor(world, p.below())) {
                world.setBlockAndUpdate(p, Blocks.REDSTONE_WIRE.defaultBlockState());
            }
        }

        // Yırtık sayfa drop — %70 ihtimal
        if (RNG.nextDouble() < 0.7) {
            ItemStack page = YirtikSayfa.olustur(RNG.nextInt(5));
            spawnItemDisi(world, temel, page);
        }

        // Amethyst shard drop (purple item) — %50 ihtimal
        if (RNG.nextDouble() < 0.5) {
            ItemStack amethyst = new ItemStack(Items.AMETHYST_SHARD, 1 + RNG.nextInt(2));
            amethyst.set(DataComponents.CUSTOM_NAME,
                Component.literal("Kırılmış Ruh").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            spawnItemDisi(world, temel, amethyst);
        }
    }

    /** Dikit'in içine değil, çevresine item drop et (dx=0,dz=0 hariç). */
    private static void spawnItemDisi(ServerLevel world, BlockPos temel, ItemStack stack) {
        int dx, dz;
        do {
            dx = RNG.nextInt(5) - 2;
            dz = RNG.nextInt(5) - 2;
        } while (dx == 0 && dz == 0);

        // Yüzey Y'sini bul, üstünde spawn et
        int wx = temel.getX() + dx;
        int wz = temel.getZ() + dz;
        int wy = world.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
        ItemEntity itemEntity = new ItemEntity(world,
            wx + 0.5, wy + 0.5, wz + 0.5, stack);
        try { itemEntity.setUnlimitedLifetime(); } catch (Throwable ignored) {}
        world.addFreshEntity(itemEntity);
    }

    public static void sunucuKapan() {
        yapilanPozisyonlar.clear();
        sonYapim = -100000L;
    }
}
