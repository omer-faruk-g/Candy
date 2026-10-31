package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * KurulDunya — kurulun 5 dakikada bir "seni oraya atayım mı?" diye tarttığı 8 dünya.
 *
 * <p>Mirror / Anti-Mirror / Ayna bu listede DEĞİL: onların kendi tetikleyicileri var
 * ({@link MirrorGirisi}, {@link AynaSistemi}). Bu 8 dünya ise {@link DunyaKurulu} tarafından
 * ölçülen beyin durumundan seçilir; hiçbiri script'li değil, karar komiteden gelir.</p>
 *
 * <h3>ÜRETİM STRATEJİSİ</h3>
 * Hiçbirinde özel {@code ChunkGenerator} yok (kod-üstü risk + bakım kabusu). Boyut JSON'u
 * basit bir vanilla üreteç kullanır (çoğunda {@code minecraft:flat} boşluk, Kör'de
 * {@code minecraft:noise} overworld) ve {@link DunyaSekillendirici} deseni
 * {@code ServerChunkEvents.CHUNK_GENERATE} anında (chunk başına BİR kez) damgalar.
 *
 * <p>Her dünyanın bir {@link Sekil} modu var — şekillendirici bu moda göre chunk'u işler.</p>
 */
public enum KurulDunya {

    /** Far Lands — süngerimsi, keskin kenarlı dev duvarlar (gerçek MC anomalisinin taklidi). */
    FARLANDS("far_lands", Sekil.FARLANDS),
    /** Farther Lands — Far Lands'in de çürümüşü: daha da gerilmiş, akışkanlaşmış arazi. */
    FARTHERLANDS("farther_lands", Sekil.FARTHERLANDS),
    /** Corner Far Lands — biome geçişi çöktüğü için dümdüz, üst üste yığılı katmanlar. */
    CORNERLANDS("corner_lands", Sekil.CORNERLANDS),
    /** Sky Grid — boşlukta asılı 3B blok ızgarası (her eksende 4'te 1 blok). */
    SKYGRID("sky_grid", Sekil.SKYGRID),
    /** Koridor — dümdüz koridorlar + sağlı sollu dallanan yan koridorlar. */
    KORIDOR("koridor", Sekil.KORIDOR),
    /** Klon — tek şablon chunk; ne koyarsan HER chunk'a yansır (bireyselliğin yokluğu). */
    KLON("klon", Sekil.KLON),
    /** ME — düz dünya, tüm blok dokuları oyuncunun YÜZÜ (client tarafı). */
    ME("me", Sekil.ME),
    /** Kör — normal dünya üretilir ama her blok barrier: hiçbir şey göremezsin. */
    KOR("kor", Sekil.KOR);

    /** Şekillendiricinin chunk'a nasıl davranacağı. */
    public enum Sekil { FARLANDS, FARTHERLANDS, CORNERLANDS, SKYGRID, KORIDOR, KLON, ME, KOR }

    public final String id;
    public final Sekil sekil;
    public final RegistryKey<World> anahtar;

    KurulDunya(String id, Sekil sekil) {
        this.id = id;
        this.sekil = sekil;
        this.anahtar = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(Candy.MOD_ID, id));
    }

    /** Bir dünya anahtarının hangi KurulDunya olduğunu bulur (yoksa null). */
    public static KurulDunya bul(RegistryKey<World> anahtar) {
        if (anahtar == null) return null;
        for (KurulDunya d : values()) if (d.anahtar.equals(anahtar)) return d;
        return null;
    }
}
