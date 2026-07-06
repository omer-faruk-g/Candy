package com.photonpulse.candy.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TreeNode — Candy'nin kişilik ağacındaki tek bir dal.
 *
 * Sabit varyantlar yok. Her node kendi trait'iyle var oluyor,
 * oyuncunun davranışıyla besleniyor, aşırı büyürse çatallanıyor,
 * uzun süre beslenmezse hibernasyona giriyor.
 *
 * Trait vektörü: hangi TruePersonality alanına ne kadar duyarlı.
 * (kacOrani: +0.8 → kaçmaya güçlü pozitif tepki)
 */
public class TreeNode {

    // ── Kimlik ──
    public String id;                            // "izleme" veya "izleme.dal_3"
    public String isim;                          // insan okuyabilir
    public String parentId;                      // null = kök
    public List<String> childIds = new ArrayList<>();

    // ── Yaş / durum ──
    public long dogumTick = 0;
    public long yas = 0;                         // her tick güncellenir
    public boolean hibernation = false;
    public long hibernationTick = 0;

    // ── Aktiflik ölçütleri (0..1) ──
    public double aktiflik = 0.0;
    public double bilgi = 0.0;

    // ── Çatallanma sayacı ──
    public int cocukSayisi = 0;

    // ── Trait vektörü ──
    // key: TruePersonality alan adı, value: -1..+1 arası ağırlık
    // Negatif ağırlık = o alan yükselince aktiflik AZALIR
    public Map<String, Double> trait = new HashMap<>();

    // ── Beslenme geçmişi (uzun vadeli aktifliği yumuşatır) ──
    public double beslenmeToplam = 0.0;

    public TreeNode() {}

    public boolean kokMu() { return parentId == null; }
    public boolean aktifMi() { return !hibernation && aktiflik > 0.3; }
}
