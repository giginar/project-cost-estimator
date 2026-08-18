# Construction Cost Estimator

Spring Boot 4 ve Angular 21 ile geliştirilmiş; iş programı, kaynak, maliyet, BOQ,
fiyatlandırma ve nakit akışı verilerini tek proje tahmini altında birleştiren örnek
inşaat maliyet yönetimi uygulamasıdır.

Uygulama varsayılan olarak kapsamlı bir demo veri setiyle açılır. Bu veri seti
kalıcı bir disk veritabanı değil, uygulama süreci içinde çalışan bellek içi
repository'lerde tutulur. Backend her yeniden başlatıldığında demo verisi yeniden
oluşturulur; yapılan değişiklikler yeniden başlatma sonrasında korunmaz.

## Özellikler

- Proje portföyü, tahmin sürümü ve WBS yönetimi
- Aktivite iş programı, Gantt görünümü, kilometre taşı, mobilizasyon ve demobilizasyon
- Miktar/üretkenlik üzerinden otomatik süre hesabı
- Çalışma takvimi, çoklu vardiya ve dört bağımlılık tipi
- Personel, ekipman ve malzeme kataloğu
- Sistem genelinde paylaşılan ve projeye özel kaynaklar
- Kaynak maliyet bileşenleri, tarih geçerliliği, vergi ve tahmine özel fiyat kopyaları
- Sahip olunan ekipman için amortisman, bakım ve sigorta hesabı
- Yakıt/enerji tüketimi ve projeye ait merkezi diesel, gasoline, marine diesel ve elektrik fiyatları
- Malzeme tedarikçisi, termin süresi, minimum sipariş ve fire oranı
- BOQ kaydı, WBS/aktivite izlenebilirliği ve Excel'den içe aktarma
- Genel gider, risk, beklenmeyen gider, teminat, finansman, vergi ve kâr kuralları
- Detaylı maliyet kırılımı ile aylık gelir-gider ve kümülatif nakit akışı
- Proje bazlı yönetilebilir maliyet kodları
- USD/TRY ve EUR/TRY proje kurları
- ENGINEER, MANAGER ve ADMIN rolleri; e-posta doğrulama ve parola sıfırlama akışları
- Swagger/OpenAPI dokümantasyonu ve Actuator sağlık uçları

## Gereksinimler

- Java 21
- Güncel bir Node.js LTS sürümü ve npm
- Windows'ta PowerShell veya eşdeğer bir terminal

Repo Maven Wrapper içerir; ayrıca Maven kurmak gerekmez.

## Yerelde çalıştırma

Backend'i repo kökünde başlatın:

```powershell
.\mvnw.cmd spring-boot:run
```

Başka bir terminalde frontend'i başlatın:

```powershell
cd frontend
npm install
npm start
```

Ardından `http://localhost:4200` adresini açın. Angular geliştirme sunucusu
`/api` isteklerini `http://localhost:8080` adresindeki backend'e yönlendirir.

Yararlı adresler:

- Uygulama: `http://localhost:4200`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
- Sağlık kontrolü: `http://localhost:8080/actuator/health`

### Demo hesapları

| Rol | E-posta | Parola | Yetki özeti |
|---|---|---|---|
| Engineer | `engineer@example.com` | `Engineer123!` | Proje, plan, kaynak ve maliyet düzenleme |
| Manager | `manager@example.com` | `Manager123!` | Proje verilerini görüntüleme ve proje ayarlarını güncelleme |
| Admin | `admin@example.com` | `Admin123!` | Kullanıcı yönetimi ve geliştirme e-posta kutusu |

## Hazır demo verisi

İlk açılışta iki proje bulunur:

| Kod | Proje | Tarih | Durum | USD/TRY | EUR/TRY |
|---|---|---|---|---:|---:|
| `MAR-001` | Marine Excavation — Phase 1 | 03.08.2026–26.09.2026 | DRAFT | 40.50 | 47.25 |
| `PORT-2027` | Aegean Deepwater Port Expansion | 12.01.2027–30.11.2027 | ACTIVE | 42.75 | 49.60 |

Arayüz başlangıçta daha kapsamlı olan `PORT-2027` projesini seçer. Proje
seçicisinden `MAR-001` projesine geçilerek projeye özel kaynak ve merkezi enerji
fiyatı örnekleri incelenebilir.

Demo kapsamı şunları içerir:

- Dolu WBS ve aktiviteler, otomatik planlanan üretim işleri, iki vardiyalı altı günlük takvim
- Finish-to-start, start-to-start, finish-to-finish ve start-to-finish bağımlılık örnekleri
- `PORT-2027` için altı, `MAR-001` için üç aktivite bağlantılı BOQ kalemi
- Her proje için sekiz maliyet kodu: `LAB`, `EQP`, `FUEL`, `MAT`, `ACC`, `TRN`, `OVH`, `TAX`
- Her proje için diesel, gasoline, marine diesel ve elektrik merkezi birim fiyatı
- Personel, ekipman, malzeme, ekip, proje personeli ve fireli malzeme atamaları
- Paylaşılan kaynaklar ve `MAR-001` projesine özel `EQ-005` Electric Dewatering Pump
- `EQ-005` üzerinde satın alma bedeli, kalıntı değer, faydalı ömür ve üretilmiş aylık maliyetler
- `MAT-001`, `MAT-002` ve `MAT-003` üzerinde tedarikçi ve satın alma bilgileri
- `EQ-004` üzerinde %20 vergili ve 2026–2028 arasında geçerli maliyet bileşeni
- Saatlik, vardiyalık, günlük, haftalık, aylık, birim ve sabit maliyet örnekleri
- Yedi fiyatlandırma kuralı ve gelir/gider içeren çok aylı nakit akışı

`EQ-005`–`EQ-009` ekipmanları üzerinde doğrudan FUEL maliyet bileşeni yoktur.
`EQ-005` ve `EQ-009` elektrik; `EQ-006` diesel; `EQ-007` gasoline; `EQ-008`
marine diesel merkezi fiyatı geri dönüşünü göstermek için oluşturulmuştur. Diğer
ekipmanlardaki açık FUEL fiyatı merkezi fiyatın üzerinde kaynak bazlı override
olarak çalışır.

Demo verisini kapatmak için backend'i şu değişkenle başlatın:

```powershell
$env:DEMO_DATA_ENABLED='false'
.\mvnw.cmd spring-boot:run
```

## Önerilen kullanım akışı

1. Engineer hesabıyla giriş yapın ve üst menüden bir proje seçin.
2. **Project settings** sayfasında proje kimliğini, USD/TRY ve EUR/TRY kurlarını,
   merkezi enerji fiyatlarını ve maliyet kodlarını inceleyin.
3. **Schedule** sayfasında WBS/aktivite yapısını ve Gantt planını görüntüleyin;
   aktivitelere personel, ekipman ve malzeme atayın.
4. **BOQ & planning** sayfasında BOQ–WBS–aktivite bağlantılarını, üretkenlik
   planını, bağımlılıkları ve vardiyaları yönetin.
5. **Cost library** sayfasında maliyet bileşenlerini, tahmine kopyalanmış
   fiyatları, ekipman ekonomisini ve malzeme tedarik bilgisini inceleyin.
6. **Pricing & profit** sayfasında ek fiyatlandırma kurallarını, hedef satış
   fiyatını, hedef kârı ve BOQ bazlı planlanan sonucu karşılaştırın.
7. **Reports > Cost breakdown** sayfasında detaylı maliyet kırılımını inceleyin.
8. **Reports > Cash flow** sayfasında BOQ planlanan geliri, tahmini maliyet,
   aylık bakiye ve kümülatif bakiyeyi karşılaştırın.

Kaynak maliyetleri aktiviteye ilk atamada tahmin fiyatı olarak kopyalanır. Kaynak
kataloğundaki fiyatı sonradan değiştirmek mevcut tahmin fiyatını otomatik olarak
değiştirmez; **Cost library** içinden senkronizasyon yapılmalıdır. Bu davranış
geçmiş tahminlerin fiyat bazını korur.

## Fiyatlandırma ve nakit akışı ilişkisi

Uygulama ticari hedef ile mevcut gelir planını birbirinden ayırır:

- **Hedef satış fiyatı**, tahmini maliyete sıralı genel gider, risk, beklenmeyen
  gider, teminat, finansman, vergi ve kâr kurallarının eklenmesiyle oluşur.
- **Hedef kâr**, `PROFIT` türündeki fiyatlandırma kurallarının toplamıdır ve ancak
  hedef satış fiyatı sözleşme/BOQ değeriyle güvence altına alındığında ticari
  hedefi ifade eder.
- **BOQ planlanan geliri**, BOQ miktar × birim fiyat toplamıdır ve cash flow'un
  gelir dayanağıdır.
- **Planlanan sonuç**, BOQ planlanan geliri eksi tahmini maliyettir. Pricing
  ekranındaki planlanan sonuç ile cash flow toplam bakiyesi aynı temele dayanır.
- **BOQ / hedef fiyat farkı**, mevcut BOQ gelirinin hedef satış fiyatını ne kadar
  karşıladığını gösterir. Negatif fark, hedef kârın henüz güvence altında
  olmadığını belirtir.

Aylık cash flow bir planlama baz çizgisidir: BOQ geliri ve hesaplanan maliyetler
bağlı oldukları aktivitenin planlanan çalışma dönemlerine dağıtılır. Fatura tarihi,
hakediş onayı, tahsilat gecikmesi, avans ve teminat kesintisi gibi gerçek ödeme
koşulları mevcut modelde bulunmadığı için rapor gerçekleşen banka nakit hareketi
olarak yorumlanmamalıdır.

## BOQ Excel içe aktarma

**BOQ & planning** sayfasındaki içe aktarma alanı `.xlsx` ve `.xls` dosyalarının
ilk çalışma sayfasını okur. Şema sabit beş sütundur:

| Sütun | İçerik | Örnek |
|---|---|---|
| A | Item No / Code | `2`, `2.1` |
| B | Description | `Capital Dredging` |
| C | Unit | `m3` |
| D | Quantity | `180000` |
| E | Row Type | `HEADER` veya `BOQ_ITEM` |

Örnek satırlar:

| Item No | Description | Unit | Quantity | Row Type |
|---|---|---|---:|---|
| 2 | Capital Dredging |  |  | HEADER |
| 2.1 | Access channel dredging | m3 | 180000 | BOQ_ITEM |
| 2.2 | Harbor basin dredging | m3 | 130000 | BOQ_ITEM |

Kabul edilen satır tipi eş anlamlıları `HEADER`/`BASLIK`/`WBS` ve
`BOQ_ITEM`/`ITEM`/`BOQ` değerleridir. Birimler `adet`, `pc`, `pcs`, `piece`,
`kg`, `ton`, `tonne`, `l`, `lt`, `liter`, `litre`, `m`, `m2`, `sqm`, `m3` ve
`cbm` biçimlerinde verilebilir.

Her `BOQ_ITEM` kendisinden önce bir `HEADER` satırı gerektirir. İçe aktarılan
başlık mevcut değilse yeni WBS oluşturulur. İçe aktarılan BOQ kalemleri proje
para birimiyle, başlangıçta `0` birim fiyatla ve aktivite bağlantısı olmadan
oluşur; içe aktarma sonrasında arayüzden fiyatlandırılıp aktiviteye bağlanmalıdır.
Hatalı bir satır varsa dosya yalnızca önizleme sonucu döndürür ve hiçbir kayıt
yazılmaz.

## Hesaplama notları

- BOQ değeri gelir planının kaynağıdır; aktivite tarih aralığındaki aylara dağıtılır.
- Yetkili maliyet raporundaki aktivite ve proje seviyesi maliyetler gider planına dağıtılır.
- Nakit akışı gerçek muhasebe hareketi değil, planlanan tahmin akışıdır.
- Merkezi enerji fiyatı yalnızca ekipmanın tüketim kaydı varsa ve aynı kaynakta
  açık bir FUEL maliyet bileşeni yoksa kullanılır.
- Vergi, yalnızca maliyet bileşeni vergilendirilebilir olarak işaretlenmişse hesaplanır.
- Proje para birimi değişiminde USD/TRY ve EUR/TRY değerleri kaynak fiyatlarının
  dönüştürülmesinde kullanılır; uygulama dışarıdan canlı kur çekmez.

## Test ve derleme

Backend testleri:

```powershell
.\mvnw.cmd test
```

Frontend testleri ve üretim derlemesi:

```powershell
cd frontend
npm test -- --watch=false
npm run build
```

## Yapı ve ek dokümantasyon

Backend, giriş portları/uygulama servisleri/domain/adapter katmanlarıyla hexagonal
mimariye göre düzenlenmiştir. Frontend bağımsız Angular uygulamasıdır.

- REST uçları ve örnek davranışlar: [API.md](API.md)
- Backend mimarisi: [docs/backend-hexagonal-architecture.md](docs/backend-hexagonal-architecture.md)
- Ürün ve entegrasyon denetimi: [docs/product-overview-and-integration-audit.md](docs/product-overview-and-integration-audit.md)
- AWS dağıtımı: [docs/aws-free-tier-deployment.md](docs/aws-free-tier-deployment.md)

SMTP varsayılan olarak kapalıdır. E-posta teslimatı, herkese açık uygulama URL'si
ve diğer çalışma zamanı değişkenleri `src/main/resources/application.yaml` içinde
belgelenen `SMTP_*`, `MAIL_*` ve `APP_PUBLIC_URL` ortam değişkenleriyle ayarlanır.
