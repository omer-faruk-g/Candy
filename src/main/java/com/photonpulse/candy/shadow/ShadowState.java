package com.photonpulse.candy.shadow;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.clone.CloneManager;
import com.photonpulse.candy.core.TruePersonality;
import net.minecraft.server.level.ServerPlayer;

/**
 * ShadowState — Candy'nin bastırılmış müdahale isteği.
 *
 * Candy oyuncuyu SADECE izlerse (aktif müdahale yok — ne klon, ne tabela),
 * içinde bir baskı birikir. Baskı 100'e ulaştığında Candy karakterinin dışına
 * çıkar: ani, yakın, oyuncunun görmediği yönde bir klon patlaması.
 *
 * Formülle önceden hesaplanamaz — ne zaman patlayacağı, ne kadar süredir
 * müdahale edilmediğiyle çarpımlı ilerler.
 */
public class ShadowState {

    // Son müdahaleden bu kadar tick geçmişse "sadece izleniyor" sayılır (~1 dk)
    private static final long INAKTIF_ESIK = 1200L;

    // Her observation tick'inde (~20t) eklenecek baskı — 100'e ulaşması ~33 dk sürer
    private static final double ARTIS = 0.05;

    private static final double PATLAMA_ESIK = 100.0;

    // Patlamadan sonra kalıcı iz: saldirOrani buna eklenir
    private static final double KALICI_IZ = 0.05;

    public static void kontrolEt(ServerPlayer player, TruePersonality tp, long simdi) {
        if (!tp.kurulumTamam) return;

        // Kurulum yeni bittiyse sonMudahaleTick henüz 0 olabilir — simdi'yi damga vur
        if (tp.sonMudahaleTick == 0) {
            tp.sonMudahaleTick = simdi;
            return;
        }

        long inaktifSure = simdi - tp.sonMudahaleTick;
        if (inaktifSure > INAKTIF_ESIK) {
            tp.bastirilmisBaski = Math.min(PATLAMA_ESIK, tp.bastirilmisBaski + ARTIS);
        }

        if (tp.bastirilmisBaski >= PATLAMA_ESIK) {
            patla(player, tp, simdi);
        }
    }

    private static void patla(ServerPlayer player, TruePersonality tp, long simdi) {
        boolean basarili = CloneManager.zorlaKlonSpawn(player, tp);
        if (!basarili) return;   // uygun konum yok — baskıyı tut, sonraki tick'te tekrar dene

        tp.bastirilmisBaski = 0.0;
        // Candy bu patlamayı "hatırlıyor" — saldırganlık kalıcı olarak hafif artar
        tp.saldirOrani = Math.min(1.0, tp.saldirOrani + KALICI_IZ);
        // Patlama bir müdahale sayılır — cooldown başlar
        tp.sonMudahaleTick = simdi;
        Candy.LOG.info("[Candy] ShadowState patladı: {} için ani klon",
            player.getName().getString());
    }
}
