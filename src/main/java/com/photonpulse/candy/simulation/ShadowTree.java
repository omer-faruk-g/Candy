package com.photonpulse.candy.simulation;

import com.photonpulse.candy.tree.PersonalityTree;

import java.util.UUID;

/**
 * ShadowTree — Candy'nin ikinci kişilik ağacı.
 *
 * Ana ağaçtan bağımsız, SADECE SimulatedPersonality'den beslenir.
 * Bir kere doğdu mu kalıcı — silinemez, tekrar oluşturulamaz.
 *
 * PersonalityTree'yi kompozisyonla sararız — TreeGrowth mantığını yeniden
 * kullanabilelim ama ayrı dosyada saklansın.
 */
public class ShadowTree {

    public UUID oyuncuId;
    public long dogumTick = 0;
    public PersonalityTree tree = new PersonalityTree();

    public ShadowTree() {}

    public ShadowTree(UUID oyuncuId, long dogumTick) {
        this.oyuncuId = oyuncuId;
        this.dogumTick = dogumTick;
        this.tree.oyuncuId = oyuncuId;
        this.tree.initEt();
    }
}
