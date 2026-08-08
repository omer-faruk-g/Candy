package com.photonpulse.candy.lore;

import com.photonpulse.candy.Candy;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetComponentsLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

import java.util.List;

/**
 * JournalLootInjector — sandık loot table'larına günlük kitabı enjekte eder.
 *
 * Sadece `chests/` yolundaki loot table'lar hedeflenir (village_weaponsmith,
 * simple_dungeon, jungle_temple, vs.). Diğer loot table'lara (mob drops,
 * fishing) dokunulmaz.
 *
 * %10 ihtimalle bir yazılı kitap ekler. Kitap içeriği JournalTexts havuzundan
 * rastgele seçilir — her günlük eşit ağırlıklı ayrı bir entry olarak eklenir,
 * yani pool tetiklendiğinde havuzdan biri düşer.
 */
public class JournalLootInjector {

    private static final float SANDIK_IHTIMAL = 0.10f;

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (key == null) return;
            String path = key.getValue().getPath();
            if (!path.startsWith("chests/")) return;

            LootPool.Builder havuz = LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(1))
                .conditionally(RandomChanceLootCondition.builder(SANDIK_IHTIMAL));

            for (JournalTexts.Journal j : JournalTexts.HEPSI) {
                havuz.with(
                    ItemEntry.builder(Items.WRITTEN_BOOK)
                        .apply(SetComponentsLootFunction.builder(
                            DataComponentTypes.WRITTEN_BOOK_CONTENT,
                            kitapIcerik(j)))
                );
            }

            tableBuilder.pool(havuz.build());
        });
        Candy.LOG.info("[Candy] JournalLootInjector aktif — sandıklara %10 ihtimalle günlük");
    }

    private static WrittenBookContentComponent kitapIcerik(JournalTexts.Journal j) {
        // Translation key'lerini translatable Text'lere çevir — client kendi dilinde renderler.
        List<RawFilteredPair<Text>> sayfalar = j.sayfaKeys.stream()
            .map(key -> RawFilteredPair.<Text>of(Text.translatable(key)))
            .toList();
        // Kitap başlığı String — Text.translatable().getString() ile server-side lang'e resolve et.
        // (Kitap title'ı String, translation key değil — Minecraft protokolü böyle.)
        String baslikStr = Text.translatable(j.baslikKey).getString();
        String yazarStr = Text.translatable(j.yazarKey).getString();
        return new WrittenBookContentComponent(
            RawFilteredPair.of(baslikStr),
            yazarStr,
            0,
            sayfalar,
            true
        );
    }
}
