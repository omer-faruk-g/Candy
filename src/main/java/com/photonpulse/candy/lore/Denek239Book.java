package com.photonpulse.candy.lore;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Denek239Book — oyuncuya ilk join'de verilen imzalı kitap.
 * TR ve EN olmak üzere iki nüsha, envanterin 0 ve 1. slotlarına konur.
 *
 * İçerik: 5 sayfa — "???dy" imzalı bir gözlem günlüğü.
 * Denek 239 = oyuncu. Yazarı belirsiz ama "Candy"nin kendisi.
 */
public class Denek239Book {

    private static final String TR_BASLIK = "Denek 239";
    private static final String EN_BASLIK = "Subject 239";
    private static final String YAZAR = "???dy";

    private static final List<String> TR_SAYFALAR = List.of(
        "Denek 239\n\nDoğaya salındı. Yaptıklarını yakından takip edeceğim.\n\nŞuan şüpheli bir hareket yok.",

        "Gün 7\n\nArtık ordaki ben miyim emin değilim. Şuan kim gerçek, o mu benim yoksa ben mi ben?\n\nRüyalarımda bile görüyorum.",

        "Gün 20\n\nArtık ben 2 kişiyim.",

        "Günü artık bilmiyorum ama artık kendimden korkuyorum.\n\nKendimden kaçmak, kendime vurmak çok garip.\n\nAkıl sağlığımı kaybetmemeliyim.",

        "Gün ???\n\nOndan artık kurtuldum, tüm verileri aldım. Artık diğerlerine...\n\n(Günlük bitiş)\n\nİmzalayan:\n???dy"
    );

    private static final List<String> EN_SAYFALAR = List.of(
        "Subject 239\n\nReleased into nature. I will observe their actions closely.\n\nNo suspicious activity yet.",

        "Day 7\n\nI'm no longer sure if that's me over there. Who is real now — them or me?\n\nI see it even in my dreams.",

        "Day 20\n\nI am two people now.",

        "I no longer know the day but I fear myself now.\n\nRunning from myself, hitting myself is strange.\n\nI mustn't lose my sanity.",

        "Day ???\n\nI'm rid of them now, took all the data. On to the others...\n\n(End of log)\n\nSigned:\n???dy"
    );

    public static ItemStack olustur(boolean turkce) {
        List<String> sayfalar = turkce ? TR_SAYFALAR : EN_SAYFALAR;
        String baslik = turkce ? TR_BASLIK : EN_BASLIK;

        List<RawFilteredPair<Text>> sayfaTextleri = sayfalar.stream()
            .map(s -> RawFilteredPair.<Text>of((Text) Text.literal(s)))
            .toList();

        WrittenBookContentComponent icerik = new WrittenBookContentComponent(
            RawFilteredPair.of(baslik),
            YAZAR,
            0,
            sayfaTextleri,
            true
        );

        ItemStack kitap = new ItemStack(Items.WRITTEN_BOOK);
        kitap.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, icerik);
        return kitap;
    }
}
