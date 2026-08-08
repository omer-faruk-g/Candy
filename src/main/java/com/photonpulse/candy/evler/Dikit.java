package com.photonpulse.candy.evler;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.lore.YirtikSayfa;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

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
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % KONTROL_ARALIGI != 0) return;
            long simdi = server.getOverworld().getTime();
            if (simdi - sonYapim < MIN_ARA) return;

            var players = server.getPlayerManager().getPlayerList();
            if (players.isEmpty()) return;
            ServerPlayerEntity player = players.get(RNG.nextInt(players.size()));
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) return;

            ServerWorld world = (ServerWorld) player.getWorld();
            if (world.getRegistryKey() != World.OVERWORLD) return;

            // v1.9.8: 3x3 chunk alanı — player chunk dahil (±1 chunk)
            int playerChunkX = player.getBlockX() >> 4;
            int playerChunkZ = player.getBlockZ() >> 4;
            int chunkX = playerChunkX + RNG.nextInt(3) - 1;
            int chunkZ = playerChunkZ + RNG.nextInt(3) - 1;
            int mx = (chunkX << 4) + RNG.nextInt(16);
            int mz = (chunkZ << 4) + RNG.nextInt(16);
            if (!world.isChunkLoaded(chunkX, chunkZ)) return;

            int my = world.getTopY(Heightmap.Type.WORLD_SURFACE, mx, mz);
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

    private static void insaEt(ServerWorld world, BlockPos temel) {
        // 40-60 blok deepslate dikit
        int yukseklik = 40 + RNG.nextInt(21);
        for (int y = 0; y < yukseklik; y++) {
            world.setBlockState(temel.up(y), Blocks.DEEPSLATE.getDefaultState());
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
            int wy = world.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz);
            BlockPos p = new BlockPos(wx, wy, wz);
            if (world.getBlockState(p).isAir()
                && world.getBlockState(p.down()).isSolidBlock(world, p.down())) {
                world.setBlockState(p, Blocks.REDSTONE_WIRE.getDefaultState());
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
            amethyst.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Kırılmış Ruh").formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC));
            spawnItemDisi(world, temel, amethyst);
        }
    }

    /** Dikit'in içine değil, çevresine item drop et (dx=0,dz=0 hariç). */
    private static void spawnItemDisi(ServerWorld world, BlockPos temel, ItemStack stack) {
        int dx, dz;
        do {
            dx = RNG.nextInt(5) - 2;
            dz = RNG.nextInt(5) - 2;
        } while (dx == 0 && dz == 0);

        // Yüzey Y'sini bul, üstünde spawn et
        int wx = temel.getX() + dx;
        int wz = temel.getZ() + dz;
        int wy = world.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz);
        ItemEntity itemEntity = new ItemEntity(world,
            wx + 0.5, wy + 0.5, wz + 0.5, stack);
        try { itemEntity.setNeverDespawn(); } catch (Throwable ignored) {}
        world.spawnEntity(itemEntity);
    }

    public static void sunucuKapan() {
        yapilanPozisyonlar.clear();
        sonYapim = -100000L;
    }
}
