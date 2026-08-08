package com.photonpulse.candy.hastalik;

/**
 * 8 psikolojik hastalık. Sadece bir tanesi aynı anda aktif olabilir.
 * Priority order = hesaplama order (üstteki daha güçlü tetikleyici).
 */
public enum PsikolojikHastalik {
    panik,       // korku ≥ 85
    sizofreni,   // kotuluk ≥ 80 + korku ≥ 80
    kisilik,     // kotuluk ≥ 90
    depresyon,   // kotuluk ≥ 60, korku < 40
    stres,       // kotuluk ≥ 50 + korku ≥ 50
    anksiyete,   // korku ≥ 60, kotuluk < 30
    bipolar,     // gündönümü — 5% rastgele
    okb;         // özel — auto trigger yok

    public String key() {
        return name();
    }
}
