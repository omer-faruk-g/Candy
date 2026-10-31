package com.photonpulse.candy.boss;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.dimension.CandyDimensions;
import com.photonpulse.candy.entity.CandyEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * BossManager (v3.1) — True RealCandy boss'unun yaşam döngüsü.
 *
 * Spawn şartı: Mirror'da 30 dk (kümülatif) + 20+ klonla karşılaşma.
 * WIN: Denek 239 kitabı drop + Mirror/Anti-Mirror'dan çıkış + 30 dk sessizlik.
 * BAD ENDING: countdown biter → kick + kalıcı 2x hız.
 *
 * Debug: -Dcandy.debug=true → Mirror süre şartı 1 dk, klon şartı 0.
 */
public class BossManager {

    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");
    private static final long MIRROR_SURE_SART = DEBUG ? 1200L : 36000L; // 1 dk / 30 dk
    private static final int KLON_SART = DEBUG ? 0 : 20;
    private static final long WIN_SESSIZLIK = 36000L; // 30 dk

    public static void register() {
        BossCountdown.register();

        // Mirror'da geçirilen süreyi say + spawn şartı + WIN sessizlik penceresi yönetimi
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return; // saniyede bir
            long simdi = server.getOverworld().getTime();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;

                // WIN sessizliği bitti mi? 30 dk geçtiyse bossOldurulmeTick'i sıfırla
                // (agresivlikCarpani bunu kontrol ediyor — sıfır olunca susma biter).
                if (tp.bossOldurulmeTick > 0 && simdi - tp.bossOldurulmeTick >= WIN_SESSIZLIK) {
                    tp.bossOldurulmeTick = 0L;
                }

                // Recovery: bossSavastaMi=true ama oyuncu gerçek dünyada + boss yok
                // (ör. /kill ile boss silinmiş) → takılı kalmasın, sıfırla.
                if (tp.bossSavastaMi
                    && player.getWorld().getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) {
                    tp.bossSavastaMi = false;
                }

                boolean mirrorda = player.getWorld().getRegistryKey().equals(CandyDimensions.MIRROR);
                if (mirrorda) {
                    tp.mirrorToplamSureTick += 20;
                    spawnKontrol(player, tp);
                }
            }
        });

        // Gizemli Kitap ile boss'a sağ tık → WIN
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(entity instanceof TrueRealCandyEntity boss)) return ActionResult.PASS;
            var stack = sp.getStackInHand(hand);
            if (!com.photonpulse.candy.boss.GizemliKitap.esittir(stack)) return ActionResult.PASS;
            if (boss.gizemliKitapKullanildi(sp)) {
                stack.decrement(1);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        Candy.LOG.info("[Candy] BossManager aktif — {} (v3.1)",
            DEBUG ? "DEBUG (Mirror 1dk, klon 0)" : "Mirror 30dk + 20 klon");
    }

    private static void spawnKontrol(ServerPlayerEntity player, TruePersonality tp) {
        if (tp.bossSavastaMi) return;
        if (tp.mirrorToplamSureTick < MIRROR_SURE_SART) return;
        if (tp.karsilastigiKlonSayisi < KLON_SART) return;

        ServerWorld world = (ServerWorld) player.getWorld();
        // Zaten bir boss var mı (yakında)?
        Box box = new Box(player.getBlockPos()).expand(64);
        if (!world.getEntitiesByClass(TrueRealCandyEntity.class, box, e -> true).isEmpty()) {
            tp.bossSavastaMi = true;
            return;
        }

        BlockPos pos = player.getBlockPos().offset(player.getHorizontalFacing(), 6);
        int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,
            pos.getX(), pos.getZ());
        pos = new BlockPos(pos.getX(), Math.max(world.getBottomY() + 2, y), pos.getZ());

        var spawned = CandyEntities.TRUE_REAL_CANDY.spawn(world, pos, SpawnReason.EVENT);
        if (spawned instanceof TrueRealCandyEntity boss) {
            boss.setOwnerUuid(player.getUuid());
            tp.bossSavastaMi = true;
            player.sendMessage(Text.translatable("candy.boss.geldi")
                .formatted(Formatting.DARK_RED, Formatting.BOLD), false);
            Candy.LOG.info("[Candy] BOSS SPAWN → {} @{}", player.getName().getString(), pos);
        }
    }

    /** WIN — kitap drop, dünyalardan çıkar, 30 dk sessizlik. */
    public static void win(ServerPlayerEntity player) {
        TruePersonality tp = PersonalityManager.get(player);
        MinecraftServer srv = player.getWorld().getServer();
        long simdi = (srv != null) ? srv.getOverworld().getTime() : player.getWorld().getTime();
        tp.bossSavastaMi = false;
        tp.badEndingAktif = false;
        tp.bossOldurulmeTick = simdi; // overworld saati — BossManager ticker ile tutarlı
        tp.mirrorToplamSureTick = 0L; // sıfırla — hemen yeni boss spawn olmasın

        // Denek 239'un Notu (20 sayfalık tam sürüm) — envantere
        player.getInventory().offerOrDrop(
            com.photonpulse.candy.lore.Denek239Book.olusturTam(true));

        // Gerçek dünyaya çıkar
        MinecraftServer server = player.getWorld().getServer();
        if (server != null) {
            ServerWorld overworld = server.getOverworld();
            BlockPos donus = BlockPos.fromLong(tp.sonMirrorGiris);
            int y = overworld.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,
                donus.getX(), donus.getZ());
            player.teleport(overworld, donus.getX() + 0.5, y, donus.getZ() + 0.5,
                java.util.Collections.emptySet(), player.getYaw(), 0f, false);
            player.setHealth(player.getMaxHealth());
        }

        player.sendMessage(Text.translatable("candy.boss.win")
            .formatted(Formatting.GOLD, Formatting.BOLD), false);
        com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "karanlik_efendi");
        Candy.LOG.info("[Candy] BOSS WIN → {}", player.getName().getString());
    }

    /** BAD ENDING — countdown bitti. Kick + kalıcı 2x hız. */
    public static void badEnding(ServerPlayerEntity player) {
        TruePersonality tp = PersonalityManager.get(player);
        tp.bossSavastaMi = false;
        tp.badEndingAktif = true;
        tp.badEndingBaslangicTick = player.getWorld().getTime();
        tp.mirrorToplamSureTick = 0L; // sıfırla — hemen yeni boss spawn olmasın
        tp.candyOzguven = Math.min(100, tp.candyOzguven + 40);

        com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "daha_hizli");
        Candy.LOG.info("[Candy] BOSS BAD ENDING → {}", player.getName().getString());

        if (player.networkHandler != null) {
            player.networkHandler.disconnect(
                Text.translatable("candy.boss.bad_kick").formatted(Formatting.DARK_RED));
        }
    }

    /** WIN sonrası 30 dk sessizlik aktif mi (Command Lock / Totem hariç mekanikler susar). */
    public static boolean sessizliktemi(TruePersonality tp, long simdi) {
        return tp.bossOldurulmeTick > 0 && (simdi - tp.bossOldurulmeTick) < WIN_SESSIZLIK;
    }
}
