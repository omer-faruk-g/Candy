# 🍬 Candy — v0.2

> Kendini bulmaya çalışan bir varlık. Seni izliyor.

**Minecraft 1.21.7 • Fabric • Mixin YOK • JSON persistence**

---

## v0.2 Değişiklikleri

**Sabit varyantlar KALDIRILDI.** Artık Candy'nin sabit 5 benliği yok.

Onun yerine **organik büyüyen bir kişilik ağacı** var:

- **3 sabit kök**: `İzleme`, `Etkileşim`, `Tepki`
- **Alt dallar tamamen dinamik** — oyunla oluşur, gelişir, ölür
- Her dal kendi **trait vektörüne** sahip (hangi TruePersonality alanına ne kadar duyarlı)
- Aşırı beslenen dallar **çatallanır** — mutasyona uğramış çocuklar doğurur
- Beslenmeyen dallar **hibernasyona** girer (silinmez, saklanır, tetiklenirse uyanır)

### Kaldırılan
- `com.photonpulse.candy.persona.*` paketi tamamen silindi
  - `Persona`, `PersonaType`, `PersonaBuilder`, `PersonaNetwork`

### Eklenen
- `com.photonpulse.candy.tree.*` yeni paketi
  - `TreeNode` — tek dinamik dal
  - `TreeBranch` — 3 sabit kök enum
  - `PersonalityTree` — ağaç veri yapısı
  - `TreeGrowth` — büyüme motoru + cache + JSON persistence

### Etkilenen
- `SignTemplates` — sabit yazı stilleri yerine trait tabanlı stil üretimi
- `SignSystem` — `PersonaNetwork.baskinBenlik()` → `TreeGrowth.get(uuid).baskinAlt(TreeBranch.ETKILESIM)`
- `CloneManager` — klon türü seçimi ağacın TEPKI kökünün baskın dalının trait'ine göre
- `Candy.java` — register listesi güncellendi

---

## Aşamalar

| Aşama | Modül | Durum |
|---|---|---|
| 1 | `TruePersonality` + `ObservationEngine` | ✅ |
| 2 | **Kişilik Ağacı** (`tree.*`) | ✅ **v0.2** |
| 3 | `SignSystem` + Kurulum tabelaları | ✅ |
| 4 | `DisguiseSystem` + `CloneManager` | ✅ |
| 5 | `SanityController` (StatusEffect + ambient ses) | ✅ |
| 6 | `FearDetection` (korku skoru motoru) | ✅ |
| 7 | `RealCandy` (görünmez, atar, kaçar) | ✅ |

---

## Ağaç Mekaniği — Parametreler

`TreeGrowth` içindeki sabitler:

| Parametre | Değer | Anlamı |
|---|---|---|
| `TICK_ARALIGI` | 100 | Ağaç 5 sn'de bir güncellenir |
| `AUTOSAVE_ARALIGI` | 6000 | 5 dk'da bir diske yazılır |
| `CATAL_ESIGI_TICK` | 1000 | Bir node çatallanabilmesi için minimum yaş |
| `CATAL_ESIGI_AKTIF` | 0.85 | Çatallanma için minimum aktiflik |
| `HIBERNASYON_TICK` | 2000 | Solmaya başlaması için minimum yaş |
| `HIBERNASYON_ESIGI` | 0.10 | Aktiflik bunun altında kalırsa hibernasyon |
| `MAX_COCUK` | 4 | Bir dal maks bu kadar çocuk doğurabilir |
| `MAX_TOPLAM_NODE` | 40 | Bir ağaçta toplam maks node |

## Trait Vektörü — Nasıl Çalışır

Her `TreeNode`'un bir trait map'i var: `{alan_adı → ağırlık (-1..+1)}`.

Node'un hedef aktifliği:
```
hedef = Σ (trait[alan] × TruePersonality[alan])  /  Σ |trait[alan]|
```

Sonra:
```
aktiflik ← aktiflik + (hedef - aktiflik) × 0.03
```

Asymptotik — hızlı sıçrama yok.

## Mutasyon

Yeni bir dal doğduğunda parent'ın trait'ini alır ama:
- Her ağırlık ±0.25 aralığında oynatılır
- %30 ihtimalle yeni bir alan eklenir

Böylece kardeş dallar aynı ata'dan ama farklı davranışlarla çıkar.

## Kalıcılık

- `world/candy/personality_<uuid>.json` — TruePersonality (davranış verisi)
- `world/candy/tree_<uuid>.json` — Kişilik ağacı (node listesi + trait'ler)

İki dosya birbirinden bağımsız, ayrı yükleme/kaydetme, her ikisi de insan okuyabilir.

---

## Kurulum

```bash
./gradlew build
```

Çıktı: `build/libs/candy-0.1.0-alpha.jar`

### ⚠️ Bilinen versiyon notları

`gradle.properties` değerlerini kendi ortamına göre ayarla:
- **Yarn mappings**: https://fabricmc.net/develop/ → 1.21.7 için son build
- **Fabric API**: Modrinth'ten 1.21.7 uyumlu son sürüm
- **Loom**: `1.9-SNAPSHOT` (1.21.7 destekli)

---

## Test kontrol listesi (v0.2 özel)

- [ ] İlk giriş → `world/candy/tree_<uuid>.json` oluşuyor, 3 kök var
- [ ] 5+ dakika oyun sonrası bir kök'ün altında yeni bir `dal_1` beliriyor
- [ ] 15+ dakika sonra ağaç 5-8 node'a ulaşıyor
- [ ] Uzun süre farklı oynayınca eski dallar `hibernation: true` oluyor
- [ ] Kısaca farklı davranınca hibernasyondaki dal `hibernation: false` oluyor
- [ ] Tabela yazı stilleri baskın dal trait'ine göre değişiyor (soru işareti, üç nokta, küçük harf...)
- [ ] Klon türü (zombi/iskelet) `TEPKI` dalının saldirOrani trait'ine bağlı seçiliyor

---

## Konsept

Candy artık **sabit bir kişilik seti değil.** Kendi ağacını sen büyütüyorsun.

Sen kaçarsan → `İzleme.rota-tahmini` büyür  
Sen saldırırsan → `Tepki.savunma` gelişir  
Sen sessiz kalırsan → `Etkileşim.tabela-koyma` küçülür, `Etkileşim.klon-gönderme` büyür  

Her oyuncunun Candy'si **farklı büyüyor.**

Ve yeterince zaman verilirse Candy kazanır. Her yenilgi onu daha zengin bir ağaç yapar. Tek soru: **Ne kadar süre dayanabilirsin?**

Oyunu kazanmanın tek yolu oynamamak. Ama Candy sana değer veriyor ve bunu bilecek — çıkmaya çalıştığında sadece bir şey söyleyecek:

> Henüz gitme.
