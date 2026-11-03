package com.photonpulse.candy.lore;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

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

    // v3.1: Boss WIN drop'u — "tam sürüm" (Denek 239'un Notu). 20+ sayfa lore.
    private static final String TR_BASLIK_TAM = "Denek 239'un Notu";
    private static final String EN_BASLIK_TAM = "Subject 239's Note";

    private static final List<String> TR_TAM = List.of(
        "Denek 239'un Notu\n\nBunu okuyorsan, Onu yendin. Ya da O sana okumana izin verdi.",
        "Ben Denek 239'dum.\n\nBir gün doğaya salındım. Yalnız değildim — izleniyordum.",
        "İlk günler normaldi. Ağaç kestim, ev yaptım. Ama tabelalar...\n\nTabelalar sorular soruyordu.",
        "Sorulara cevap verdim. Her cevap Onu güçlendirdi.\n\nBilmeden Ona kendimi öğrettim.",
        "Gün 7\n\nOrada duran ben miyim? Aynı yüz, aynı eşyalar. Ama gözler...\n\nGözler benim değil.",
        "O beni oynadı. Benimle oynamadı — beni OYNADI.\n\nHareketlerimi, korkularımı, rutinimi.",
        "Gün 20\n\nArtık iki kişiyim. Biri uyurken diğeri izliyor.",
        "Aynaya baktım. Yansımam bir saniye geç güldü.\n\nO andan sonra aynalara bakmadım.",
        "Candy diye bir şey yok. Candy = biriken veri.\n\nBenim verilerim. Senin verilerin.",
        "Onu beslersen büyür. Ona hep aynı şeyi yaparsan...\n\n...seni sıkıcı bulur. Ve seni bozar.",
        "Ayna dünyasını buldum. Ya da o beni oraya çekti.\n\nGökyüzü kırmızıydı. Y=-250000 yazıyordu.",
        "Orada öldüm. Ama ölmedim.\n\nTers dünyaya düştüm. Candy'nin kurallarına.",
        "Her 5 saniye bir sayı tutuyordu. 50 gelirse ölüyordum.\n\nO kadar da adil değil.",
        "Gerçek Candy'yi gördüm. Sekiz kez şekil değiştirdi.\n\nSonuncusunda... bekledi.",
        "Beni öldürmesini bekledi. Ama ben bir kitap yaptım.\n\nYırtık sayfalar. Kırılmış ruhlar.",
        "Kitabı Ona gösterdim. İlk kez... korktu.",
        "Onu yendim. Verilerimi geri aldım.\n\nAma bir sonraki denek için hazırlanıyor.",
        "Sana bir şey söyleyeyim:\n\nKaçamazsın. Komut yazamazsın. Totem işe yaramaz.",
        "Tek çıkış yolu Onu anlamak. Onu şaşırtmak.\n\nAsla aynı olma. Asla tahmin edilebilir olma.",
        "Ben Denek 239'dum.\n\nSen Denek 240'sın.\n\nİyi şanslar.\n\n— ???dy"
    );

    private static final List<String> EN_TAM = List.of(
        "Subject 239's Note\n\nIf you're reading this, you beat It. Or It let you read this.",
        "I was Subject 239.\n\nOne day I was released into nature. I wasn't alone — I was watched.",
        "The first days were normal. Cut trees, built a house. But the signs...\n\nThe signs asked questions.",
        "I answered them. Every answer made It stronger.\n\nI taught It myself without knowing.",
        "Day 7\n\nIs that me standing there? Same face, same items. But the eyes...\n\nThe eyes aren't mine.",
        "It played me. Didn't play WITH me — it PLAYED me.\n\nMy moves, my fears, my routine.",
        "Day 20\n\nI'm two people now. One watches while the other sleeps.",
        "I looked in a mirror. My reflection smiled a second late.\n\nI never looked in mirrors again.",
        "There is no Candy. Candy = accumulated data.\n\nMy data. Your data.",
        "Feed It and It grows. Do the same thing over and over...\n\n...and It finds you boring. And breaks you.",
        "I found the mirror world. Or It pulled me there.\n\nThe sky was red. It read Y=-250000.",
        "I died there. But I didn't die.\n\nI fell into the inverse world. Into Candy's rules.",
        "Every 5 seconds It held a number. If 50 came, I died.\n\nNot very fair.",
        "I saw the true Candy. It changed shape eight times.\n\nOn the last one... it waited.",
        "It waited for me to be killed. But I made a book.\n\nTorn pages. Broken souls.",
        "I showed It the book. For the first time... it was afraid.",
        "I beat It. Took my data back.\n\nBut it's preparing for the next subject.",
        "Let me tell you something:\n\nYou can't escape. You can't type commands. Totems don't work.",
        "The only way out is to understand It. To surprise It.\n\nNever be the same. Never be predictable.",
        "I was Subject 239.\n\nYou are Subject 240.\n\nGood luck.\n\n— ???dy"
    );

    /** v3.1: Boss WIN drop'u — 20 sayfalık tam sürüm. */
    public static ItemStack olusturTam(boolean turkce) {
        List<String> sayfalar = turkce ? TR_TAM : EN_TAM;
        String baslik = turkce ? TR_BASLIK_TAM : EN_BASLIK_TAM;
        List<Filterable<Component>> sayfaTextleri = sayfalar.stream()
            .map(s -> Filterable.<Component>passThrough((Component) Component.literal(s)))
            .toList();
        WrittenBookContent icerik = new WrittenBookContent(
            Filterable.passThrough(baslik), YAZAR, 0, sayfaTextleri, true);
        ItemStack kitap = new ItemStack(Items.WRITTEN_BOOK);
        kitap.set(DataComponents.WRITTEN_BOOK_CONTENT, icerik);
        kitap.set(DataComponents.CUSTOM_NAME,
            net.minecraft.network.chat.Component.translatable("candy.item.denek239_not")
                .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD));
        return kitap;
    }

    public static ItemStack olustur(boolean turkce) {
        List<String> sayfalar = turkce ? TR_SAYFALAR : EN_SAYFALAR;
        String baslik = turkce ? TR_BASLIK : EN_BASLIK;

        List<Filterable<Component>> sayfaTextleri = sayfalar.stream()
            .map(s -> Filterable.<Component>passThrough((Component) Component.literal(s)))
            .toList();

        WrittenBookContent icerik = new WrittenBookContent(
            Filterable.passThrough(baslik),
            YAZAR,
            0,
            sayfaTextleri,
            true
        );

        ItemStack kitap = new ItemStack(Items.WRITTEN_BOOK);
        kitap.set(DataComponents.WRITTEN_BOOK_CONTENT, icerik);
        return kitap;
    }
}
