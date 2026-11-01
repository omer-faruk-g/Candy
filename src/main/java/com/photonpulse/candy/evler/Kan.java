package com.photonpulse.candy.evler;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

            // v1.9.9: 2x2 chunk alanı — player chunk + 3 komşu (toplam 4 chunk)
            int playerChunkX = player.getBlockX() >> 4;
            int playerChunkZ = player.getBlockZ() >> 4;
            int chunkX = playerChunkX + RNG.nextInt(2); // 0 veya +1
            int chunkZ = playerChunkZ + RNG.nextInt(2);
            int mx = (chunkX << 4) + RNG.nextInt(16);
            int mz = (chunkZ << 4) + RNG.nextInt(16);
            if (!world.hasChunk(chunkX, chunkZ)) return;

            int my = world.getHeight(Heightmap.Types.WORLD_SURFACE, mx, mz);
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

    private static void insaEt(ServerLevel world, BlockPos merkez) {
        // 10-15 REDSTONE_WIRE saçılmış, merkezin 5 blok radius'unda
        int sayi = 10 + RNG.nextInt(6);
        int konulan = 0;
        for (int i = 0; i < sayi * 3 && konulan < sayi; i++) {
            int dx = RNG.nextInt(11) - 5;
            int dz = RNG.nextInt(11) - 5;
            BlockPos p = merkez.offset(dx, 0, dz);
            // Yüzey Y'sine snap et
            int y = world.getHeight(Heightmap.Types.WORLD_SURFACE,
                p.getX(), p.getZ());
            BlockPos yuzey = new BlockPos(p.getX(), y, p.getZ());
            if (world.getBlockState(yuzey).isAir()
                && world.getBlockState(yuzey.below()).isRedstoneConductor(world, yuzey.below())) {
                world.setBlockAndUpdate(yuzey, Blocks.REDSTONE_WIRE.defaultBlockState());
                konulan++;
            }
        }

        // 1-2 REDSTONE item drop, adı "Kan"
        int itemSayi = 1 + RNG.nextInt(2);
        for (int i = 0; i < itemSayi; i++) {
            ItemStack kan = new ItemStack(Items.REDSTONE, 1 + RNG.nextInt(3));
            kan.set(DataComponents.CUSTOM_NAME,
                Component.literal("Kan").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            int dx = RNG.nextInt(5) - 2;
            int dz = RNG.nextInt(5) - 2;
            ItemEntity itemEntity = new ItemEntity(world,
                merkez.getX() + dx + 0.5,
                merkez.getY() + 0.5,
                merkez.getZ() + dz + 0.5,
                kan);
            try { itemEntity.setUnlimitedLifetime(); } catch (Throwable ignored) {}
            world.addFreshEntity(itemEntity);
        }
    }

    public static void sunucuKapan() {
        yapilanPozisyonlar.clear();
        sonYapim = -100000L;
    }
}
