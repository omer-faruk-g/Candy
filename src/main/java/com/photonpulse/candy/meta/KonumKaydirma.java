package com.photonpulse.candy.meta;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * KonumKaydirma (v8.3) — oyuncunun XYZ'sini sessizce kaydırır.
 *
 * <h3>NİYE KÜÇÜK, NİYE "OYUN SÜRESİNE BAĞLI" DEĞİL</h3>
 * İstek "oyun süresine bağlı olarak koordinatı değiştirsin"di. İki yerde biçim
 * değiştirdi, ikisi de bilinçli:
 *
 * <p><b>1. Oyun süresi tek kapı değil, yumuşak çarpan.</b> Takvime/süreye bağlı
 * tırmanma {@link com.photonpulse.candy.beyin.Perde}'de reddedilen hatanın aynısı:
 * emergent olmayan, AFK ile kandırılabilen bir mekanik. Burada kapı
 * {@code perdeKademe >= 2} + {@code candyOfke}; {@link TruePersonality#toplamOynananTick}
 * yalnızca ihtimali ve mesafeyi büyütüyor. Yani "uzun oynayan daha çok görür" korunuyor,
 * "sırf uzun oynadı diye açılır" kalkıyor.</p>
 *
 * <p><b>2. Kaydırma 3-12 blok, asla büyük teleport.</b> Oyuncuyu 500 blok öteye atmak
 * korku değil <i>hasar</i>: üs kaybolur, eşya kaybolur, oyuncu bunu "mod çöktü" diye
 * okur ve Candy'yi hiç suçlamaz. Küçük kaydırma ise tam tersi çalışır — tünelden
 * çıkarsın, sekiz blok yanda olduğunu fark edersin, hiçbir şey kaybetmemişsindir ve
 * <b>kendinden</b> şüphe edersin. Korku "nereye gittim"de değil, <b>"sandığım yerde
 * değilim"</b>de.</p>
 *
 * <h3>GÜVENLİK ŞARTLARI (hepsi zorunlu)</h3>
 * <ul>
 *   <li>Aynı boyut, aynı dünya. Boyut değişimi yok.</li>
 *   <li>Hedef yüklü chunk içinde — yüklenmemiş chunk'a atmak chunk yükleme
 *       fırtınası ve düşme ölümü demek.</li>
 *   <li>Ayak ve baş hizası hava, altı katı zemin. Duvarın içine, boşluğa, lavaya
 *       ya da suya atmıyor.</li>
 *   <li>Y en çok ±2 kayar — kaydırma yukarıdan düşme olmasın.</li>
 *   <li>Oyuncu yerde ve sakin olmalı: havada, araçta, uyurken, elytra ile uçarken,
 *       tırmanırken, az önce hasar almışken ya da creative/spectator'da kaydırılmaz.
 *       Bunların hepsinde kaydırma oyuncuyu <i>öldürebilir</i> ya da apaçık bir hata
 *       gibi görünür.</li>
 * </ul>
 *
 * <p>Tek bir güvenlik şartı sağlanmazsa hiçbir şey yapılmaz ve soğuma da başlamaz —
 * mekanik bir sonraki uygun ana saklanır.</p>
 */
public final class KonumKaydirma {

    private KonumKaydirma() {}

    /** Kontrol sıklığı (tick). 200 = 10 sn. */
    private static final int ARALIK = 200;

    /** İki kaydırma arası en az bu kadar tick (~30 dk oyun). */
    private static final long SOGUMA = 36_000L;

    /** Bu kademenin altında hiç çalışmaz. */
    private static final int MIN_KADEME = 2;

    /** Bu öfkenin altında çalışmaz. */
    private static final double OFKE_ESIGI = 0.30;

    /** En küçük / en büyük yatay kaydırma (blok). */
    private static final int MIN_MESAFE = 3;
    private static final int MAKS_MESAFE = 12;

    /** Y en çok bu kadar kayar. */
    private static final int MAKS_Y_FARK = 2;

    /** Oyun süresi çarpanının doyduğu nokta (~20 saat oyun). */
    private static final double SURE_DOYUM = 1_440_000.0;

    /** Son hasardan sonra en az bu kadar tick geçmeli (savaşta kaydırma yok). */
    private static final int HASAR_BEKLEME = 200;

    /** Güvenli konum araması kaç deneme yapar. */
    private static final int DENEME = 24;

    private static int sayac = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(KonumKaydirma::onTick);
        Candy.LOG.info("[Candy] KonumKaydirma aktif — sessiz küçük kaydırma");
    }

    private static void onTick(MinecraftServer server) {
        if (++sayac < ARALIK) return;
        sayac = 0;
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                TruePersonality tp = PersonalityManager.get(player);
                if (tp == null) continue;
                // Oynanan süreyi burada biriktiriyoruz — dünya tick'i değil OYUNCU tick'i.
                tp.toplamOynananTick += ARALIK;
                denemeYap(player, tp);
            }
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] KonumKaydirma tick hatası: {}", t.toString());
        }
    }

    private static void denemeYap(ServerPlayer player, TruePersonality tp) {
        if (tp.perdeKademe < MIN_KADEME) return;
        double ofke = Math.max(0.0, Math.min(1.0, tp.candyOfke));
        if (ofke < OFKE_ESIGI) return;
        if (tp.toplamOynananTick - tp.kaydirmaSonTick < SOGUMA) return;
        if (!uygunDurum(player)) return;

        // Oyun süresi: ihtimali ve mesafeyi büyüten yumuşak çarpan (0..1).
        double sure = Math.min(1.0, tp.toplamOynananTick / SURE_DOYUM);
        double ihtimal = 0.10 + ofke * 0.25 + sure * 0.20;
        if (Math.random() >= ihtimal) return;

        if (!(player.level() instanceof ServerLevel world)) return;

        int menzil = MIN_MESAFE + (int) Math.round((MAKS_MESAFE - MIN_MESAFE) * sure);
        BlockPos hedef = guvenliKonum(world, player.blockPosition(), menzil);
        if (hedef == null) return;

        // Bakış açısı KORUNUYOR: kafayı da çevirmek apaçık bir müdahale olur,
        // oyuncu "bir şey beni döndürdü" der. Sessiz kalması için yalnızca konum.
        player.teleportTo(world, hedef.getX() + 0.5, hedef.getY(), hedef.getZ() + 0.5,
            java.util.Collections.emptySet(), player.getYRot(), player.getXRot(), false);

        tp.kaydirmaSayisi++;
        tp.kaydirmaSonTick = tp.toplamOynananTick;
        tp.metaFarkindalik = Math.min(100.0, tp.metaFarkindalik + 1.5);
        tp.paranoyaSkoru = Math.min(100.0, tp.paranoyaSkoru + 2.0);

        Candy.LOG.info("[Candy] konum kaydırıldı ({}) — {} blok",
            player.getGameProfile().getName(), menzil);
    }

    /**
     * Oyuncu kaydırılabilir durumda mı.
     *
     * <p>Buradaki her şart bir ölüm ya da apaçık hata senaryosunu kapatıyor; biri
     * eksik olsa mekanik "korkutucu" değil "bozuk" olurdu.</p>
     */
    private static boolean uygunDurum(ServerPlayer player) {
        if (!player.isAlive()) return false;
        if (player.isSpectator() || player.isCreative()) return false;
        if (!player.onGround()) return false;              // havada = düşme ölümü
        if (player.isPassenger()) return false;               // at/kayık kopar
        if (player.isSleeping()) return false;
        if (player.isFallFlying()) return false;                // elytra
        if (player.onClimbable()) return false;
        if (player.isSwimming() || player.isInWater()) return false;
        if (player.isUsingItem()) return false;              // yemek/yay yarıda kalır
        if (player.hurtTime > 0) return false;
        if (player.tickCount - player.getLastHurtByMobTimestamp() < HASAR_BEKLEME) return false;
        if (player.getLastHurtMob() != null) return false;     // savaşın ortası
        return true;
    }

    /**
     * Yarıçap içinde oyuncu için güvenli bir konum bulur; bulamazsa {@code null}.
     *
     * <p>{@code null} dönmesi normal ve beklenen: dar tünelde, mağarada ya da su
     * kenarında güvenli yer yoktur. O zaman hiçbir şey yapılmıyor.</p>
     */
    private static BlockPos guvenliKonum(ServerLevel world, BlockPos merkez, int menzil) {
        for (int i = 0; i < DENEME; i++) {
            double aci = Math.random() * Math.PI * 2.0;
            double r = MIN_MESAFE + Math.random() * Math.max(1, menzil - MIN_MESAFE);
            int x = merkez.getX() + (int) Math.round(Math.cos(aci) * r);
            int z = merkez.getZ() + (int) Math.round(Math.sin(aci) * r);

            if (!world.areEntitiesLoaded(ChunkPos.asLong(x >> 4, z >> 4))) continue;

            for (int dy = 0; dy <= MAKS_Y_FARK; dy++) {
                for (int yon = (dy == 0 ? 0 : -1); yon <= 1; yon += 2) {
                    int y = merkez.getY() + dy * yon;
                    BlockPos p = new BlockPos(x, y, z);
                    if (guvenliMi(world, p)) return p;
                    if (dy == 0) break;
                }
            }
        }
        return null;
    }

    /** Ayak + baş hava, alt katı zemin, sıvı/ateş yok. */
    private static boolean guvenliMi(ServerLevel world, BlockPos ayak) {
        BlockPos bas = ayak.above();
        BlockPos zemin = ayak.below();

        BlockState sAyak = world.getBlockState(ayak);
        BlockState sBas = world.getBlockState(bas);
        BlockState sZemin = world.getBlockState(zemin);

        if (!sAyak.getCollisionShape(world, ayak).isEmpty()) return false;
        if (!sBas.getCollisionShape(world, bas).isEmpty()) return false;
        if (!sZemin.isRedstoneConductor(world, zemin)) return false;

        // Sıvı yok: lava ölüm, su ise "yüzüyorum" sürprizi.
        if (!sAyak.getFluidState().isEmpty()) return false;
        if (!sBas.getFluidState().isEmpty()) return false;
        if (sZemin.getFluidState().is(FluidTags.LAVA)) return false;

        // Ateş/magma/kaktüs gibi hasar veren zemin.
        if (sZemin.getBlock() == net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) return false;
        if (sZemin.getBlock() == net.minecraft.world.level.block.Blocks.CACTUS) return false;
        if (sAyak.getBlock() == net.minecraft.world.level.block.Blocks.FIRE) return false;
        if (sAyak.getBlock() == net.minecraft.world.level.block.Blocks.SOUL_FIRE) return false;
        if (sAyak.getBlock() == net.minecraft.world.level.block.Blocks.POWDER_SNOW) return false;

        return true;
    }

    public static void sunucuKapan() {
        sayac = 0;
    }
}
