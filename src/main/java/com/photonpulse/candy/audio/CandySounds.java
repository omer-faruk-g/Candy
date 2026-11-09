package com.photonpulse.candy.audio;

import com.photonpulse.candy.Candy;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Custom Candy sound events — resources/assets/candy/sounds.json + sounds/*.ogg
 *   - murmur: fısıltı (FisiltiSistemi)
 *   - bhoot: dramatic hayalet ambient — MASTER category
 *   - crowd: kalabalık uğultu — -35dB attenuated
 */
public class CandySounds {

    public static final ResourceLocation MURMUR_ID = ResourceLocation.fromNamespaceAndPath("candy", "murmur");
    public static final SoundEvent MURMUR = SoundEvent.createVariableRangeEvent(MURMUR_ID);

    public static final ResourceLocation BHOOT_ID = ResourceLocation.fromNamespaceAndPath("candy", "bhoot");
    public static final SoundEvent BHOOT = SoundEvent.createVariableRangeEvent(BHOOT_ID);

    public static final ResourceLocation CROWD_ID = ResourceLocation.fromNamespaceAndPath("candy", "crowd");
    public static final SoundEvent CROWD = SoundEvent.createVariableRangeEvent(CROWD_ID);

    public static final ResourceLocation LAUGH_ID = ResourceLocation.fromNamespaceAndPath("candy", "laugh");
    public static final SoundEvent LAUGH = SoundEvent.createVariableRangeEvent(LAUGH_ID);

    // v3.0 Phase 3.13: Yatak bekçisi kabus sesi
    public static final ResourceLocation PANIC_FEAR_ID = ResourceLocation.fromNamespaceAndPath("candy", "panic_fear");
    public static final SoundEvent PANIC_FEAR = SoundEvent.createVariableRangeEvent(PANIC_FEAR_ID);

    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, MURMUR_ID, MURMUR);
        Registry.register(BuiltInRegistries.SOUND_EVENT, BHOOT_ID, BHOOT);
        Registry.register(BuiltInRegistries.SOUND_EVENT, CROWD_ID, CROWD);
        Registry.register(BuiltInRegistries.SOUND_EVENT, LAUGH_ID, LAUGH);
        Registry.register(BuiltInRegistries.SOUND_EVENT, PANIC_FEAR_ID, PANIC_FEAR);
        Candy.LOG.info("[Candy] custom sound events kayıtlı: murmur, bhoot, crowd, laugh, panic_fear");
    }
}
