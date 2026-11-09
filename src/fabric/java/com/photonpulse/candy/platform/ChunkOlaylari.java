package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.function.BiConsumer;

/**
 * Chunk olaylari — Fabric uygulamasi.
 *
 * CHUNK_GENERATE chunk basina TAM BIR KEZ ateslenir; dunya sekillendirme
 * bu garantiye dayaniyor.
 */
public final class ChunkOlaylari {

    public static final Uretim CHUNK_GENERATE = new Uretim();

    private ChunkOlaylari() {}

    public static final class Uretim {
        private Uretim() {}

        public void register(BiConsumer<ServerLevel, LevelChunk> dinleyici) {
            ServerChunkEvents.CHUNK_GENERATE.register(dinleyici::accept);
        }
    }
}
