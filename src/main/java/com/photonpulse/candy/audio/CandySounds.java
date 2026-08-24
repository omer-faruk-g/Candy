package com.photonpulse.candy.audio;

import com.photonpulse.candy.Candy;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Custom Candy sound events — resources/assets/candy/sounds.json + sounds/*.ogg
 *   - murmur: fısıltı (FisiltiSistemi)
 *   - bhoot: dramatic hayalet ambient — MASTER category
 *   - crowd: kalabalık uğultu — -35dB attenuated
 */
public class CandySounds {

    public static final Identifier MURMUR_ID = Identifier.of("candy", "murmur");
    public static final SoundEvent MURMUR = SoundEvent.of(MURMUR_ID);

    public static final Identifier BHOOT_ID = Identifier.of("candy", "bhoot");
    public static final SoundEvent BHOOT = SoundEvent.of(BHOOT_ID);

    public static final Identifier CROWD_ID = Identifier.of("candy", "crowd");
    public static final SoundEvent CROWD = SoundEvent.of(CROWD_ID);

    public static final Identifier LAUGH_ID = Identifier.of("candy", "laugh");
    public static final SoundEvent LAUGH = SoundEvent.of(LAUGH_ID);

    // v3.0 Phase 3.13: Yatak bekçisi kabus sesi
    public static final Identifier PANIC_FEAR_ID = Identifier.of("candy", "panic_fear");
    public static final SoundEvent PANIC_FEAR = SoundEvent.of(PANIC_FEAR_ID);

    public static void register() {
        Registry.register(Registries.SOUND_EVENT, MURMUR_ID, MURMUR);
        Registry.register(Registries.SOUND_EVENT, BHOOT_ID, BHOOT);
        Registry.register(Registries.SOUND_EVENT, CROWD_ID, CROWD);
        Registry.register(Registries.SOUND_EVENT, LAUGH_ID, LAUGH);
        Registry.register(Registries.SOUND_EVENT, PANIC_FEAR_ID, PANIC_FEAR);
        Candy.LOG.info("[Candy] custom sound events kayıtlı: murmur, bhoot, crowd, laugh, panic_fear");
    }
}
