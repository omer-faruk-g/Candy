package com.photonpulse.candy.boss;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.dimension.CandyDimensions;
import com.photonpulse.candy.entity.CandyEntities;
import com.photonpulse.candy.platform.SunucuTick;
import com.photonpulse.candy.platform.EtkilesimOlaylari;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

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
        SunucuTick.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return; // saniyede bir
            long simdi = server.overworld().getGameTime();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
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
                    && player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                    tp.bossSavastaMi = false;
                }

                boolean mirrorda = player.level().dimension().equals(CandyDimensions.MIRROR);
                if (mirrorda) {
                    tp.mirrorToplamSureTick += 20;
                    spawnKontrol(player, tp);
                }
            }
        });

        // Gizemli Kitap ile boss'a sağ tık → WIN
        EtkilesimOlaylari.VARLIK.register((player, world, hand, entity) -> {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!(entity instanceof TrueRealCandyEntity boss)) return InteractionResult.PASS;
            var stack = sp.getItemInHand(hand);
            if (!com.photonpulse.candy.boss.GizemliKitap.esittir(stack)) return InteractionResult.PASS;
            if (boss.gizemliKitapKullanildi(sp)) {
                stack.shrink(1);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        Candy.LOG.info("[Candy] BossManager aktif — {} (v3.1)",
            DEBUG ? "DEBUG (Mirror 1dk, klon 0)" : "Mirror 30dk + 20 klon");
    }

    private static void spawnKontrol(ServerPlayer player, TruePersonality tp) {
        if (tp.bossSavastaMi) return;
        if (tp.mirrorToplamSureTick < MIRROR_SURE_SART) return;
        if (tp.karsilastigiKlonSayisi < KLON_SART) return;

        ServerLevel world = (ServerLevel) player.level();
        // Zaten bir boss var mı (yakında)?
        AABB box = new AABB(player.blockPosition()).inflate(64);
        if (!world.getEntitiesOfClass(TrueRealCandyEntity.class, box, e -> true).isEmpty()) {
            tp.bossSavastaMi = true;
            return;
        }

        BlockPos pos = player.blockPosition().relative(player.getDirection(), 6);
        int y = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
            pos.getX(), pos.getZ());
        pos = new BlockPos(pos.getX(), Math.max(world.getMinY() + 2, y), pos.getZ());

        // Ölüm/rejoin sonrası: boyutta yüklü kalmış kendi boss'u varsa (64 blok dışı) yenisini doğurma,
        // en ileri fazdakini sürdür, kalanını sil — tek boss kuralı
        var eskiler = world.getEntities(CandyEntities.TRUE_REAL_CANDY,
            e -> e.getOwnerUuid().map(player.getUUID()::equals).orElse(false));
        if (!eskiler.isEmpty()) {
            TrueRealCandyEntity devam = java.util.Collections.max(eskiler,
                java.util.Comparator.comparingInt(TrueRealCandyEntity::getFaz));
            eskiler.stream().filter(b -> b != devam).forEach(net.minecraft.world.entity.Entity::discard);
            tp.aktifBossUuid = devam.getUUID().toString();
            tp.bossSavastaMi = true;
            return;
        }

        var spawned = CandyEntities.TRUE_REAL_CANDY.spawn(world, pos, EntitySpawnReason.EVENT);
        if (spawned instanceof TrueRealCandyEntity boss) {
            boss.setOwnerUuid(player.getUUID());
            tp.aktifBossUuid = boss.getUUID().toString();
            tp.bossSavastaMi = true;
            player.displayClientMessage(Component.translatable("candy.boss.geldi")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
            Candy.LOG.info("[Candy] BOSS SPAWN → {} @{}", player.getName().getString(), pos);
        }
    }

    /** WIN — kitap drop, dünyalardan çıkar, 30 dk sessizlik. */
    public static void win(ServerPlayer player) {
        TruePersonality tp = PersonalityManager.get(player);
        MinecraftServer srv = player.level().getServer();
        long simdi = (srv != null) ? srv.overworld().getGameTime() : player.level().getGameTime();
        tp.bossSavastaMi = false;
        tp.aktifBossUuid = "";
        tp.badEndingAktif = false;
        tp.bossOldurulmeTick = simdi; // overworld saati — BossManager ticker ile tutarlı
        tp.mirrorToplamSureTick = 0L; // sıfırla — hemen yeni boss spawn olmasın

        // Denek 239'un Notu (20 sayfalık tam sürüm) — envantere
        player.getInventory().placeItemBackInInventory(
            com.photonpulse.candy.lore.Denek239Book.olusturTam(true));

        // Gerçek dünyaya çıkar
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            ServerLevel overworld = server.overworld();
            BlockPos donus = BlockPos.of(tp.sonMirrorGiris);
            overworld.getChunk((donus.getX()) >> 4, (donus.getZ()) >> 4); // chunk yüklü değilse getHeight minY döndürür (oyuncu -64'e ışınlanır)
            int y = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                donus.getX(), donus.getZ());
            player.teleportTo(overworld, donus.getX() + 0.5, y, donus.getZ() + 0.5,
                java.util.Collections.emptySet(), player.getYRot(), 0f, false);
            player.setHealth(player.getMaxHealth());
        }

        player.displayClientMessage(Component.translatable("candy.boss.win")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "karanlik_efendi");
        Candy.LOG.info("[Candy] BOSS WIN → {}", player.getName().getString());
    }

    /** BAD ENDING — countdown bitti. Kick + kalıcı 2x hız. */
    public static void badEnding(ServerPlayer player) {
        TruePersonality tp = PersonalityManager.get(player);
        tp.bossSavastaMi = false;
        tp.aktifBossUuid = "";
        tp.badEndingAktif = true;
        tp.badEndingBaslangicTick = player.level().getGameTime();
        tp.mirrorToplamSureTick = 0L; // sıfırla — hemen yeni boss spawn olmasın
        tp.candyOzguven = Math.min(100, tp.candyOzguven + 40);

        com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "daha_hizli");
        Candy.LOG.info("[Candy] BOSS BAD ENDING → {}", player.getName().getString());

        if (player.connection != null) {
            player.connection.disconnect(
                Component.translatable("candy.boss.bad_kick").withStyle(ChatFormatting.DARK_RED));
        }
    }

    /** WIN sonrası 30 dk sessizlik aktif mi (Command Lock / Totem hariç mekanikler susar). */
    public static boolean sessizliktemi(TruePersonality tp, long simdi) {
        return tp.bossOldurulmeTick > 0 && (simdi - tp.bossOldurulmeTick) < WIN_SESSIZLIK;
    }
}
