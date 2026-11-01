package com.photonpulse.candy.lore;

import com.photonpulse.candy.Candy;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
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
            String path = key.location().getPath();
            if (!path.startsWith("chests/")) return;

            LootPool.Builder havuz = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(SANDIK_IHTIMAL));

            for (JournalTexts.Journal j : JournalTexts.HEPSI) {
                havuz.add(
                    LootItem.lootTableItem(Items.WRITTEN_BOOK)
                        .apply(SetComponentsFunction.setComponent(
                            DataComponents.WRITTEN_BOOK_CONTENT,
                            kitapIcerik(j)))
                );
            }

            tableBuilder.pool(havuz.build());
        });
        Candy.LOG.info("[Candy] JournalLootInjector aktif — sandıklara %10 ihtimalle günlük");
    }

    private static WrittenBookContent kitapIcerik(JournalTexts.Journal j) {
        // Translation key'lerini translatable Text'lere çevir — client kendi dilinde renderler.
        List<Filterable<Component>> sayfalar = j.sayfaKeys.stream()
            .map(key -> Filterable.<Component>passThrough(Component.translatable(key)))
            .toList();
        // Kitap başlığı String — Text.translatable().getString() ile server-side lang'e resolve et.
        // (Kitap title'ı String, translation key değil — Minecraft protokolü böyle.)
        String baslikStr = Component.translatable(j.baslikKey).getString();
        String yazarStr = Component.translatable(j.yazarKey).getString();
        return new WrittenBookContent(
            Filterable.passThrough(baslikStr),
            yazarStr,
            0,
            sayfalar,
            true
        );
    }
}
