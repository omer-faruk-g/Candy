package com.photonpulse.candy.evler;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
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

            // v1.9.8: player chunk'ının etrafındaki 15 chunk radius içinde random
            int playerChunkX = player.getBlockX() >> 4;
            int playerChunkZ = player.getBlockZ() >> 4;
            int chunkX = playerChunkX + RNG.nextInt(31) - 15; // ±15
            int chunkZ = playerChunkZ + RNG.nextInt(31) - 15;
            int mx = (chunkX << 4) + RNG.nextInt(16);
            int mz = (chunkZ << 4) + RNG.nextInt(16);
            if (!world.hasChunk(chunkX, chunkZ)) return;

            int my = world.getHeight(Heightmap.Types.WORLD_SURFACE, mx, mz);
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

    private static boolean alanUygun(ServerLevel world, BlockPos temel) {
        int base = temel.getY();
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                int y = world.getHeight(Heightmap.Types.WORLD_SURFACE,
                    temel.getX() + dx, temel.getZ() + dz);
                if (Math.abs(y - base) > 2) return false;
            }
        }
        return true;
    }

    private static void insaEt(ServerLevel world, BlockPos temel) {
        // Zemin Y=0 — pale oak planks (screenshotta açık renk)
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                world.setBlockAndUpdate(temel.offset(dx, 0, dz),
                    Blocks.OAK_PLANKS.defaultBlockState());
            }
        }

        // Duvarlar Y=1..3 — oak plank + köşede dark oak log
        for (int y = 1; y <= 3; y++) {
            for (int dx = 0; dx < 7; dx++) {
                for (int dz = 0; dz < 7; dz++) {
                    boolean isEdge = (dx == 0 || dx == 6 || dz == 0 || dz == 6);
                    if (!isEdge) continue;
                    boolean isCorner = (dx == 0 || dx == 6) && (dz == 0 || dz == 6);
                    world.setBlockAndUpdate(temel.offset(dx, y, dz),
                        isCorner ? Blocks.DARK_OAK_LOG.defaultBlockState()
                                 : Blocks.OAK_PLANKS.defaultBlockState());
                }
            }
        }

        // Tavan Y=4 — dark oak log solid
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                world.setBlockAndUpdate(temel.offset(dx, 4, dz),
                    Blocks.DARK_OAK_LOG.defaultBlockState());
            }
        }

        // Piramit çatı Y=5..7 — nether brick
        for (int i = 0; i < 3; i++) {
            int inset = i + 1;
            int size = 7 - 2 * inset;
            if (size < 1) break;
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    world.setBlockAndUpdate(temel.offset(inset + dx, 5 + i, inset + dz),
                        Blocks.NETHER_BRICKS.defaultBlockState());
                }
            }
        }

        // Kapı — front center (dx=3, dz=0), y=1&2. Warped door.
        BlockPos doorPos = temel.offset(3, 1, 0);
        world.setBlockAndUpdate(doorPos, Blocks.WARPED_DOOR.defaultBlockState()
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
            .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
            .setValue(DoorBlock.FACING, Direction.SOUTH));
        world.setBlockAndUpdate(doorPos.above(), Blocks.WARPED_DOOR.defaultBlockState()
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
            .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
            .setValue(DoorBlock.FACING, Direction.SOUTH));

        // Su kolonu — merkez (dx=3, dz=3) Y=1,2,3
        for (int y = 1; y <= 3; y++) {
            world.setBlockAndUpdate(temel.offset(3, y, 3), Blocks.WATER.defaultBlockState());
        }

        // Blue bed (dx=1, dz=1) foot → (dx=2, dz=1) head, facing east
        world.setBlockAndUpdate(temel.offset(1, 1, 1), Blocks.BLUE_BED.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT)
            .setValue(BedBlock.FACING, Direction.EAST));
        world.setBlockAndUpdate(temel.offset(2, 1, 1), Blocks.BLUE_BED.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.HEAD)
            .setValue(BedBlock.FACING, Direction.EAST));

        // Chest (dx=5, dz=1) + Blood-renamed redstone
        BlockPos chestPos = temel.offset(5, 1, 1);
        world.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        var be = world.getBlockEntity(chestPos);
        if (be instanceof ChestBlockEntity chest) {
            for (int i = 0; i < 20; i++) {
                int slot = RNG.nextInt(chest.getContainerSize());
                if (!chest.getItem(slot).isEmpty()) continue;
                ItemStack stack = new ItemStack(Items.REDSTONE, 1 + RNG.nextInt(5));
                stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Blood").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                chest.setItem(slot, stack);
            }
            chest.setChanged();
        }

        // Cobweb'ler — duvarlar iç yüzey Y=2 üzerine 6 tane random
        for (int i = 0; i < 6; i++) {
            int side = RNG.nextInt(4);
            int pos = 1 + RNG.nextInt(5);
            BlockPos webPos = switch (side) {
                case 0 -> temel.offset(pos, 2, 1);          // North iç
                case 1 -> temel.offset(pos, 2, 5);          // South iç
                case 2 -> temel.offset(1, 2, pos);          // West iç
                default -> temel.offset(5, 2, pos);          // East iç
            };
            if (world.getBlockState(webPos).isAir()) {
                world.setBlockAndUpdate(webPos, Blocks.COBWEB.defaultBlockState());
            }
        }
    }

    public static void sunucuKapan() {
        yapilanPozisyonlar.clear();
        sonYapim = -100000L;
    }
}
