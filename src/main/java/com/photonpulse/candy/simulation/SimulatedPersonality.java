package com.photonpulse.candy.simulation;

import java.util.UUID;

/**
 * SimulatedPersonality — TruePersonality'nin "zıt" ikizi.
 *
 * Candy'nin "oyuncu farklı yapsaydı ne olurdu" iç simülasyonu bu nesnede birikir.
 * Gerçek TruePersonality'yi etkilemez — sadece SimulationEngine tarafından beslenir.
 * ShadowTree bir kere doğduktan sonra bu nesneden büyür.
 *
 * Neden sadece ana davranış alanları: bu bir hayal, tam bir kimlik değil.
 */
public class SimulatedPersonality {

    public UUID oyuncuId;

    // Ana hareket eğilimleri
    public double kacOrani = 0.0;
    public double saldirOrani = 0.0;
    public double donOrani = 0.0;
    public double kesfetOrani = 0.0;

    // Psikoloji
    public double aciklik = 0.5;
    public double inkarEgilimi = 0.0;
    public double kesfetmeIstegi = 0.5;
    public double panikEsigi = 0.5;

    public SimulatedPersonality() {}

    public SimulatedPersonality(UUID oyuncuId) {
        this.oyuncuId = oyuncuId;
    }
}
