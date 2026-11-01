package com.photonpulse.candy.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * CandyDoppelEntity — Candy'nin oyuncu şeklinde klonu.
 *
 * KRITIK: HostileEntity extends — Zombie/Skeleton DEĞİL. Iron golem'in built-in
 * target listesi Zombie/Illager vs. Bu entity hedef alınmıyor.
 *
 * Öznitelikler: 40 HP, 3 melee damage, player-hızı hareket.
 *
 * Ekstra: mind control — her 5 sn'de bir 16 blok çevresindeki hostile mob'ları
 * owner'a hedef verir.
 */
public class CandyDoppelEntity extends Monster {

    // UUID için OPTIONAL_UUID handler yok; STRING kullan
    private static final EntityDataAccessor<String> OWNER_UUID_STR =
        SynchedEntityData.defineId(CandyDoppelEntity.class, EntityDataSerializers.STRING);

    private int mindControlTimer = 0;
    private static final int MIND_CONTROL_ARALIGI = 100;   // 5 sn
    private static final double MIND_CONTROL_YARICAP = 16.0;
    private static final String KLON_ETIKETI = "candy_clone";

    // Boss variantlar — command tag ile ayırdedilir:
    //   BEKCI: sabit durur, saldırmaz, invulnerable, 15 sn sonra despawn (silent watcher)
    //   YANSIMA: her tick oyuncunun pozisyonunu ayna gibi kopyalar
    public static final String TAG_BEKCI = "candy_bekci";
    public static final String TAG_YANSIMA = "candy_yansima";
    // v3.2 (idea 13) Ayna kopyası — AI yok; konum/rotasyon AynaSistemi tarafından
    // her tick set edilir (oyuncunun kayıtlı hareketini tekrar oynatır). Vurulamaz.
    public static final String TAG_AYNA = "candy_ayna";
    // v1.9.17: Evrim tag'leri
    public static final String TAG_EVRIMLI = "candy_evrimli"; // 4+ klon sonrası — blok kırıp koyabilir
    public static final String TAG_ELITE = "candy_elite";     // %15, envanter tarayıp ekipman üretir
    private static final int BEKCI_DESPAWN_TICK = 300;   // 15 sn
    private int bosOmruTick = 0;

    // Oyuncunun hotbar'ının snapshot'ı (9 slot). Spawn'da CloneManager tarafından set edilir.
    // Doppel canı düşünce yemek varsa switch eder, sonra silahına geri döner.
    private final List<ItemStack> depoHotbar = new ArrayList<>();
    private int yemekSwitchCooldown = 0;
    private ItemStack silahYedegi = ItemStack.EMPTY;   // Yemek yerken saklanacak silah
    private static final float DUSUK_CAN_ORAN = 0.5f;   // %50 altı → yemek düşün
    private static final int YEMEK_YEME_SURESI_TICK = 32;   // ~1.6 sn

    public CandyDoppelEntity(EntityType<? extends Monster> entityType, Level world) {
        super(entityType, world);
    }

    public static AttributeSupplier.Builder createDoppelAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID_STR, "");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 12.0f));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        // SADECE owner player'ı hedefle — MP'de başkasına saldırmasın
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<Player>(
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
        try {
            return Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        String s = this.entityData.get(OWNER_UUID_STR);
        if (s != null && !s.isEmpty()) view.putString("OwnerUuid", s);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        view.getString("OwnerUuid").ifPresent(s -> this.entityData.set(OWNER_UUID_STR, s));
    }

    @Override
    protected void customServerAiStep(ServerLevel world) {
        // v3.2 Ayna kopyası — kendi AI'sı yok; AynaSistemi konumu sürer. Yerçekimsiz, sabit.
        if (this.getTags().contains(TAG_AYNA)) {
            this.setDeltaMovement(0, 0, 0);
            this.setNoGravity(true);
            return; // super.mobTick çağrılmaz — hareket dışarıdan kontrol edilir
        }
        // Boss variantlar farklı davranır
        if (this.getTags().contains(TAG_BEKCI)) {
            // Sabit dur, oyuncuya bak, 15 sn sonra sessizce kaybol.
            // NOT: super.mobTick skip edildiği için LookControl.tick çalışmıyor;
            // yaw manuel set edilir.
            bosOmruTick++;
            this.setDeltaMovement(0, 0, 0);
            this.setNoGravity(true);   // alt blok kırılırsa düşmesin
            var ownerOpt = getOwnerUuid();
            if (ownerOpt.isPresent()) {
                var owner = world.getServer().getPlayerList().getPlayer(ownerOpt.get());
                if (owner != null) oyuncuyaBak(owner);
            }
            if (bosOmruTick >= BEKCI_DESPAWN_TICK) this.discard();
            return;
        }
        if (this.getTags().contains(TAG_YANSIMA)) {
            bosOmruTick++;
            this.setDeltaMovement(0, 0, 0);
            this.setNoGravity(true);
            var ownerOpt = getOwnerUuid();
            if (ownerOpt.isPresent()) {
                var owner = world.getServer().getPlayerList().getPlayer(ownerOpt.get());
                if (owner != null) {
                    oyuncuyaBak(owner);
                    if (this.distanceTo(owner) < 8) {
                        this.discard();
                        return;
                    }
                }
            }
            if (bosOmruTick >= 500) this.discard();
            return;
        }

        // Normal doppel
        super.customServerAiStep(world);
        if (++mindControlTimer >= MIND_CONTROL_ARALIGI) {
            mindControlTimer = 0;
            mindControlYap(world);
        }
        yemekMantigi();

        // v1.9.17: Evrimli — target'a ulaşamıyorsa blok kır/koy
        if (this.getTags().contains(TAG_EVRIMLI) && world.getGameTime() % 40L == 0) {
            evrimliBlokManipulasyon(world);
        }
    }

    /**
     * v1.9.17: Evrimli klon — hedefe blok manipulasyonu ile ulaşmaya çalışır.
     * Player yukarıdaysa pillar-up, aşağıdaysa aşağı kaz, aynı seviyedeyse duvar kır.
     */
    private void evrimliBlokManipulasyon(ServerLevel world) {
        var target = this.getTarget();
        if (!(target instanceof net.minecraft.world.entity.player.Player)) return;
        double mesafe = this.distanceTo(target);
        if (mesafe < 3 || mesafe > 20) return;

        double dy = target.getY() - this.getY();
        net.minecraft.core.BlockPos pos = this.blockPosition();

        if (dy > 2) {
            // Player yukarıda — pillar up
            var basUstu = pos.above(2);
            if (!world.getBlockState(basUstu).isAir()) {
                world.destroyBlock(basUstu, false);
                return;
            }
            // Şimdi ayak altına blok koy
            var ayak = pos.below();
            if (world.getBlockState(ayak).isRedstoneConductor(world, ayak)
                && world.getBlockState(pos).isAir()) {
                world.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
                this.setPos(this.getX(), this.getY() + 1.0, this.getZ());
            }
        } else if (dy < -2) {
            // Player aşağıda — aşağı kaz
            var ayakAlti = pos.below();
            if (!world.getBlockState(ayakAlti).isAir()) {
                world.destroyBlock(ayakAlti, false);
            }
        } else {
            // Aynı seviyede — target yönüne duvar kır
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            var yon = net.minecraft.core.Direction.getApproximateNearest(dx, 0, dz);
            var duvarPos = pos.relative(yon);
            if (!world.getBlockState(duvarPos).isAir()) {
                world.destroyBlock(duvarPos, false);
            }
        }
    }

    /**
     * v1.9.17: Su üstünde yürüme — Candy boğulmasın.
     * Water fluid tag'ine sahip block üstünde yürüyebilir.
     */
    @Override
    public boolean canStandOnFluid(net.minecraft.world.level.material.FluidState state) {
        return state.is(net.minecraft.tags.FluidTags.WATER);
    }

    /**
     * LookControl.tick çalışmadan direct yaw/pitch set — boss variantlar için.
     */
    private void oyuncuyaBak(net.minecraft.world.entity.player.Player player) {
        double dx = player.getX() - this.getX();
        double dy = player.getEyeY() - this.getEyeY();
        double dz = player.getZ() - this.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        this.setYRot(yaw);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
        this.setXRot(pitch);
    }

    @Override
    public boolean hurtServer(ServerLevel world, net.minecraft.world.damagesource.DamageSource source, float amount) {
        // BEKCI invulnerable — vurulamaz, atmosfer boss
        if (this.getTags().contains(TAG_BEKCI)) return false;
        // AYNA kopyası — vurulamaz (o sensin; ona vurmak seni vurmak gibi)
        if (this.getTags().contains(TAG_AYNA)) return false;
        // YANSIMA vurulunca hemen despawn eder (asıl varlığı yok gibi)
        if (this.getTags().contains(TAG_YANSIMA)) {
            this.discard();
            return false;
        }
        return super.hurtServer(world, source, amount);
    }

    // ─── Hotbar depo + akıllı switch ───

    public void setDepoHotbar(List<ItemStack> hotbar) {
        this.depoHotbar.clear();
        for (ItemStack s : hotbar) this.depoHotbar.add(s == null ? ItemStack.EMPTY : s.copy());
    }

    /**
     * Hotbar'dan en iyi silahı seç. Öncelik: Sword > Trident > Axe > Bow/Crossbow >
     * Pickaxe/Shovel > herhangi bir eşya. Boş dönerse null.
     */
    public ItemStack enIyiSilahiSec() {
        return depoHotbar.stream()
            .filter(s -> !s.isEmpty())
            .max(Comparator.comparingInt(CandyDoppelEntity::silahOnceligi))
            .orElse(ItemStack.EMPTY);
    }

    private static int silahOnceligi(ItemStack s) {
        // 1.21+ item tag'leri kullan (SwordItem/MiningToolItem gibi sınıflar kaldırıldı)
        if (s.is(ItemTags.SWORDS)) return 100;
        if (s.getItem() == Items.TRIDENT) return 90;
        if (s.is(ItemTags.AXES)) return 80;
        if (s.getItem() == Items.CROSSBOW) return 70;
        if (s.getItem() == Items.BOW) return 65;
        if (s.is(ItemTags.PICKAXES) || s.is(ItemTags.SHOVELS)) return 40;
        return 10;
    }

    /**
     * Yemek switch mantığı. Her tick:
     *  - Yeme cooldown'u aktifse (çiğniyor): sayacı azalt, süresi bitince iyileş + silaha geri dön.
     *  - Canı düşükse ve hotbar'da yemek varsa: silahı yedekle, yemeği ele al, cooldown başlat.
     */
    private void yemekMantigi() {
        if (yemekSwitchCooldown > 0) {
            yemekSwitchCooldown--;
            if (yemekSwitchCooldown == 0) {
                // Yeme bitti: iyileş + silaha dön
                ItemStack elimdeki = this.getItemBySlot(EquipmentSlot.MAINHAND);
                var foodComp = elimdeki.get(net.minecraft.core.component.DataComponents.FOOD);
                if (foodComp != null) {
                    // Nutrition'a göre iyileş (yaklaşık)
                    float iyilesme = Math.min(foodComp.nutrition(), 8);
                    this.heal(iyilesme);
                    elimdeki.shrink(1);   // yemeği tüket
                }
                // Silaha dön
                if (!silahYedegi.isEmpty()) {
                    this.setItemSlot(EquipmentSlot.MAINHAND, silahYedegi);
                    silahYedegi = ItemStack.EMPTY;
                }
            }
            return;
        }

        // Yemek gerekli mi?
        if (this.getHealth() >= this.getMaxHealth() * DUSUK_CAN_ORAN) return;

        // Yemek ara — hotbar'da food component'i olan item
        ItemStack yemek = ItemStack.EMPTY;
        int yemekIdx = -1;
        for (int i = 0; i < depoHotbar.size(); i++) {
            ItemStack s = depoHotbar.get(i);
            if (s.isEmpty()) continue;
            var foodComp = s.get(net.minecraft.core.component.DataComponents.FOOD);
            if (foodComp != null) {
                yemek = s;
                yemekIdx = i;
                break;
            }
        }
        if (yemek.isEmpty()) return;

        // Silahı yedekle, yemeği ele geçir
        silahYedegi = this.getItemBySlot(EquipmentSlot.MAINHAND).copy();
        this.setItemSlot(EquipmentSlot.MAINHAND, yemek.copy());
        depoHotbar.set(yemekIdx, ItemStack.EMPTY);   // hotbar'dan çıkar (bir kere ye)
        yemekSwitchCooldown = YEMEK_YEME_SURESI_TICK;
    }

    /**
     * Yakındaki hostile mob'ların hedefini owner'a çevirir — halüsinasyon efekti.
     */
    private void mindControlYap(ServerLevel world) {
        Optional<UUID> ownerOpt = getOwnerUuid();
        if (ownerOpt.isEmpty()) return;
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerOpt.get());
        if (owner == null || !owner.isAlive()) return;

        AABB arama = this.getBoundingBox().inflate(MIND_CONTROL_YARICAP);
        world.getEntitiesOfClass(Monster.class, arama, m ->
            m != this
                && !(m instanceof CandyDoppelEntity)
                && !m.getTags().contains(KLON_ETIKETI)
                && m.isAlive()
        ).forEach(mob -> {
            if (mob.getTarget() == null || mob.getTarget() != owner) {
                mob.setTarget(owner);
            }
        });
    }
}
