package com.photonpulse.candy.arkadas;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.entity.CandyEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.List;

/**
 * ArkadasYonetici (v8.3) — Arkadaş'ı doğurur, görev verir, tekliğini korur.
 *
 * <h3>TEKLİK</h3>
 * Dünyada en fazla bir Arkadaş olur. İkinci bir tane "bot sürüsü" hissi verir ve
 * ölümün ağırlığını sıfırlar: iki tane varsa birini öldürmek bir şey ifade etmez.
 *
 * <h3>SPAWN KAPISI</h3>
 * <ul>
 *   <li>{@link ArkadasKaydi#olduMu} — öldürüldüyse bir daha <b>hiç</b>.</li>
 *   <li>{@code perdeKademe >= 2} — Kurul kendini kanıtlamadan Candy'nin dünyaya
 *       elini uzatan en büyük mekaniği açılmıyor.</li>
 *   <li>{@code candyOfke} eşiği — öfkesiz Candy Arkadaş yollamıyor.</li>
 *   <li>Oyuncudan 24-48 blok ötede, güvenli zeminde, yüklü chunk'ta. Yanında
 *       belirmek "mod spawn etti" demek; uzakta belirip <i>yürüyerek</i> gelmesi
 *       "bir şey geliyor" demek.</li>
 * </ul>
 *
 * <h3>GÖREV SEÇİMİ</h3>
 * Tek oyuncuda görev {@code candyOfke} ve mesafeye göre seçiliyor; varsayılan
 * {@link ArkadasEntity.Gorev#IZLE} — durup bakmak. Çok oyuncuda fiziksel baskı yerine
 * {@link ArkadasFisilti} devreye giriyor (gerekçesi o sınıfta).
 */
public final class ArkadasYonetici {

    private ArkadasYonetici() {}

    /** Kontrol sıklığı (tick). 100 = 5 sn. */
    private static final int ARALIK = 100;

    private static final int MIN_KADEME = 2;
    private static final double OFKE_ESIGI = 0.35;

    /** Spawn mesafesi (blok). */
    private static final int MIN_UZAK = 24;
    private static final int MAKS_UZAK = 48;

    /** Güvenli konum araması kaç deneme yapar. */
    private static final int DENEME = 32;

    /** Her kontrolde spawn ihtimali — öfke doluyken bile aniden gelmesin. */
    private static final double SPAWN_IHTIMAL = 0.08;

    private static int sayac = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ArkadasYonetici::onTick);
        Candy.LOG.info("[Candy] ArkadasYonetici aktif");
    }

    private static void onTick(MinecraftServer server) {
        ArkadasFisilti.tickIlerlet();
        if (++sayac < ARALIK) return;
        sayac = 0;
        try {
            int oyuncuSayisi = server.getPlayerManager().getCurrentPlayerCount();
            if (oyuncuSayisi == 0) return;

            // Çok oyunculu: Candy'nin asıl aracı güveni bozmak.
            if (oyuncuSayisi > 1) ArkadasFisilti.dene(server);

            for (ServerWorld world : server.getWorlds()) {
                dunyaIcin(world, oyuncuSayisi);
            }
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] ArkadasYonetici tick hatası: {}", t.toString());
        }
    }

    private static void dunyaIcin(ServerWorld world, int oyuncuSayisi) {
        List<? extends ArkadasEntity> mevcut =
            world.getEntitiesByType(CandyEntities.ARKADAS, e -> true);

        if (!mevcut.isEmpty()) {
            gorevGuncelle(world, mevcut.get(0));
            // Fazlalık varsa temizle — teklik kuralı.
            for (int i = 1; i < mevcut.size(); i++) mevcut.get(i).discard();
            return;
        }

        if (ArkadasKaydi.olduMu(world)) return;

        List<ServerPlayerEntity> oyuncular = world.getPlayers();
        if (oyuncular.isEmpty()) return;

        ServerPlayerEntity hedef = null;
        double enYuksekOfke = 0.0;
        for (ServerPlayerEntity p : oyuncular) {
            TruePersonality tp = PersonalityManager.get(p);
            if (tp == null) continue;
            if (tp.perdeKademe < MIN_KADEME) continue;
            if (tp.candyOfke < OFKE_ESIGI) continue;
            if (p.isCreative() || p.isSpectator()) continue;
            if (tp.candyOfke > enYuksekOfke) {
                enYuksekOfke = tp.candyOfke;
                hedef = p;
            }
        }
        if (hedef == null) return;
        if (Math.random() >= SPAWN_IHTIMAL) return;

        BlockPos konum = spawnKonumu(world, hedef.getBlockPos());
        if (konum == null) return;

        ArkadasEntity a = CandyEntities.ARKADAS.create(
            (net.minecraft.world.World) world, net.minecraft.entity.SpawnReason.EVENT);
        if (a == null) return;
        a.refreshPositionAndAngles(konum.getX() + 0.5, konum.getY(), konum.getZ() + 0.5,
            world.getRandom().nextFloat() * 360f, 0f);
        world.spawnEntity(a);

        TruePersonality tp = PersonalityManager.get(hedef);
        if (tp != null) tp.arkadasKarsilasma++;

        Candy.LOG.info("[Candy] Arkadaş doğdu — {} için, {} blok ötede",
            hedef.getGameProfile().getName(),
            (int) Math.sqrt(konum.getSquaredDistance(hedef.getBlockPos())));
    }

    /**
     * Görev seçimi. Tek oyuncuda fiziksel, çok oyuncuda minimal.
     *
     * <p>Öfke arttıkça IZLE → KAZ → KES → SALDIR. SALDIR en sonda, çünkü saldırmak
     * gizemi bitirir: dövüşülen şey artık bir düşman, bir bilinmez değil. Candy'nin
     * en uzun süre kullandığı görev kasıtlı olarak IZLE.</p>
     */
    private static void gorevGuncelle(ServerWorld world, ArkadasEntity a) {
        ServerPlayerEntity yakin = null;
        double enYakin = Double.MAX_VALUE;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isCreative() || p.isSpectator()) continue;
            double d = a.squaredDistanceTo(p);
            if (d < enYakin) { enYakin = d; yakin = p; }
        }
        if (yakin == null) {
            a.gorevVer(ArkadasEntity.Gorev.IZLE);
            return;
        }

        TruePersonality tp = PersonalityManager.get(yakin);
        double ofke = tp == null ? 0.0 : Math.max(0.0, Math.min(1.0, tp.candyOfke));
        boolean cokOyuncu = world.getServer().getPlayerManager().getCurrentPlayerCount() > 1;

        ArkadasEntity.Gorev g;
        if (cokOyuncu) {
            // Kalabalıkta Arkadaş yalnızca var olsun; baskı fısıltıdan geliyor.
            g = ofke > 0.85 ? ArkadasEntity.Gorev.SALDIR : ArkadasEntity.Gorev.IZLE;
        } else if (ofke > 0.88) {
            g = ArkadasEntity.Gorev.SALDIR;
        } else if (ofke > 0.70) {
            g = ArkadasEntity.Gorev.KES;
        } else if (ofke > 0.50 && enYakin > 64.0) {
            // Uzaktayken kazması duvarın arkasından gelen ses demek; yakında
            // kazmak anlamsız (oyuncu onu görüyor).
            g = ArkadasEntity.Gorev.KAZ;
        } else {
            g = ArkadasEntity.Gorev.IZLE;
        }
        a.gorevVer(g);
    }

    /** Oyuncudan uzakta, güvenli, yüklü bir konum. */
    private static BlockPos spawnKonumu(ServerWorld world, BlockPos merkez) {
        for (int i = 0; i < DENEME; i++) {
            double aci = Math.random() * Math.PI * 2.0;
            double r = MIN_UZAK + Math.random() * (MAKS_UZAK - MIN_UZAK);
            int x = merkez.getX() + (int) Math.round(Math.cos(aci) * r);
            int z = merkez.getZ() + (int) Math.round(Math.sin(aci) * r);
            if (!world.isChunkLoaded(ChunkPos.toLong(x >> 4, z >> 4))) continue;

            for (int dy = -6; dy <= 6; dy++) {
                BlockPos p = new BlockPos(x, merkez.getY() + dy, z);
                if (uygunMu(world, p)) return p;
            }
        }
        return null;
    }

    private static boolean uygunMu(ServerWorld world, BlockPos ayak) {
        BlockPos bas = ayak.up();
        BlockPos zemin = ayak.down();
        BlockState sAyak = world.getBlockState(ayak);
        BlockState sBas = world.getBlockState(bas);
        BlockState sZemin = world.getBlockState(zemin);
        if (!sAyak.getCollisionShape(world, ayak).isEmpty()) return false;
        if (!sBas.getCollisionShape(world, bas).isEmpty()) return false;
        if (!sZemin.isSolidBlock(world, zemin)) return false;
        if (!sAyak.getFluidState().isEmpty()) return false;
        if (sZemin.getFluidState().isIn(FluidTags.LAVA)) return false;
        return true;
    }

    public static void sunucuKapan() {
        sayac = 0;
        ArkadasFisilti.sunucuKapan();
        ArkadasKaydi.sunucuKapan();
    }
}
