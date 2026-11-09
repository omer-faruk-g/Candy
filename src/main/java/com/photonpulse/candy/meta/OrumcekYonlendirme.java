package com.photonpulse.candy.meta;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.phys.AABB;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * OrumcekYonlendirme (v3.0) — Spider-Man göndermesi.
 *
 * Örümcekler (normal + mağara) bazen player'a saldırmayı bırakıp BAŞKA bir hedefe döner.
 * Yakındaki tüm örümcekler AYNI hedefe yönlendirilir (senkron = daha dikkat çekici).
 * "Örümcekler başka tarafa saldırıyor" — anlayan için gönderme, anlamayan için tuhaflık.
 *
 * Şartlar: kurulum tamam + her 1 dk kontrol + 30 sn cooldown + yakında örümcek & alternatif hedef.
 */
public class OrumcekYonlendirme {

    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");
    private static final int KONTROL_ARALIGI = 1200;                 // 1 dk
    private static final long COOLDOWN = DEBUG ? 200L : 600L;        // debug 10sn / normal 30 sn
    private static final double YARICAP = 24.0;
    private static final double IHTIMAL = DEBUG ? 1.0 : 0.35;        // "bazen"

    // Yönlendirme ne kadar sürsün — bu süre boyunca hedef her tick yeniden uygulanır.
    // (Vanilla ActiveTargetGoal aksi halde 1 tick sonra hedefi player'a geri çevirir.)
    private static final int SURE_TICK = 200; // 10 sn

    private static final Random RNG = new Random();
    private static final Map<UUID, Long> sonTetik = new HashMap<>();

    /** Aktif yönlendirmeler: örümcek UUID → [hedef UUID, bitiş tick]. */
    private record Yonlendirme(UUID hedefId, long bitisTick) {}
    private static final Map<UUID, Yonlendirme> aktifYonlendirmeler = new HashMap<>();

    public static void register() {
        // Her tick: aktif yönlendirmelerin hedefini yeniden uygula (goal geri almasın)
        SunucuTick.END_SERVER_TICK.register(server -> {
            if (aktifYonlendirmeler.isEmpty()) return;
            long simdi = server.overworld().getGameTime();
            var iter = aktifYonlendirmeler.entrySet().iterator();
            while (iter.hasNext()) {
                var e = iter.next();
                if (simdi >= e.getValue().bitisTick()) { iter.remove(); continue; }
                boolean bulundu = false;
                for (ServerLevel w : server.getAllLevels()) {
                    var ent = w.getEntity(e.getKey());
                    if (!(ent instanceof Spider orumcek) || !orumcek.isAlive()) continue;
                    var hedefEnt = w.getEntity(e.getValue().hedefId());
                    if (hedefEnt instanceof LivingEntity hedef && hedef.isAlive()) {
                        // Sadece goal hedefi geri çevirdiyse yeniden uygula
                        if (orumcek.getTarget() != hedef) orumcek.setTarget(hedef);
                        bulundu = true;
                    }
                    break;
                }
                if (!bulundu) iter.remove(); // örümcek/hedef öldü veya kayboldu
            }
        });

        SunucuTick.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % KONTROL_ARALIGI != 0) return;
            long simdi = server.overworld().getGameTime();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;

                if (tp.bossOldurulmeTick > 0) continue; // WIN 30dk sessizlik
                Long son = sonTetik.get(player.getUUID());
                long cd = tp.badEndingAktif ? COOLDOWN / 2 : COOLDOWN; // BAD ENDING 2x
                if (son != null && simdi - son < cd) continue;
                if (RNG.nextDouble() >= IHTIMAL) continue;

                ServerLevel world = (ServerLevel) player.level();
                AABB box = new AABB(player.blockPosition()).inflate(YARICAP);

                // Yakındaki örümcekler (SpiderEntity + CaveSpiderEntity alt sınıf)
                List<Spider> orumcekler = world.getEntitiesOfClass(
                    Spider.class, box, s -> s.isAlive());
                if (orumcekler.isEmpty()) continue;

                // Alternatif hedefler: player HARİÇ, örümcekler hariç, canlı mob'lar
                List<Mob> hedefler = world.getEntitiesOfClass(Mob.class, box,
                    m -> m.isAlive() && !(m instanceof Spider));
                if (hedefler.isEmpty()) continue;

                // Hepsi AYNI rastgele hedefe saldırsın (senkron)
                LivingEntity yeniHedef = hedefler.get(RNG.nextInt(hedefler.size()));
                long bitis = simdi + SURE_TICK;
                int yonlendirilen = 0;
                for (Spider orumcek : orumcekler) {
                    orumcek.setTarget(yeniHedef);
                    // Süre boyunca her tick yeniden uygulanacak (goal geri almasın)
                    aktifYonlendirmeler.put(orumcek.getUUID(),
                        new Yonlendirme(yeniHedef.getUUID(), bitis));
                    yonlendirilen++;
                }

                sonTetik.put(player.getUUID(), simdi);
                Candy.LOG.info("[Candy] OrumcekYonlendirme[{}] — {} örümcek → {} (başka tarafa)",
                    player.getName().getString(), yonlendirilen,
                    yeniHedef.getType().getDescription().getString());
            }
        });

        Candy.LOG.info("[Candy] OrumcekYonlendirme aktif — {} (v3.0)",
            DEBUG ? "DEBUG (10sn cooldown)" : "kurulum sonrası, 1 dk, 30 sn cooldown, %35");
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonTetik.remove(uuid);
    }

    public static void sunucuKapan() {
        sonTetik.clear();
        aktifYonlendirmeler.clear();
    }
}
