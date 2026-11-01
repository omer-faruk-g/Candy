package com.photonpulse.candy.boss;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.Psikoloji;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.entity.CandyDoppelEntity;
import com.photonpulse.candy.entity.CandyEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * BossEventler — nadir, yüksek etki boss anları.
 *
 * Şu an 3 boss + RealCandy (=4 toplam):
 *
 *  1. BEKCI — 20 blok ötede sabit, sessiz duran Candy klonu.
 *     Invulnerable, saldırmaz, 15 sn sonra kayıp. "Seni izliyorum" hissi.
 *
 *  2. YANSIMA — 15 blok ötede player'a bakan klon. Player yaklaşınca kaybolur.
 *     Vurulunca hemen kaybolur. "Yansıman gerçek mi?"
 *
 *  3. FISILTI — Entity yok. Oyuncunun 4 yönünden ambient warden/enderman
 *     sesleri çalar + gölge ses/blindness kısa flaş. Hiçbir şey görünmez.
 *
 * Trigger: her ~4 dk kontrol, agresivlik × psikoloji ile modüle. Kritik korku
 * veya kötü psikoloji artıkça daha sık.
 */
public class BossEventler {

    private static final int TICK_ARALIGI = 200;                // 10 sn kontrol
    private static final long OYUNCU_COOLDOWN_TICK = 4800L;     // oyuncu başına min 4 dk
    private static final double MIN_KORKU = 40.0;               // altı boss yok
    private static final double TETIKLEME_IHTIMAL = 0.08;       // her kontrolde %8

    private static int tickSayaci = 0;
    private static final Random RNG = new Random();
    private static final Map<UUID, Long> sonBossTick = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BossEventler::onTick);
        Candy.LOG.info("[Candy] BossEventler aktif — Bekci / Yansıma / Fısıltı");
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonBossTick.remove(uuid);
    }

    public static void sunucuKapan() {
        sonBossTick.clear();
        tickSayaci = 0;
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI != 0) return;

        long simdi = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;
            if (tp.korkuSkoru < MIN_KORKU) continue;

            Long son = sonBossTick.get(player.getUUID());
            double agresivlik = Psikoloji.agresivlikCarpani(tp);
            // Kötü psikolojide cooldown kısalır
            long cooldown = (long) (OYUNCU_COOLDOWN_TICK / agresivlik);
            if (son != null && simdi - son < cooldown) continue;

            // İhtimal — agresivlikle çarp
            if (RNG.nextDouble() > TETIKLEME_IHTIMAL * agresivlik) continue;

            // Rastgele boss seç (3 tip)
            int tur = RNG.nextInt(3);
            boolean basarili = switch (tur) {
                case 0 -> bekciSpawn(player, tp);
                case 1 -> yansimaSpawn(player, tp);
                default -> fisiltiTetikle(player, tp);
            };
            if (basarili) {
                sonBossTick.put(player.getUUID(), simdi);
                com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "patron");
            }
        }
    }

    // ─── BEKCI: sabit gözlemci ───
    private static boolean bekciSpawn(ServerPlayer player, TruePersonality tp) {
        ServerLevel world = (ServerLevel) player.level();
        BlockPos hedef = uzakKonum(world, player, 18, 22);
        if (hedef == null) return false;

        var spawned = CandyEntities.DOPPEL.spawn(world, hedef, EntitySpawnReason.MOB_SUMMONED);
        if (!(spawned instanceof CandyDoppelEntity klon)) return false;

        klon.setOwnerUuid(player.getUUID());
        klon.addTag(CandyDoppelEntity.TAG_BEKCI);
        klon.addTag("candy_clone");   // GriefSystem uyumluluğu
        klon.setInvulnerable(true);           // ekstra güvenlik
        klon.setPersistenceRequired();

        tp.sonMudahaleTick = world.getGameTime();
        Candy.LOG.info("[Candy] BOSS: Bekci → {} için 20 blok ötede", player.getName().getString());
        return true;
    }

    // ─── YANSIMA: mesafeli ayna ───
    private static boolean yansimaSpawn(ServerPlayer player, TruePersonality tp) {
        ServerLevel world = (ServerLevel) player.level();
        BlockPos hedef = uzakKonum(world, player, 12, 16);
        if (hedef == null) return false;

        var spawned = CandyEntities.DOPPEL.spawn(world, hedef, EntitySpawnReason.MOB_SUMMONED);
        if (!(spawned instanceof CandyDoppelEntity klon)) return false;

        klon.setOwnerUuid(player.getUUID());
        klon.addTag(CandyDoppelEntity.TAG_YANSIMA);
        klon.addTag("candy_clone");
        klon.setPersistenceRequired();

        tp.sonMudahaleTick = world.getGameTime();
        Candy.LOG.info("[Candy] BOSS: Yansıma → {} için 15 blok ötede", player.getName().getString());
        return true;
    }

    // 1.21.7'de SoundEvents sabitleri karma — bazı SoundEvent, bazı RegistryEntry<SoundEvent>.
    private static SoundEvent se(Object o) {
        if (o instanceof Holder<?> re) return (SoundEvent) re.value();
        return (SoundEvent) o;
    }

    // ─── FISILTI: sadece ses, hiçbir şey görünmez ───
    private static boolean fisiltiTetikle(ServerPlayer player, TruePersonality tp) {
        ServerLevel world = (ServerLevel) player.level();
        // 4 farklı yönden warden/enderman sesleri
        SoundEvent[] sesler = {
            se(SoundEvents.WARDEN_HEARTBEAT),
            se(SoundEvents.ENDERMAN_STARE),
            se(SoundEvents.AMBIENT_CAVE),
            se(SoundEvents.ENDERMAN_AMBIENT)
        };
        for (int i = 0; i < 4; i++) {
            double angle = (Math.PI / 2) * i + RNG.nextDouble() * 0.5;
            double mesafe = 6 + RNG.nextDouble() * 4;
            double x = player.getX() + Math.cos(angle) * mesafe;
            double z = player.getZ() + Math.sin(angle) * mesafe;
            double y = player.getY() + RNG.nextDouble() * 2 - 1;
            world.playSound(null, x, y, z, sesler[i], SoundSource.HOSTILE,
                0.9f, 0.5f + RNG.nextFloat() * 0.5f);
        }
        tp.sonMudahaleTick = world.getGameTime();
        Candy.LOG.info("[Candy] BOSS: Fısıltı → {} çevresinde", player.getName().getString());
        return true;
    }

    // ─── Konum yardımcısı ───
    private static BlockPos uzakKonum(ServerLevel world, ServerPlayer player, int minMes, int maxMes) {
        BlockPos merkez = player.blockPosition();
        for (int deneme = 0; deneme < 15; deneme++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double mesafe = minMes + RNG.nextDouble() * (maxMes - minMes);
            int x = merkez.getX() + (int) (Math.cos(angle) * mesafe);
            int z = merkez.getZ() + (int) (Math.sin(angle) * mesafe);
            int y = merkez.getY();
            for (int dy = 0; dy <= 3; dy++) {
                BlockPos p = new BlockPos(x, y + dy, z);
                if (uygun(world, p)) return p;
                if (dy > 0) {
                    p = new BlockPos(x, y - dy, z);
                    if (uygun(world, p)) return p;
                }
            }
            int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos p = new BlockPos(x, topY, z);
            if (uygun(world, p)) return p;
        }
        return null;
    }

    private static boolean uygun(ServerLevel world, BlockPos p) {
        return world.getBlockState(p).isAir()
            && world.getBlockState(p.above()).isAir()
            && world.getBlockState(p.below()).isRedstoneConductor(world, p.below());
    }
}
