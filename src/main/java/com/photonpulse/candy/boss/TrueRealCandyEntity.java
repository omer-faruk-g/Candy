package com.photonpulse.candy.boss;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.entity.CandyDoppelEntity;
import com.photonpulse.candy.entity.CandyEntities;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
public class TrueRealCandyEntity extends Monster {

    private static final EntityDataAccessor<String> OWNER_UUID_STR =
        SynchedEntityData.defineId(TrueRealCandyEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> FAZ =
        SynchedEntityData.defineId(TrueRealCandyEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossBar = new ServerBossEvent(
        Component.translatable("candy.boss.isim"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);

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

    public TrueRealCandyEntity(EntityType<? extends Monster> type, Level world) {
        super(type, world);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 500.0)
            .add(Attributes.ATTACK_DAMAGE, 8.0)
            .add(Attributes.MOVEMENT_SPEED, 0.30)
            .add(Attributes.FOLLOW_RANGE, 64.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID_STR, "");
        builder.define(FAZ, 1);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 24.0f));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
            this, Player.class, 10, true, false,
            (entity, world) -> {
                Optional<UUID> owner = getOwnerUuid();
                return owner.isPresent() && owner.get().equals(entity.getUUID());
            }));
    }

    public void setOwnerUuid(UUID uuid) {
        this.entityData.set(OWNER_UUID_STR, uuid != null ? uuid.toString() : "");
    }

    public Optional<UUID> getOwnerUuid() {
        String s = this.entityData.get(OWNER_UUID_STR);
        if (s == null || s.isEmpty()) return Optional.empty();
        try { return Optional.of(UUID.fromString(s)); }
        catch (IllegalArgumentException e) { return Optional.empty(); }
    }

    public int getFaz() { return this.entityData.get(FAZ); }
    private void setFaz(int f) { this.entityData.set(FAZ, f); }

    private ServerPlayer owner(ServerLevel world) {
        var opt = getOwnerUuid();
        if (opt.isEmpty()) return null;
        return world.getServer().getPlayerList().getPlayer(opt.get());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        String s = this.entityData.get(OWNER_UUID_STR);
        if (s != null && !s.isEmpty()) view.putString("OwnerUuid", s);
        view.putInt("Faz", getFaz());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        view.getString("OwnerUuid").ifPresent(s -> this.entityData.set(OWNER_UUID_STR, s));
        view.getInt("Faz").ifPresent(this::setFaz);
        // Faz 8'de reload → countdown sıfır olup anında Bad Ending TETİKLENMESİN.
        // Timer'ı yeniden başlat (cömert davran).
        if (getFaz() == 8) countdownTick = COUNTDOWN_BASLANGIC;
    }

    // ─── BossBar player yönetimi ───
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    // ─── Ana döngü ───
    @Override
    protected void customServerAiStep(ServerLevel world) {
        super.customServerAiStep(world);

        // Owner set edilmemişse (ör. /summon ile) en yakın oyuncuyu owner yap
        if (getOwnerUuid().isEmpty()) {
            var yakin = world.getNearestPlayer(this, 48.0);
            if (yakin instanceof ServerPlayer sp) setOwnerUuid(sp.getUUID());
        }

        // BossBar güncelle
        bossBar.setProgress(Math.max(0f, this.getHealth() / this.getMaxHealth()));

        if (fazGecisFreeze > 0) { fazGecisFreeze--; this.setDeltaMovement(0, 0, 0); return; }

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
        ServerLevel world = (ServerLevel) this.level();
        ServerPlayer p = owner(world);
        // Cutscene: kahkaha + mesaj
        try {
            world.playSound(null, this.blockPosition(),
                com.photonpulse.candy.audio.CandySounds.LAUGH, SoundSource.HOSTILE, 1.2f, 0.8f);
        } catch (Throwable ignored) {}
        if (p != null) {
            String key = "candy.boss.gecis." + yeniFaz;
            p.displayClientMessage(Component.translatable(key)
                .withStyle(net.minecraft.ChatFormatting.DARK_RED,
                           net.minecraft.ChatFormatting.BOLD), false);
        }
        bossBar.setName(Component.translatable("candy.boss.isim")
            .append(Component.literal(" [" + yeniFaz + "/8]")));

        // Faz özel giriş işleri
        if (yeniFaz == 3) girisFaz3(world);
        if (yeniFaz == 5) girisFaz5(world);
        if (yeniFaz == 8) girisFaz8(world);
        Candy.LOG.info("[Candy] Boss faz {} → {}", yeniFaz - 1, yeniFaz);
    }

    // ─── Faz 1: klon dalgaları ───
    private void faz1(ServerLevel world) {
        if (++klonTimer < 100) return; // 5 sn
        klonTimer = 0;
        int adet = 1 + this.random.nextInt(5);
        klonDalgasi(world, adet);
    }

    // ─── Faz 2: dikey blok silme + klon ───
    private void faz2(ServerLevel world) {
        if (++klonTimer >= 100) { klonTimer = 0; klonDalgasi(world, 1 + this.random.nextInt(3)); }
        if (++blokTimer >= 60) { blokTimer = 0; dikeyBlokSil(world); }
    }

    // ─── Faz 3: invulnerable + elit klon ───
    private void girisFaz3(ServerLevel world) {
        elitKlonId = eliteKlonSpawn(world);
    }
    private void faz3(ServerLevel world) {
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
    private void faz4(ServerLevel world) {
        this.setDeltaMovement(0, 0, 0);
        if (++faz4Timer >= 300) { faz4Timer = 0; fazGec(5); }
    }

    // ─── Faz 5: BUFF 750 ───
    private void girisFaz5(ServerLevel world) {
        var inst = this.getAttribute(Attributes.MAX_HEALTH);
        if (inst != null) inst.setBaseValue(750.0);
        this.setHealth(750f);
        // Faz 5 anında faz 6'ya geç (buff bir "giriş" fazı)
    }
    private void faz5(ServerLevel world) {
        fazGec(6);
    }

    // ─── Faz 6: Faz1 + Faz2 birleşik ───
    private void faz6(ServerLevel world) {
        if (++klonTimer >= 100) { klonTimer = 0; klonDalgasi(world, 1 + this.random.nextInt(5)); }
        if (++blokTimer >= 50) { blokTimer = 0; dikeyBlokSil(world); }
    }

    // ─── Faz 7: chunk silme (kendi altı dahil, önce kaçar) ───
    private void faz7(ServerLevel world) {
        if (++klonTimer >= 120) { klonTimer = 0; klonDalgasi(world, 1 + this.random.nextInt(3)); }
        if (++blokTimer >= 120) { blokTimer = 0; chunkSil(world); }
    }

    // ─── Faz 8: countdown + Gizemli Kitap ───
    private void girisFaz8(ServerLevel world) {
        var inst = this.getAttribute(Attributes.MAX_HEALTH);
        if (inst != null) inst.setBaseValue(50.0);
        this.setHealth(50f);
        countdownTick = COUNTDOWN_BASLANGIC;
    }
    private void faz8(ServerLevel world) {
        this.setDeltaMovement(0, 0, 0);
        countdownTick--;
        ServerPlayer p = owner(world);
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
    public boolean gizemliKitapKullanildi(ServerPlayer player) {
        if (getFaz() != 8) {
            player.displayClientMessage(Component.translatable("candy.boss.vakti_degil")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY), false);
            return false;
        }
        bossOldu = true;
        ServerLevel world = (ServerLevel) this.level();
        BossCountdown.gonder(player, -1); // temizle
        this.setHealth(0f);
        this.kill(world);
        return true;
    }

    private void badEnding(ServerLevel world) {
        ServerPlayer p = owner(world);
        if (p != null) {
            BossManager.badEnding(p);
            BossCountdown.gonder(p, -1);
        }
        bossBar.removeAllPlayers();
        this.discard();
    }

    @Override
    public boolean hurtServer(ServerLevel world, net.minecraft.world.damagesource.DamageSource source, float amount) {
        int faz = getFaz();
        // Faz 3, 4, 8 invulnerable (klon/kitap ile ilerlenir)
        if (faz == 3 || faz == 4 || faz == 8) return false;
        return super.hurtServer(world, source, amount);
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        bossBar.removeAllPlayers();
        if (this.level() instanceof ServerLevel world) {
            klonlariTemizle(world);
            if (bossOldu) {
                ServerPlayer p = owner(world);
                if (p != null) BossManager.win(p);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        if (this.level() instanceof ServerLevel world) klonlariTemizle(world);
        super.remove(reason);
    }

    /** Boss'un spawn ettiği klonları temizle — Mirror'da swarm kalmasın. */
    private void klonlariTemizle(ServerLevel world) {
        try {
            var kutu = new net.minecraft.world.phys.AABB(this.blockPosition()).inflate(64);
            world.getEntitiesOfClass(CandyDoppelEntity.class, kutu,
                e -> e.getTags().contains(KLON_ETIKETI))
                .forEach(net.minecraft.world.entity.Entity::discard);
        } catch (Throwable ignored) {}
    }

    /** Yakındaki boss-klonu sayısı — cap için. */
    private int yakinKlonSayisi(ServerLevel world) {
        var kutu = new net.minecraft.world.phys.AABB(this.blockPosition()).inflate(32);
        return world.getEntitiesOfClass(CandyDoppelEntity.class, kutu,
            e -> e.isAlive() && e.getTags().contains(KLON_ETIKETI)).size();
    }

    // ═══════════ Yardımcılar ═══════════

    private void klonDalgasi(ServerLevel world, int adet) {
        var ownerOpt = getOwnerUuid();
        if (ownerOpt.isEmpty()) return;
        // Cap — 25'ten fazla klon varken yenisini spawn etme (lag/grief engeli)
        if (yakinKlonSayisi(world) >= 25) return;
        for (int i = 0; i < adet; i++) {
            double a = this.random.nextDouble() * Math.PI * 2;
            double d = 4 + this.random.nextDouble() * 6;
            BlockPos pos = BlockPos.containing(this.getX() + Math.cos(a) * d,
                this.getY(), this.getZ() + Math.sin(a) * d);
            var spawned = CandyEntities.DOPPEL.spawn(world, pos, EntitySpawnReason.MOB_SUMMONED);
            if (spawned instanceof CandyDoppelEntity klon) {
                klon.setOwnerUuid(ownerOpt.get());
                klon.addTag(KLON_ETIKETI);
            }
        }
    }

    private UUID eliteKlonSpawn(ServerLevel world) {
        var ownerOpt = getOwnerUuid();
        if (ownerOpt.isEmpty()) return null;
        BlockPos pos = this.blockPosition().relative(this.getDirection(), 3);
        var spawned = CandyEntities.DOPPEL.spawn(world, pos, EntitySpawnReason.MOB_SUMMONED);
        if (spawned instanceof CandyDoppelEntity klon) {
            klon.setOwnerUuid(ownerOpt.get());
            klon.addTag(KLON_ETIKETI);
            klon.addTag(CandyDoppelEntity.TAG_EVRIMLI);
            var hpInst = klon.getAttribute(Attributes.MAX_HEALTH);
            if (hpInst != null) hpInst.setBaseValue(50.0);
            klon.setHealth(50f);
            var dmgInst = klon.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmgInst != null) dmgInst.setBaseValue(4.0);
            return klon.getUUID();
        }
        return null;
    }

    /** Dikey blok silme — Candy'nin ALTI HARİÇ rastgele bir kolonu min_y'ye kadar sil (sınırlı). */
    private void dikeyBlokSil(ServerLevel world) {
        int cx = this.getBlockX(), cz = this.getBlockZ();
        int rx, rz, deneme = 0;
        do {
            rx = cx + this.random.nextInt(21) - 10;
            rz = cz + this.random.nextInt(21) - 10;
            deneme++;
        } while (rx == cx && rz == cz && deneme < 5);

        int ust = this.getBlockY() + 6;
        int alt = Math.max(world.getMinY(), this.getBlockY() - 40); // en fazla 46 blok
        for (int y = ust; y >= alt; y--) {
            BlockPos p = new BlockPos(rx, y, rz);
            if (!world.getBlockState(p).isAir()) {
                world.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** Chunk silme — kendi chunk'ı dahil, ama önce güvenli yere kaçar. Sınırlı Y bandı. */
    private void chunkSil(ServerLevel world) {
        // Önce kaç — 8 blok rastgele yön
        double a = this.random.nextDouble() * Math.PI * 2;
        BlockPos kacis = BlockPos.containing(this.getX() + Math.cos(a) * 12,
            this.getY(), this.getZ() + Math.sin(a) * 12);
        this.teleportTo(kacis.getX() + 0.5, kacis.getY(), kacis.getZ() + 0.5);

        // Eski chunk'ı sil (player Y ± 12 bandı — full-height değil, lag/grief sınırı)
        int chunkX = (this.getBlockX() >> 4) << 4;
        int chunkZ = (this.getBlockZ() >> 4) << 4;
        int yMin = Math.max(world.getMinY(), this.getBlockY() - 12);
        int yMax = this.getBlockY() + 12;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int y = yMin; y <= yMax; y++) {
                    BlockPos p = new BlockPos(chunkX + dx, y, chunkZ + dz);
                    if (!world.getBlockState(p).isAir()) {
                        world.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }
}
