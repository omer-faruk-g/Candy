package com.photonpulse.candy.lore;

import com.photonpulse.candy.Candy;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
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
            String path = key.location().getPath();
            if (!path.startsWith("chests/")) return;

            LootPool.Builder havuz = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(SANDIK_IHTIMAL));

            for (int i = 0; i < SAYFA_ISIMLERI.length; i++) {
                Component isim = Component.literal(SAYFA_ISIMLERI[i]).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
                Component metin = Component.literal(SAYFA_METINLERI[i]).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
                havuz.add(
                    LootItem.lootTableItem(Items.PAPER)
                        .apply(SetComponentsFunction.setComponent(DataComponents.CUSTOM_NAME, isim))
                        .apply(SetComponentsFunction.setComponent(
                            DataComponents.LORE, new ItemLore(List.of(metin))))
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
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal(SAYFA_ISIMLERI[idx]).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal(SAYFA_METINLERI[idx]).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
        )));
        return stack;
    }
}
