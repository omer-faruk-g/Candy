package com.photonpulse.candy.sign;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

/**
 * Tabela (sign) API'sinin surume bagli kisimlari — TEK yer.
 *
 * 26.x'te olculen degisiklikler:
 *   new SignText()              -> SignText.EMPTY
 *   t.setMessage(i, c)          -> t.asMutable().setLine(i, c).asImmutable()
 *   t.getMessage(i, false)      -> t.getMessages(false).get(i)   (Component[] -> List)
 *   sign.getFrontText()/Back()  -> sign.getText(SignTextSlot.FRONT/BACK)
 *   sign.setText(t, boolean)    -> sign.setText(t, SignTextSlot.FRONT/BACK)
 *
 * Bunlar replacements kurallari ile de yazilabilirdi ama yirmi kadar capalanmis
 * metin kurali gerekirdi (cagri yerlerinin tam metnine bagli). Tek uyum sinifi
 * hem kirilgan degil hem de farkin ne oldugunu okunur birakiyor.
 */
public final class TabelaUyum {

    private TabelaUyum() {}

    /** Bos tabela metni. */
    public static SignText bos() {
        //? if >=26.3 {
        /*return SignText.EMPTY;
        */
        //?}
        //? if <26.3 {
        return new SignText();
        //?}
    }

    /** i. satiri degistirilmis YENI metin dondurur (ikisinde de deger tipi gibi davranir). */
    public static SignText satir(SignText t, int i, Component c) {
        //? if >=26.3 {
        /*return t.asMutable().setLine(i, c).asImmutable();
        */
        //?}
        //? if <26.3 {
        return t.setMessage(i, c);
        //?}
    }

    /** i. satir (filtrelenmemis). */
    public static Component mesaj(SignText t, int i) {
        //? if >=26.3 {
        /*return t.getMessages(false).get(i);
        */
        //?}
        //? if <26.3 {
        return t.getMessage(i, false);
        //?}
    }

    /** On yuz metni. */
    public static SignText onYuz(SignBlockEntity sign) {
        //? if >=26.3 {
        /*return sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
        */
        //?}
        //? if <26.3 {
        return sign.getFrontText();
        //?}
    }

    /** Arka yuz metni. */
    public static SignText arkaYuz(SignBlockEntity sign) {
        //? if >=26.3 {
        /*return sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.BACK);
        */
        //?}
        //? if <26.3 {
        return sign.getBackText();
        //?}
    }

    /** onYuze=true ise on, false ise arka yuze yazar. */
    public static void yaz(SignBlockEntity sign, SignText t, boolean onYuze) {
        //? if >=26.3 {
        /*sign.setText(t, onYuze
            ? net.minecraft.world.level.block.entity.SignTextSlot.FRONT
            : net.minecraft.world.level.block.entity.SignTextSlot.BACK);
        */
        //?}
        //? if <26.3 {
        sign.setText(t, onYuze);
        //?}
    }
}
