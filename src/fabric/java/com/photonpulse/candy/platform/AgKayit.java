package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Payload tipi kaydi — Fabric uygulamasi.
 *
 * Cagri bicimi Candy'nin kaynaginda zaten kullanilan bicim:
 * {@code AgKayit.playS2C().register(X.ID, X.CODEC)}.
 *
 * Fabric'te kayit ANINDA gerceklesir; bu sinif yalnizca ad degisimi.
 * NeoForge tarafi ayni cagriyi kuyruga alir, bkz. src/neoforge/.../AgKayit.
 */
public final class AgKayit {

    private AgKayit() {}

    public static Yon playS2C() { return Yon.S2C; }
    public static Yon playC2S() { return Yon.C2S; }

    public enum Yon {
        S2C, C2S;

        public <T extends CustomPacketPayload> void register(
                CustomPacketPayload.Type<T> tip,
                StreamCodec<? super RegistryFriendlyByteBuf, T> kodek) {
            if (this == S2C) {
                PayloadTypeRegistry.playS2C().register(tip, kodek);
            } else {
                PayloadTypeRegistry.playC2S().register(tip, kodek);
            }
        }
    }
}
