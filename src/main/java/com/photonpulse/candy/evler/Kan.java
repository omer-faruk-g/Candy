package com.photonpulse.candy.evler;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
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
 * Kan (v1.9.9) — sadece kan izi. Küçük yapı.
 *
 * Player'ın 2x2 chunk alanında (4 chunk toplam — player chunk + 3 komşu) rastgele
 * bir merkez seçilir. Merkezin 4-5 blok çevresine 10-15 REDSTONE_WIRE bloğu saçılır.
 * Ayrıca 1-2 REDSTONE item entity drop olur, adı "Kan" (koyu kırmızı).
 *
 * Player yürürken karşısında bir anda kan izi bulur. Kimin? Ne için?
 */
public class Kan {

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

            // v1.9.9: 2x2 chunk alanı — player chunk + 3 komşu (toplam 4 chunk)
            int playerChunkX = player.getBlockX() >> 4;
            int playerChunkZ = player.getBlockZ() >> 4;
            int chunkX = playerChunkX + RNG.nextInt(2); // 0 veya +1
            int chunkZ = playerChunkZ + RNG.nextInt(2);
            int mx = (chunkX << 4) + RNG.nextInt(16);
            int mz = (chunkZ << 4) + RNG.nextInt(16);
            if (!world.isChunkLoaded(chunkX, chunkZ)) return;

            int my = world.getTopY(Heightmap.Type.WORLD_SURFACE, mx, mz);
            BlockPos merkez = new BlockPos(mx, my, mz);
            if (yapilanPozisyonlar.contains(merkez.asLong())) return;

            try {
                insaEt(world, merkez);
                yapilanPozisyonlar.add(merkez.asLong());
                sonYapim = simdi;
                Candy.LOG.info("[Candy] Kan izi @ {}", merkez);
            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] Kan izi hata: {}", t.getMessage());
            }
        });
        Candy.LOG.info("[Candy] Kan generator aktif — 2x2 chunk alanı, redstone kan izi");
    }

    private static void insaEt(ServerWorld world, BlockPos merkez) {
        // 10-15 REDSTONE_WIRE saçılmış, merkezin 5 blok radius'unda
        int sayi = 10 + RNG.nextInt(6);
        int konulan = 0;
        for (int i = 0; i < sayi * 3 && konulan < sayi; i++) {
            int dx = RNG.nextInt(11) - 5;
            int dz = RNG.nextInt(11) - 5;
            BlockPos p = merkez.add(dx, 0, dz);
            // Yüzey Y'sine snap et
            int y = world.getTopY(Heightmap.Type.WORLD_SURFACE,
                p.getX(), p.getZ());
            BlockPos yuzey = new BlockPos(p.getX(), y, p.getZ());
            if (world.getBlockState(yuzey).isAir()
                && world.getBlockState(yuzey.down()).isSolidBlock(world, yuzey.down())) {
                world.setBlockState(yuzey, Blocks.REDSTONE_WIRE.getDefaultState());
                konulan++;
            }
        }

        // 1-2 REDSTONE item drop, adı "Kan"
        int itemSayi = 1 + RNG.nextInt(2);
        for (int i = 0; i < itemSayi; i++) {
            ItemStack kan = new ItemStack(Items.REDSTONE, 1 + RNG.nextInt(3));
            kan.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Kan").formatted(Formatting.DARK_RED, Formatting.BOLD));
            int dx = RNG.nextInt(5) - 2;
            int dz = RNG.nextInt(5) - 2;
            ItemEntity itemEntity = new ItemEntity(world,
                merkez.getX() + dx + 0.5,
                merkez.getY() + 0.5,
                merkez.getZ() + dz + 0.5,
                kan);
            try { itemEntity.setNeverDespawn(); } catch (Throwable ignored) {}
            world.spawnEntity(itemEntity);
        }
    }

    public static void sunucuKapan() {
        yapilanPozisyonlar.clear();
        sonYapim = -100000L;
    }
}
