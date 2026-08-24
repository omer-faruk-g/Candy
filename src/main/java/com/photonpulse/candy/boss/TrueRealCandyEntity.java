package com.photonpulse.candy.boss;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.entity.CandyDoppelEntity;
import com.photonpulse.candy.entity.CandyEntities;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

/**
 * TrueRealCandyEntity (v3.1) — Candy'nin gerçek hali. 8 fazlı boss.
 *
 * Faz akışı:
 *  1 (500)      klon dalgaları (5 sn'de 1-5)
 *  2 (500-200)  dikey blok silme + klon dalgaları
 *  3 (200→50)   invulnerable, elit klon spawn — klon ölünce 200→50, oto Faz 4
 *  4 (50)       pasif, 15 sn nefes
 *  5 (50→750)   BUFF, cutscene
 *  6 (750-500)  Faz1+Faz2 birleşik
 *  7 (500-300)  chunk silme (kendi altı dahil, önce kaçar)
 *  8 (300→50)   invulnerable, 300 sn countdown, Gizemli Kitap ile öldürülür
 *
 * Blok/chunk silme SINIRLI ve GÜVENLİ tutulur (lag/grief engeli).
 */
public class TrueRealCandyEntity extends HostileEntity {

    private static final TrackedData<String> OWNER_UUID_STR =
        DataTracker.registerData(TrueRealCandyEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> FAZ =
        DataTracker.registerData(TrueRealCandyEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private final ServerBossBar bossBar = new ServerBossBar(
        Text.translatable("candy.boss.isim"), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_20);

    // Faz zamanlayıcıları
    private int klonTimer = 0;
    private int blokTimer = 0;
    private int faz4Timer = 0;
    private int fazGecisFreeze = 0;
    private int countdownTick = 0;               // Faz 8: 300 sn = 6000 tick
    private UUID elitKlonId = null;              // Faz 3 elit klon
    private boolean bossOldu = false;            // WIN işaretlendi mi

    private static final int COUNTDOWN_BASLANGIC = 6000; // 300 sn
    private static final String KLON_ETIKETI = "candy_clone";

    public TrueRealCandyEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createBossAttributes() {
        return HostileEntity.createHostileAttributes()
            .add(EntityAttributes.MAX_HEALTH, 500.0)
            .add(EntityAttributes.ATTACK_DAMAGE, 8.0)
            .add(EntityAttributes.MOVEMENT_SPEED, 0.30)
            .add(EntityAttributes.FOLLOW_RANGE, 64.0)
            .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(OWNER_UUID_STR, "");
        builder.add(FAZ, 1);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.7));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
        this.targetSelector.add(1, new net.minecraft.entity.ai.goal.ActiveTargetGoal<>(
            this, PlayerEntity.class, 10, true, false,
            (entity, world) -> {
                Optional<UUID> owner = getOwnerUuid();
                return owner.isPresent() && owner.get().equals(entity.getUuid());
            }));
    }

    public void setOwnerUuid(UUID uuid) {
        this.dataTracker.set(OWNER_UUID_STR, uuid != null ? uuid.toString() : "");
    }

    public Optional<UUID> getOwnerUuid() {
        String s = this.dataTracker.get(OWNER_UUID_STR);
        if (s == null || s.isEmpty()) return Optional.empty();
        try { return Optional.of(UUID.fromString(s)); }
        catch (IllegalArgumentException e) { return Optional.empty(); }
    }

    public int getFaz() { return this.dataTracker.get(FAZ); }
    private void setFaz(int f) { this.dataTracker.set(FAZ, f); }

    private ServerPlayerEntity owner(ServerWorld world) {
        var opt = getOwnerUuid();
        if (opt.isEmpty()) return null;
        return world.getServer().getPlayerManager().getPlayer(opt.get());
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        String s = this.dataTracker.get(OWNER_UUID_STR);
        if (s != null && !s.isEmpty()) view.putString("OwnerUuid", s);
        view.putInt("Faz", getFaz());
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        view.getOptionalString("OwnerUuid").ifPresent(s -> this.dataTracker.set(OWNER_UUID_STR, s));
        view.getOptionalInt("Faz").ifPresent(this::setFaz);
        // Faz 8'de reload → countdown sıfır olup anında Bad Ending TETİKLENMESİN.
        // Timer'ı yeniden başlat (cömert davran).
        if (getFaz() == 8) countdownTick = COUNTDOWN_BASLANGIC;
    }

    // ─── BossBar player yönetimi ───
    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        bossBar.removePlayer(player);
    }

    // ─── Ana döngü ───
    @Override
    protected void mobTick(ServerWorld world) {
        super.mobTick(world);

        // Owner set edilmemişse (ör. /summon ile) en yakın oyuncuyu owner yap
        if (getOwnerUuid().isEmpty()) {
            var yakin = world.getClosestPlayer(this, 48.0);
            if (yakin instanceof ServerPlayerEntity sp) setOwnerUuid(sp.getUuid());
        }

        // BossBar güncelle
        bossBar.setPercent(Math.max(0f, this.getHealth() / this.getMaxHealth()));

        if (fazGecisFreeze > 0) { fazGecisFreeze--; this.setVelocity(0, 0, 0); return; }

        int faz = getFaz();
        try {
            switch (faz) {
                case 1 -> faz1(world);
                case 2 -> faz2(world);
                case 3 -> faz3(world);
                case 4 -> faz4(world);
                case 5 -> faz5(world);
                case 6 -> faz6(world);
                case 7 -> faz7(world);
                case 8 -> faz8(world);
            }
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] Boss faz {} hata: {}", faz, t.getMessage());
        }

        // HP tabanlı geçişler (invulnerable/countdown fazları hariç)
        fazGecisKontrol();
    }

    private void fazGecisKontrol() {
        int faz = getFaz();
        float hp = this.getHealth();
        // Faz 1 → 2: HP 500 altına inince (ilk hasar)
        if (faz == 1 && hp < 500f) fazGec(2);
        // Faz 2 → 3: HP 200
        else if (faz == 2 && hp <= 200f) fazGec(3);
        // Faz 5 → 6: buff sonrası HP 750'den düşünce zaten faz 6 (fazGec(6) faz5'te set edilir)
        // Faz 6 → 7: HP 500
        else if (faz == 6 && hp <= 500f) fazGec(7);
        // Faz 7 → 8: HP 300
        else if (faz == 7 && hp <= 300f) fazGec(8);
        // Faz 3, 4, 8 kendi içlerinde geçer
    }

    private void fazGec(int yeniFaz) {
        setFaz(yeniFaz);
        fazGecisFreeze = 40; // 2 sn cutscene
        klonTimer = 0; blokTimer = 0; faz4Timer = 0;
        ServerWorld world = (ServerWorld) this.getWorld();
        ServerPlayerEntity p = owner(world);
        // Cutscene: kahkaha + mesaj
        try {
            world.playSound(null, this.getBlockPos(),
                com.photonpulse.candy.audio.CandySounds.LAUGH, SoundCategory.HOSTILE, 1.2f, 0.8f);
        } catch (Throwable ignored) {}
        if (p != null) {
            String key = "candy.boss.gecis." + yeniFaz;
            p.sendMessage(Text.translatable(key)
                .formatted(net.minecraft.util.Formatting.DARK_RED,
                           net.minecraft.util.Formatting.BOLD), false);
        }
        bossBar.setName(Text.translatable("candy.boss.isim")
            .append(Text.literal(" [" + yeniFaz + "/8]")));

        // Faz özel giriş işleri
        if (yeniFaz == 3) girisFaz3(world);
        if (yeniFaz == 5) girisFaz5(world);
        if (yeniFaz == 8) girisFaz8(world);
        Candy.LOG.info("[Candy] Boss faz {} → {}", yeniFaz - 1, yeniFaz);
    }

    // ─── Faz 1: klon dalgaları ───
    private void faz1(ServerWorld world) {
        if (++klonTimer < 100) return; // 5 sn
        klonTimer = 0;
        int adet = 1 + this.random.nextInt(5);
        klonDalgasi(world, adet);
    }

    // ─── Faz 2: dikey blok silme + klon ───
    private void faz2(ServerWorld world) {
        if (++klonTimer >= 100) { klonTimer = 0; klonDalgasi(world, 1 + this.random.nextInt(3)); }
        if (++blokTimer >= 60) { blokTimer = 0; dikeyBlokSil(world); }
    }

    // ─── Faz 3: invulnerable + elit klon ───
    private void girisFaz3(ServerWorld world) {
        elitKlonId = eliteKlonSpawn(world);
    }
    private void faz3(ServerWorld world) {
        // Elit klon öldü mü kontrol
        if (elitKlonId != null) {
            var ent = world.getEntity(elitKlonId);
            if (ent == null || !ent.isAlive()) {
                // Klon öldü → Candy 200→50, oto Faz 4
                this.setHealth(50f);
                elitKlonId = null;
                fazGec(4);
            }
        } else {
            // Klon hiç spawn olamadıysa yine de ilerle
            this.setHealth(50f);
            fazGec(4);
        }
    }

    // ─── Faz 4: pasif nefes 15 sn ───
    private void faz4(ServerWorld world) {
        this.setVelocity(0, 0, 0);
        if (++faz4Timer >= 300) { faz4Timer = 0; fazGec(5); }
    }

    // ─── Faz 5: BUFF 750 ───
    private void girisFaz5(ServerWorld world) {
        var inst = this.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (inst != null) inst.setBaseValue(750.0);
        this.setHealth(750f);
        // Faz 5 anında faz 6'ya geç (buff bir "giriş" fazı)
    }
    private void faz5(ServerWorld world) {
        fazGec(6);
    }

    // ─── Faz 6: Faz1 + Faz2 birleşik ───
    private void faz6(ServerWorld world) {
        if (++klonTimer >= 100) { klonTimer = 0; klonDalgasi(world, 1 + this.random.nextInt(5)); }
        if (++blokTimer >= 50) { blokTimer = 0; dikeyBlokSil(world); }
    }

    // ─── Faz 7: chunk silme (kendi altı dahil, önce kaçar) ───
    private void faz7(ServerWorld world) {
        if (++klonTimer >= 120) { klonTimer = 0; klonDalgasi(world, 1 + this.random.nextInt(3)); }
        if (++blokTimer >= 120) { blokTimer = 0; chunkSil(world); }
    }

    // ─── Faz 8: countdown + Gizemli Kitap ───
    private void girisFaz8(ServerWorld world) {
        var inst = this.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (inst != null) inst.setBaseValue(50.0);
        this.setHealth(50f);
        countdownTick = COUNTDOWN_BASLANGIC;
    }
    private void faz8(ServerWorld world) {
        this.setVelocity(0, 0, 0);
        countdownTick--;
        ServerPlayerEntity p = owner(world);
        // Countdown sync — her saniye
        if (p != null && countdownTick % 20 == 0) {
            int saniye = Math.max(0, countdownTick / 20);
            BossCountdown.gonder(p, saniye);
        }
        if (countdownTick <= 0) {
            badEnding(world);
        }
    }

    // ─── WIN — Gizemli Kitap ile öldürülür (BossManager çağırır) ───
    public boolean gizemliKitapKullanildi(ServerPlayerEntity player) {
        if (getFaz() != 8) {
            player.sendMessage(Text.translatable("candy.boss.vakti_degil")
                .formatted(net.minecraft.util.Formatting.DARK_GRAY), false);
            return false;
        }
        bossOldu = true;
        ServerWorld world = (ServerWorld) this.getWorld();
        BossCountdown.gonder(player, -1); // temizle
        this.setHealth(0f);
        this.kill(world);
        return true;
    }

    private void badEnding(ServerWorld world) {
        ServerPlayerEntity p = owner(world);
        if (p != null) {
            BossManager.badEnding(p);
            BossCountdown.gonder(p, -1);
        }
        bossBar.clearPlayers();
        this.discard();
    }

    @Override
    public boolean damage(ServerWorld world, net.minecraft.entity.damage.DamageSource source, float amount) {
        int faz = getFaz();
        // Faz 3, 4, 8 invulnerable (klon/kitap ile ilerlenir)
        if (faz == 3 || faz == 4 || faz == 8) return false;
        return super.damage(world, source, amount);
    }

    @Override
    public void onDeath(net.minecraft.entity.damage.DamageSource source) {
        super.onDeath(source);
        bossBar.clearPlayers();
        if (this.getWorld() instanceof ServerWorld world) {
            klonlariTemizle(world);
            if (bossOldu) {
                ServerPlayerEntity p = owner(world);
                if (p != null) BossManager.win(p);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.clearPlayers();
        if (this.getWorld() instanceof ServerWorld world) klonlariTemizle(world);
        super.remove(reason);
    }

    /** Boss'un spawn ettiği klonları temizle — Mirror'da swarm kalmasın. */
    private void klonlariTemizle(ServerWorld world) {
        try {
            var kutu = new net.minecraft.util.math.Box(this.getBlockPos()).expand(64);
            world.getEntitiesByClass(CandyDoppelEntity.class, kutu,
                e -> e.getCommandTags().contains(KLON_ETIKETI))
                .forEach(net.minecraft.entity.Entity::discard);
        } catch (Throwable ignored) {}
    }

    /** Yakındaki boss-klonu sayısı — cap için. */
    private int yakinKlonSayisi(ServerWorld world) {
        var kutu = new net.minecraft.util.math.Box(this.getBlockPos()).expand(32);
        return world.getEntitiesByClass(CandyDoppelEntity.class, kutu,
            e -> e.isAlive() && e.getCommandTags().contains(KLON_ETIKETI)).size();
    }

    // ═══════════ Yardımcılar ═══════════

    private void klonDalgasi(ServerWorld world, int adet) {
        var ownerOpt = getOwnerUuid();
        if (ownerOpt.isEmpty()) return;
        // Cap — 25'ten fazla klon varken yenisini spawn etme (lag/grief engeli)
        if (yakinKlonSayisi(world) >= 25) return;
        for (int i = 0; i < adet; i++) {
            double a = this.random.nextDouble() * Math.PI * 2;
            double d = 4 + this.random.nextDouble() * 6;
            BlockPos pos = BlockPos.ofFloored(this.getX() + Math.cos(a) * d,
                this.getY(), this.getZ() + Math.sin(a) * d);
            var spawned = CandyEntities.DOPPEL.spawn(world, pos, SpawnReason.MOB_SUMMONED);
            if (spawned instanceof CandyDoppelEntity klon) {
                klon.setOwnerUuid(ownerOpt.get());
                klon.addCommandTag(KLON_ETIKETI);
            }
        }
    }

    private UUID eliteKlonSpawn(ServerWorld world) {
        var ownerOpt = getOwnerUuid();
        if (ownerOpt.isEmpty()) return null;
        BlockPos pos = this.getBlockPos().offset(this.getHorizontalFacing(), 3);
        var spawned = CandyEntities.DOPPEL.spawn(world, pos, SpawnReason.MOB_SUMMONED);
        if (spawned instanceof CandyDoppelEntity klon) {
            klon.setOwnerUuid(ownerOpt.get());
            klon.addCommandTag(KLON_ETIKETI);
            klon.addCommandTag(CandyDoppelEntity.TAG_EVRIMLI);
            var hpInst = klon.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (hpInst != null) hpInst.setBaseValue(50.0);
            klon.setHealth(50f);
            var dmgInst = klon.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
            if (dmgInst != null) dmgInst.setBaseValue(4.0);
            return klon.getUuid();
        }
        return null;
    }

    /** Dikey blok silme — Candy'nin ALTI HARİÇ rastgele bir kolonu min_y'ye kadar sil (sınırlı). */
    private void dikeyBlokSil(ServerWorld world) {
        int cx = this.getBlockX(), cz = this.getBlockZ();
        int rx, rz, deneme = 0;
        do {
            rx = cx + this.random.nextInt(21) - 10;
            rz = cz + this.random.nextInt(21) - 10;
            deneme++;
        } while (rx == cx && rz == cz && deneme < 5);

        int ust = this.getBlockY() + 6;
        int alt = Math.max(world.getBottomY(), this.getBlockY() - 40); // en fazla 46 blok
        for (int y = ust; y >= alt; y--) {
            BlockPos p = new BlockPos(rx, y, rz);
            if (!world.getBlockState(p).isAir()) {
                world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }

    /** Chunk silme — kendi chunk'ı dahil, ama önce güvenli yere kaçar. Sınırlı Y bandı. */
    private void chunkSil(ServerWorld world) {
        // Önce kaç — 8 blok rastgele yön
        double a = this.random.nextDouble() * Math.PI * 2;
        BlockPos kacis = BlockPos.ofFloored(this.getX() + Math.cos(a) * 12,
            this.getY(), this.getZ() + Math.sin(a) * 12);
        this.requestTeleport(kacis.getX() + 0.5, kacis.getY(), kacis.getZ() + 0.5);

        // Eski chunk'ı sil (player Y ± 12 bandı — full-height değil, lag/grief sınırı)
        int chunkX = (this.getBlockX() >> 4) << 4;
        int chunkZ = (this.getBlockZ() >> 4) << 4;
        int yMin = Math.max(world.getBottomY(), this.getBlockY() - 12);
        int yMax = this.getBlockY() + 12;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int y = yMin; y <= yMax; y++) {
                    BlockPos p = new BlockPos(chunkX + dx, y, chunkZ + dz);
                    if (!world.getBlockState(p).isAir()) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }
    }
}
