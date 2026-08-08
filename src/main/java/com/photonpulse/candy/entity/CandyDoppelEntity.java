package com.photonpulse.candy.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
public class CandyDoppelEntity extends HostileEntity {

    // UUID için OPTIONAL_UUID handler yok; STRING kullan
    private static final TrackedData<String> OWNER_UUID_STR =
        DataTracker.registerData(CandyDoppelEntity.class, TrackedDataHandlerRegistry.STRING);

    private int mindControlTimer = 0;
    private static final int MIND_CONTROL_ARALIGI = 100;   // 5 sn
    private static final double MIND_CONTROL_YARICAP = 16.0;
    private static final String KLON_ETIKETI = "candy_clone";

    // Boss variantlar — command tag ile ayırdedilir:
    //   BEKCI: sabit durur, saldırmaz, invulnerable, 15 sn sonra despawn (silent watcher)
    //   YANSIMA: her tick oyuncunun pozisyonunu ayna gibi kopyalar
    public static final String TAG_BEKCI = "candy_bekci";
    public static final String TAG_YANSIMA = "candy_yansima";
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

    public CandyDoppelEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createDoppelAttributes() {
        return HostileEntity.createHostileAttributes()
            .add(EntityAttributes.MAX_HEALTH, 40.0)
            .add(EntityAttributes.ATTACK_DAMAGE, 3.0)
            .add(EntityAttributes.MOVEMENT_SPEED, 0.28)
            .add(EntityAttributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(OWNER_UUID_STR, "");
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));

        // SADECE owner player'ı hedefle — MP'de başkasına saldırmasın
        this.targetSelector.add(1, new ActiveTargetGoal<PlayerEntity>(
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
        try {
            return Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        String s = this.dataTracker.get(OWNER_UUID_STR);
        if (s != null && !s.isEmpty()) view.putString("OwnerUuid", s);
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        view.getOptionalString("OwnerUuid").ifPresent(s -> this.dataTracker.set(OWNER_UUID_STR, s));
    }

    @Override
    protected void mobTick(ServerWorld world) {
        // Boss variantlar farklı davranır
        if (this.getCommandTags().contains(TAG_BEKCI)) {
            // Sabit dur, oyuncuya bak, 15 sn sonra sessizce kaybol.
            // NOT: super.mobTick skip edildiği için LookControl.tick çalışmıyor;
            // yaw manuel set edilir.
            bosOmruTick++;
            this.setVelocity(0, 0, 0);
            this.setNoGravity(true);   // alt blok kırılırsa düşmesin
            var ownerOpt = getOwnerUuid();
            if (ownerOpt.isPresent()) {
                var owner = world.getServer().getPlayerManager().getPlayer(ownerOpt.get());
                if (owner != null) oyuncuyaBak(owner);
            }
            if (bosOmruTick >= BEKCI_DESPAWN_TICK) this.discard();
            return;
        }
        if (this.getCommandTags().contains(TAG_YANSIMA)) {
            bosOmruTick++;
            this.setVelocity(0, 0, 0);
            this.setNoGravity(true);
            var ownerOpt = getOwnerUuid();
            if (ownerOpt.isPresent()) {
                var owner = world.getServer().getPlayerManager().getPlayer(ownerOpt.get());
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
        super.mobTick(world);
        if (++mindControlTimer >= MIND_CONTROL_ARALIGI) {
            mindControlTimer = 0;
            mindControlYap(world);
        }
        yemekMantigi();

        // v1.9.17: Evrimli — target'a ulaşamıyorsa blok kır/koy
        if (this.getCommandTags().contains(TAG_EVRIMLI) && world.getTime() % 40L == 0) {
            evrimliBlokManipulasyon(world);
        }
    }

    /**
     * v1.9.17: Evrimli klon — hedefe blok manipulasyonu ile ulaşmaya çalışır.
     * Player yukarıdaysa pillar-up, aşağıdaysa aşağı kaz, aynı seviyedeyse duvar kır.
     */
    private void evrimliBlokManipulasyon(ServerWorld world) {
        var target = this.getTarget();
        if (!(target instanceof net.minecraft.entity.player.PlayerEntity)) return;
        double mesafe = this.distanceTo(target);
        if (mesafe < 3 || mesafe > 20) return;

        double dy = target.getY() - this.getY();
        net.minecraft.util.math.BlockPos pos = this.getBlockPos();

        if (dy > 2) {
            // Player yukarıda — pillar up
            var basUstu = pos.up(2);
            if (!world.getBlockState(basUstu).isAir()) {
                world.breakBlock(basUstu, false);
                return;
            }
            // Şimdi ayak altına blok koy
            var ayak = pos.down();
            if (world.getBlockState(ayak).isSolidBlock(world, ayak)
                && world.getBlockState(pos).isAir()) {
                world.setBlockState(pos, net.minecraft.block.Blocks.COBBLESTONE.getDefaultState());
                this.setPosition(this.getX(), this.getY() + 1.0, this.getZ());
            }
        } else if (dy < -2) {
            // Player aşağıda — aşağı kaz
            var ayakAlti = pos.down();
            if (!world.getBlockState(ayakAlti).isAir()) {
                world.breakBlock(ayakAlti, false);
            }
        } else {
            // Aynı seviyede — target yönüne duvar kır
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            var yon = net.minecraft.util.math.Direction.getFacing(dx, 0, dz);
            var duvarPos = pos.offset(yon);
            if (!world.getBlockState(duvarPos).isAir()) {
                world.breakBlock(duvarPos, false);
            }
        }
    }

    /**
     * v1.9.17: Su üstünde yürüme — Candy boğulmasın.
     * Water fluid tag'ine sahip block üstünde yürüyebilir.
     */
    @Override
    public boolean canWalkOnFluid(net.minecraft.fluid.FluidState state) {
        return state.isIn(net.minecraft.registry.tag.FluidTags.WATER);
    }

    /**
     * LookControl.tick çalışmadan direct yaw/pitch set — boss variantlar için.
     */
    private void oyuncuyaBak(net.minecraft.entity.player.PlayerEntity player) {
        double dx = player.getX() - this.getX();
        double dy = player.getEyeY() - this.getEyeY();
        double dz = player.getZ() - this.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
        this.setPitch(pitch);
    }

    @Override
    public boolean damage(ServerWorld world, net.minecraft.entity.damage.DamageSource source, float amount) {
        // BEKCI invulnerable — vurulamaz, atmosfer boss
        if (this.getCommandTags().contains(TAG_BEKCI)) return false;
        // YANSIMA vurulunca hemen despawn eder (asıl varlığı yok gibi)
        if (this.getCommandTags().contains(TAG_YANSIMA)) {
            this.discard();
            return false;
        }
        return super.damage(world, source, amount);
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
        if (s.isIn(ItemTags.SWORDS)) return 100;
        if (s.getItem() == Items.TRIDENT) return 90;
        if (s.isIn(ItemTags.AXES)) return 80;
        if (s.getItem() == Items.CROSSBOW) return 70;
        if (s.getItem() == Items.BOW) return 65;
        if (s.isIn(ItemTags.PICKAXES) || s.isIn(ItemTags.SHOVELS)) return 40;
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
                ItemStack elimdeki = this.getEquippedStack(EquipmentSlot.MAINHAND);
                var foodComp = elimdeki.get(net.minecraft.component.DataComponentTypes.FOOD);
                if (foodComp != null) {
                    // Nutrition'a göre iyileş (yaklaşık)
                    float iyilesme = Math.min(foodComp.nutrition(), 8);
                    this.heal(iyilesme);
                    elimdeki.decrement(1);   // yemeği tüket
                }
                // Silaha dön
                if (!silahYedegi.isEmpty()) {
                    this.equipStack(EquipmentSlot.MAINHAND, silahYedegi);
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
            var foodComp = s.get(net.minecraft.component.DataComponentTypes.FOOD);
            if (foodComp != null) {
                yemek = s;
                yemekIdx = i;
                break;
            }
        }
        if (yemek.isEmpty()) return;

        // Silahı yedekle, yemeği ele geçir
        silahYedegi = this.getEquippedStack(EquipmentSlot.MAINHAND).copy();
        this.equipStack(EquipmentSlot.MAINHAND, yemek.copy());
        depoHotbar.set(yemekIdx, ItemStack.EMPTY);   // hotbar'dan çıkar (bir kere ye)
        yemekSwitchCooldown = YEMEK_YEME_SURESI_TICK;
    }

    /**
     * Yakındaki hostile mob'ların hedefini owner'a çevirir — halüsinasyon efekti.
     */
    private void mindControlYap(ServerWorld world) {
        Optional<UUID> ownerOpt = getOwnerUuid();
        if (ownerOpt.isEmpty()) return;
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerOpt.get());
        if (owner == null || !owner.isAlive()) return;

        Box arama = this.getBoundingBox().expand(MIND_CONTROL_YARICAP);
        world.getEntitiesByClass(HostileEntity.class, arama, m ->
            m != this
                && !(m instanceof CandyDoppelEntity)
                && !m.getCommandTags().contains(KLON_ETIKETI)
                && m.isAlive()
        ).forEach(mob -> {
            if (mob.getTarget() == null || mob.getTarget() != owner) {
                mob.setTarget(owner);
            }
        });
    }
}
