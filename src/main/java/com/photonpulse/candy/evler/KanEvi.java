package com.photonpulse.candy.evler;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * KanEvi (v1.9.7) — Candy'nin dünyaya bıraktığı gizemli ev.
 *
 * Rastgele bir player'ın 200-500 blok uzağında spawn olur (30+ dk cooldown).
 * 7x7 taban, 4 kat duvar, piramit çatı. İçinde:
 *   - Su kolonu (merkez, akan)
 *   - Blue bed (köşede)
 *   - Chest — içinde "Blood" adlı redstone (renamed via CUSTOM_NAME component)
 *   - Cobweb'ler duvarlarda
 * Dışında:
 *   - Nether brick piramit çatı
 *   - Dark oak log köşeler
 *   - Oak plank duvarlar
 *   - Warped door (cyan)
 *
 * Player bulursa: "Bu ev neden burada? Kim yaptı? Blood nedir?"
 */
public class KanEvi {

    private static final int KONTROL_ARALIGI = 6000;   // 5 dk kontrol
    private static final long MIN_ARA = 12000L;         // 10 dk minimum yapı arası
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

            // v1.9.8: player chunk'ının etrafındaki 15 chunk radius içinde random
            int playerChunkX = player.getBlockX() >> 4;
            int playerChunkZ = player.getBlockZ() >> 4;
            int chunkX = playerChunkX + RNG.nextInt(31) - 15; // ±15
            int chunkZ = playerChunkZ + RNG.nextInt(31) - 15;
            int mx = (chunkX << 4) + RNG.nextInt(16);
            int mz = (chunkZ << 4) + RNG.nextInt(16);
            if (!world.isChunkLoaded(chunkX, chunkZ)) return;

            int my = world.getTopY(Heightmap.Type.WORLD_SURFACE, mx, mz);
            BlockPos temel = new BlockPos(mx, my, mz);
            if (!alanUygun(world, temel)) return;
            if (yapilanPozisyonlar.contains(temel.asLong())) return;

            try {
                insaEt(world, temel);
                yapilanPozisyonlar.add(temel.asLong());
                sonYapim = simdi;
                Candy.LOG.info("[Candy] Kan Evi inşa @ {}", temel);
            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] Kan Evi inşa hata: {}", t.getMessage());
            }
        });
        Candy.LOG.info("[Candy] KanEvi aktif — gizemli ev generator");
    }

    private static boolean alanUygun(ServerWorld world, BlockPos temel) {
        int base = temel.getY();
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                int y = world.getTopY(Heightmap.Type.WORLD_SURFACE,
                    temel.getX() + dx, temel.getZ() + dz);
                if (Math.abs(y - base) > 2) return false;
            }
        }
        return true;
    }

    private static void insaEt(ServerWorld world, BlockPos temel) {
        // Zemin Y=0 — pale oak planks (screenshotta açık renk)
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                world.setBlockState(temel.add(dx, 0, dz),
                    Blocks.OAK_PLANKS.getDefaultState());
            }
        }

        // Duvarlar Y=1..3 — oak plank + köşede dark oak log
        for (int y = 1; y <= 3; y++) {
            for (int dx = 0; dx < 7; dx++) {
                for (int dz = 0; dz < 7; dz++) {
                    boolean isEdge = (dx == 0 || dx == 6 || dz == 0 || dz == 6);
                    if (!isEdge) continue;
                    boolean isCorner = (dx == 0 || dx == 6) && (dz == 0 || dz == 6);
                    world.setBlockState(temel.add(dx, y, dz),
                        isCorner ? Blocks.DARK_OAK_LOG.getDefaultState()
                                 : Blocks.OAK_PLANKS.getDefaultState());
                }
            }
        }

        // Tavan Y=4 — dark oak log solid
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                world.setBlockState(temel.add(dx, 4, dz),
                    Blocks.DARK_OAK_LOG.getDefaultState());
            }
        }

        // Piramit çatı Y=5..7 — nether brick
        for (int i = 0; i < 3; i++) {
            int inset = i + 1;
            int size = 7 - 2 * inset;
            if (size < 1) break;
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    world.setBlockState(temel.add(inset + dx, 5 + i, inset + dz),
                        Blocks.NETHER_BRICKS.getDefaultState());
                }
            }
        }

        // Kapı — front center (dx=3, dz=0), y=1&2. Warped door.
        BlockPos doorPos = temel.add(3, 1, 0);
        world.setBlockState(doorPos, Blocks.WARPED_DOOR.getDefaultState()
            .with(DoorBlock.HALF, DoubleBlockHalf.LOWER)
            .with(DoorBlock.HINGE, DoorHinge.LEFT)
            .with(DoorBlock.FACING, Direction.SOUTH));
        world.setBlockState(doorPos.up(), Blocks.WARPED_DOOR.getDefaultState()
            .with(DoorBlock.HALF, DoubleBlockHalf.UPPER)
            .with(DoorBlock.HINGE, DoorHinge.LEFT)
            .with(DoorBlock.FACING, Direction.SOUTH));

        // Su kolonu — merkez (dx=3, dz=3) Y=1,2,3
        for (int y = 1; y <= 3; y++) {
            world.setBlockState(temel.add(3, y, 3), Blocks.WATER.getDefaultState());
        }

        // Blue bed (dx=1, dz=1) foot → (dx=2, dz=1) head, facing east
        world.setBlockState(temel.add(1, 1, 1), Blocks.BLUE_BED.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT)
            .with(BedBlock.FACING, Direction.EAST));
        world.setBlockState(temel.add(2, 1, 1), Blocks.BLUE_BED.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD)
            .with(BedBlock.FACING, Direction.EAST));

        // Chest (dx=5, dz=1) + Blood-renamed redstone
        BlockPos chestPos = temel.add(5, 1, 1);
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
        var be = world.getBlockEntity(chestPos);
        if (be instanceof ChestBlockEntity chest) {
            for (int i = 0; i < 20; i++) {
                int slot = RNG.nextInt(chest.size());
                if (!chest.getStack(slot).isEmpty()) continue;
                ItemStack stack = new ItemStack(Items.REDSTONE, 1 + RNG.nextInt(5));
                stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Blood").formatted(Formatting.DARK_RED, Formatting.BOLD));
                chest.setStack(slot, stack);
            }
            chest.markDirty();
        }

        // Cobweb'ler — duvarlar iç yüzey Y=2 üzerine 6 tane random
        for (int i = 0; i < 6; i++) {
            int side = RNG.nextInt(4);
            int pos = 1 + RNG.nextInt(5);
            BlockPos webPos = switch (side) {
                case 0 -> temel.add(pos, 2, 1);          // North iç
                case 1 -> temel.add(pos, 2, 5);          // South iç
                case 2 -> temel.add(1, 2, pos);          // West iç
                default -> temel.add(5, 2, pos);          // East iç
            };
            if (world.getBlockState(webPos).isAir()) {
                world.setBlockState(webPos, Blocks.COBWEB.getDefaultState());
            }
        }
    }

    public static void sunucuKapan() {
        yapilanPozisyonlar.clear();
        sonYapim = -100000L;
    }
}
