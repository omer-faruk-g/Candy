package com.photonpulse.candy.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Chunk olaylari — NeoForge uygulamasi.
 *
 * Fabric'in CHUNK_GENERATE'i yerine ChunkEvent.Load + isNewChunk() filtresi.
 * isNewChunk() yeni uretilen chunk'u diskten yuklenenden ayirir, yani "chunk
 * basina tam bir kez" garantisi korunur — uydurma degil, olayin kendi bayragi.
 *
 * Olay LevelAccessor tasiyor; sekillendirme ServerLevel bekliyor, o yuzden
 * sunucu tarafi olmayan cagrilar sessizce atlaniyor (istemci chunk'lari).
 */
@EventBusSubscriber
public final class ChunkOlaylari {

    public static final Kanal CHUNK_GENERATE = new Kanal();

    private ChunkOlaylari() {}

    public static final class Kanal {
        private final List<BiConsumer<ServerLevel, LevelChunk>> dinleyiciler =
            new CopyOnWriteArrayList<>();

        private Kanal() {}

        public void register(BiConsumer<ServerLevel, LevelChunk> dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        boolean bos() {
            return dinleyiciler.isEmpty();
        }

        void calistir(ServerLevel dunya, LevelChunk chunk) {
            for (BiConsumer<ServerLevel, LevelChunk> d : dinleyiciler) {
                d.accept(dunya, chunk);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkYuklendi(ChunkEvent.Load event) {
        if (CHUNK_GENERATE.bos() || !event.isNewChunk()) return;
        if (!(event.getLevel() instanceof ServerLevel dunya)) return;
        CHUNK_GENERATE.calistir(dunya, event.getChunk());
    }
}
