package com.photonpulse.candy.audio;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Tek oyuncuya ses. 1.21.9'da Entity.playSoundToPlayer kaldırıldı, bu yüzden
 * paketi doğrudan o oyuncunun bağlantısına yolluyoruz: ses oyuncunun kendi
 * konumundan çalar ve başkası duymaz — eski metodun davranışı buydu.
 *
 * PlaySoundS2CPacket imzası 1.21.7 ve 1.21.11'de birebir aynı, o yüzden
 * burada sürüm ayrımına gerek yok.
 */
public final class SesGonder {

    private SesGonder() {}

    public static void tek(ServerPlayer player, SoundEvent ses,
                           SoundSource kategori, float siddet, float perde) {
        if (player == null || player.connection == null) return;
        player.connection.send(new ClientboundSoundPacket(
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ses), kategori,
            player.getX(), player.getY(), player.getZ(),
            siddet, perde, player.getRandom().nextLong()));
    }
}
