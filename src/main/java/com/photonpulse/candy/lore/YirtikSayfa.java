package com.photonpulse.candy.lore;

import com.photonpulse.candy.Candy;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetComponentsLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * YirtikSayfa — sandıklarda %5 ihtimalle "Yırtık Sayfa" (paper + custom name + lore).
 *
 * Denek 239 kitabının bir sayfasının kopyası — 5 farklı varyant (her sayfa için).
 * Player 5 sayfa toplarsa kitabın parçalarını birleştirebilir (gelecek özellik).
 *
 * Şimdilik: item name = "Yırtık Sayfa (X/5)", lore = sayfanın kısaltılmış içeriği.
 */
public class YirtikSayfa {

    private static final float SANDIK_IHTIMAL = 0.05f;

    // 5 sayfa — TR versiyonu (server-side literal). MP dedicated server için de bu.
    private static final String[] SAYFA_ISIMLERI = {
        "Yırtık Sayfa (1/5)",
        "Yırtık Sayfa (2/5)",
        "Yırtık Sayfa (3/5)",
        "Yırtık Sayfa (4/5)",
        "Yırtık Sayfa (5/5)"
    };
    private static final String[] SAYFA_METINLERI = {
        "Denek 239 doğaya salındı. Şüpheli bir hareket yok.",
        "Gün 7 — Artık ordaki ben miyim emin değilim.",
        "Gün 20 — Artık ben 2 kişiyim.",
        "Kendimden kaçmak, kendime vurmak çok garip.",
        "Gün ??? — Ondan kurtuldum. İmzalayan: ???dy"
    };

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (key == null) return;
            String path = key.getValue().getPath();
            if (!path.startsWith("chests/")) return;

            LootPool.Builder havuz = LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(1))
                .conditionally(RandomChanceLootCondition.builder(SANDIK_IHTIMAL));

            for (int i = 0; i < SAYFA_ISIMLERI.length; i++) {
                Text isim = Text.literal(SAYFA_ISIMLERI[i]).formatted(Formatting.GRAY, Formatting.ITALIC);
                Text metin = Text.literal(SAYFA_METINLERI[i]).formatted(Formatting.DARK_GRAY, Formatting.ITALIC);
                havuz.with(
                    ItemEntry.builder(Items.PAPER)
                        .apply(SetComponentsLootFunction.builder(DataComponentTypes.CUSTOM_NAME, isim))
                        .apply(SetComponentsLootFunction.builder(
                            DataComponentTypes.LORE, new LoreComponent(List.of(metin))))
                );
            }

            tableBuilder.pool(havuz.build());
        });
        Candy.LOG.info("[Candy] YirtikSayfa aktif — sandıklara %5 ihtimalle yırtık sayfa");
    }

    /**
     * Manual olarak yırtık sayfa oluştur (test/debug için).
     */
    public static ItemStack olustur(int sayfaIdx) {
        int idx = Math.max(0, Math.min(4, sayfaIdx));
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(SAYFA_ISIMLERI[idx]).formatted(Formatting.GRAY, Formatting.ITALIC));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal(SAYFA_METINLERI[idx]).formatted(Formatting.DARK_GRAY, Formatting.ITALIC)
        )));
        return stack;
    }
}
