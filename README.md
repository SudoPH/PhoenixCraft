# PhoenixCraft

> **Kategorize crafting GUI for [PhoenixFish](https://github.com/) — olta ve yem üretim sistemi.**
>
> ⚠️ **Bu eklenti şu an ERKEN AŞAMADA (early stage).** Temel üretim sistemi çalışıyor ama henüz eksik özellikler var, test sürümünde. Hatalar görürseniz lütfen issue açın.

---

## 📋 Nedir?

PhoenixCraft, PhoenixFish balıkçılık eklentisi için bağımsız bir **crafting (üretim) GUI** modülüdür. Oyuncular kategorize edilmiş bir menü üzerinden özel oltalarını ve yemlerini üretebilir.

- **Oltalar**: 8 tier'lık ilerleme (Hardwood → Iron → Gold → Diamond → Netherite → Ocean → Legendary → Cosmic)
- **Yemler**: 13 farklı yem, Common'dan Legendary'ye kadar (worm, bread_crust, minnow_bait, seaweed_wrap, glowing_bait, bug_paste, enchanted_dough, bloodied_flesh, spider_silk, ocean_essence, golden_roe, void_leech, lunar_moth)
- **Crafting Level/XP sistemi**: Üretim yaptıkça XP kazan, level atla, daha gelişmiş tarifler aç
- **Tamamen veri-ayrık mimari**: PhoenixCraft sadece GUI ve etkileşim sağlar, tüm iş mantığı PhoenixFish'te kalır

---

## 🚀 Kurulum

### Gereksinimler

- **Paper 1.21+** (Java 21)
- **PhoenixFish 1.3.1+** — zorunlu bağımlılık (`depend: [PhoenixFish]`)

### Adımlar

1. `PhoenixFish-1.3.0.jar`'ı `plugins/` klasörüne koy
2. `PhoenixCraft-1.0.0.jar`'ı `plugins/` klasörüne koy
3. Server'ı başlat
4. Konsolda şu satırları görmelisin:

```
[PhoenixFish] PhoenixFish başarıyla etkinleştirildi!
[PhoenixCraft] PhoenixCraft v1.0.0 etkinleştirildi.
[PhoenixCraft] Bağlı PhoenixFishAPI: com.phoenix.fish.api.PhoenixFishAPI
```

5. Oyuna gir ve `/pcraft` yaz

---

## 🎮 Kullanım

### Komutlar

| Komut | Açıklama |
|---|---|
| `/pcraft` | Ana menüyü açar (alias: `/pc`) |
| `/pcraft rods` | Direkt oltalar kategorisi |
| `/pcraft baits` | Direkt yemler kategorisi |
| `/pcraft help` | Yardım mesajı |

### Yetkiler

| Yetki | Varsayılan | Açıklama |
|---|---|---|
| `phoenixcraft.use` | herkes | `/pcraft` komutunu kullanma |
| `phoenixcraft.admin` | op | Yönetici komutları |

### GUI Akışı

```
/pcraft
  └── ANA MENÜ (27 slot)
       ├─ 🎣 OLTALAR → Kategori menüsü
       ├─ ℹ️ BİLGİ (level + XP gösterir)
       ├─ 🪱 YEMLER → Kategori menüsü
       └─ ❌ KAPAT
              ↓
       KATEGORİ MENÜSÜ (54 slot, sayfalı)
       ├─ Tarif ikonları (lore'da malzeme listesi)
       ├─ « Önceki Sayfa
       ├─ ANA MENÜ
       └─ Sonraki Sayfa »
              ↓
       TARİF DETAYI (27 slot)
       ├─ 📖 Gerekli Malzemeler (book)
       ├─ 🎣 Üretilecek eşya (preview)
       ├─ ✔ ÜRET (yeşil = hazır / kırmızı = değil)
       └─ « Geri Dön
```

### Üretim Akışı

1. Detay ekranında "ÜRET" butonu yeşilse tıkla
2. Malzemeler envanterden otomatik tüketilir
3. Üretilen eşya envantere eklenir (taşarsa yere düşer)
4. Crafting XP kazanılır, level-up gelirse otomatik işlenir

---

## 🏗️ Mimari

```
┌─────────────────────────────────┐    ┌──────────────────────────────────┐
│         PhoenixFish (zorunlu)   │    │      PhoenixCraft (bu eklenti)   │
│  ─────────────────────────────  │    │  ──────────────────────────────  │
│  • custom_recipes.yml (tarifler)│    │  • /pcraft komutu                │
│  • rods.yml, baits.yml          │    │  • Kategorize Crafting GUI       │
│  • FishingData (craftLevel/XP)  │    │  • Oltalar / Yemler menüleri     │
│  • BaitManager, ItemFixer       │    │  • Üretim butonu + malzeme listesi│
│  • PhoenixFishAPI (servis)      │◄───┤  • PhoenixFishAPI consumer       │
│  • CraftingManager (iş mantığı) │    │  • CraftingListener (click/drag) │
└─────────────────────────────────┘    └──────────────────────────────────┘
            ▲                                      │
            │   Bukkit ServicesManager             │
            └──────────────────────────────────────┘
```

**Tasarım prensibi**: PhoenixCraft **sadece GUI ve etkileşim** sağlar. Tüm iş mantığı (tarif yükleme, malzeme kontrolü, üretim, XP/level) PhoenixFish'te kalır. Bu sayede:

- ✅ Tek doğruluk kaynağı (single source of truth)
- ✅ Database/cache tek yerde
- ✅ Reload tek komutla (`/pf reload`)
- ✅ Başka eklentiler de aynı API'yi kullanabilir

---

## 📦 Proje Yapısı

```
PhoenixCraft/
├── pom.xml
└── src/main/
    ├── java/com/phoenix/craft/
    │   ├── PhoenixCraft.java          # Ana sınıf
    │   ├── command/
    │   │   ├── CraftCommand.java      # /pcraft executor
    │   │   └── CraftTabCompleter.java # Tab completer
    │   ├── gui/
    │   │   ├── CraftingGUIHolder.java # InventoryHolder marker
    │   │   └── CraftingGUIManager.java# Ekran oluşturma + craft logic
    │   └── listener/
    │       └── CraftingListener.java  # InventoryClick/Drag events
    └── resources/
        └── plugin.yml
```

---

## ⚠️ Erken Aşama Uyarısı

Bu eklenti **aktif geliştirme aşamasında** ve production'da kullanımdan önce dikkatli test edilmesi önerilir.

### Bilinen Eksiklikler

- ❌ Shift-tık ile toplu üretim yok (her tıklamada 1 adet)
- ❌ Detay ekranında "içindekiler" hover tooltip'i yok
- ❌ Ses efektleri minimal (sadece success/fail)
- ❌ Yerelleştirme tam değil (sabit Türkçe GUI metinleri)
- ❌ Config dosyası yok (GUI görünümü koda gömülü)

### Bilinen Riskler

- Malzeme tüketiminde stack sırası `Inventory.getContents()` sırasıyla
- Çok hızlı tıklamada race condition olabilir
- Crafting level-up bildirimi şu an sadece chat mesajı (title yok)

### Test Edilmemiş Senaryolar

- Çok oyunculu eşzamanlı üretim
- Reload sırasında açık GUI'ler
- PhoenixFish yokken açılış (disable olması bekleniyor)

---

## 🔧 Geliştirme

### Build

```bash
git clone <repo-url>
cd PhoenixCraft
mvn clean package
# target/PhoenixCraft-1.0.0.jar
```

### Maven Bağımlılığı

```xml
<dependency>
    <groupId>com.Phoenix.Fish</groupId>
    <artifactId>PhoenixFish</artifactId>
    <version>1.3.0</version>
    <scope>provided</scope>
</dependency>
```

(Önce PhoenixFish JAR'ını lokal Maven repo'suna yüklemelisiniz)

---

## 📝 Lisans

Bu proje [MIT License](LICENSE) altında dağıtılmaktadır.

---

## 🤝 Katkıda Bulunma

- Issue açın: hata bildirimi veya özellik isteği
- PR gönderin: büyük değişiklikler için önce issue açın

---

## 📚 İlgili Projeler

- **[PhoenixFish](https://github.com/)** — Ana balıkçılık eklentisi (zorunlu bağımlılık)

---

**Author**: Sudo  
**Version**: 1.0.0  
**Status**: 🚧 Early Stage / Beta
