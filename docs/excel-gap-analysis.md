# Excel kaynakları karşılaştırma ve geliştirme yol haritası

Bu belge 5 Ağustos 2026 tarihinde aşağıdaki üç ilham dosyasının formül, sayfa ve veri akışı incelenerek hazırlanmıştır:

- `Examples/Cost Study.xlsx`
- `Examples/Master Plan.xlsx`
- `Examples/FR.SAT.07  Insaat Maliyet Hesap Tablosu GANA.xlsx`

## 1. Excel çalışmalarının ortak modeli

Üç çalışma kitabında ayrıntı seviyesi farklı olsa da aynı hesap zinciri bulunuyor:

1. Teklif/BOQ kalemleri: iş tanımı, birim, miktar, birim fiyat ve tutar.
2. Üretim planı: toplam miktar / günlük üretim = süre.
3. Zaman fazlı kaynak planı: aktivite, ekipman ve personelin ay/hafta içindeki dağılımı.
4. Doğrudan maliyetler: personel, ekipman, yakıt ve malzeme.
5. Mobilizasyon/demobilizasyon ve geçici tesisler.
6. Taşeron ve diğer doğrudan sarflar.
7. Dolaylı maliyetler: kamp, ofis, yemek, izin, konaklama, QHSE vb.
8. On-cost/markup: teminat, finansman, risk, vergi, sigorta, genel merkez payı ve kâr.
9. Maliyet özeti ve teklif/satış fiyatı.

Bu zincirin önemli özelliği tek yönlü izlenebilirliktir: programdaki miktar ve süre kaynak ihtiyacını, kaynak ihtiyacı doğrudan maliyetleri, bunlar da dolaylı gider ve satış fiyatını besler.

## 2. Dosya bazında bulgular

### Cost Study.xlsx

- 11 sayfa, yaklaşık 14 bin formül.
- `schedule_ARAS`: 36 aylık program; ekipman ve personel dağılımı, çalışan/bekleyen saatler.
- `Machinery Deployment Chart`: doğrudan/dolaylı ekipman histogramı ve toplam çalışma/bekleme süresi.
- `Manpower Histogram`: doğrudan/dolaylı personel histogramı ve adam-ay toplamı.
- `MACHINERY & EQUIPMENT COST`: kira/amortisman, bakım-sigorta, çalışma ve bekleme yakıtı.
- `PERSONNEL COST`: net ücret, fazla mesai, izin/bilet, vergi, vize/oturum ve adam-ay.
- `Mobilization`, `MATERIAL_ARAS`, `SUBCONTRACTOR_ARAS`, `INDIRECT COST`: ayrı maliyet alt defterleri.
- `On-cost Coefficient`: teminat, finansman, sigorta, damga vergisi, risk ve brüt kâr üzerinden satış katsayısı.
- `COST SUMMARY`: tüm alt defterleri tek nihai fiyatta topluyor.
- Veri kalitesi notu: 87 tanımlı alanın 34'ü bozuk `#REF!` içeriyor; görünür sayfalarda da bazı `#REF!`/`#DIV/0!` hücreleri var. Uygulama bu formülleri aynen kopyalamamalı; kuralları açık veri modeli olarak kurmalı.

### Master Plan.xlsx

- 8 sayfa, yaklaşık 192 formül.
- En temiz ve okunabilir referans model.
- `3.Schedule`: miktar ve günlük üretimden süre üretip dönemlere dağıtıyor.
- `4.Equipment` ve `5.Manpower`: çeyrek aylık kaynak dağılımı, toplam ay ve maliyet.
- `6.Mob-Demob`: ekipman + insan gücü + yakıt bazlı mobilizasyon hesabı.
- `7.MGO+Lub+Insurance`: ekipman kullanım süresinden yakıt, yağ ve sigorta hesabı.
- `9.Indirect`: personel maliyetinden vergi/SGK; kafa sayısından konaklama, izin ve iaşe.
- Teklif özetindeki markup yaklaşımı `1 / (1 - toplam oran)` formülüyle maliyet üstüne satış katsayısı kuruyor.

### FR.SAT.07 ... GANA.xlsx

- 10 sayfa, yaklaşık 160 formül.
- Kurumsal teklif formu, organizasyon şeması, BOQ ve satış fiyatı birlikte tutuluyor.
- Doğrudan ve dolaylı personel ayrı; aylık adam sayısı, adam-ay, ücret, konaklama ve yemek bulunuyor.
- Ekipmanda sahip/kiralık ayrımı, kapasite, set-ay, kira/amortisman, dizel, yağ, sigorta ve bakım bulunuyor.
- Mobilizasyon ve demobilizasyon toplamı 2/3 ve 1/3 olarak teklif özetine dağıtılıyor.
- Teklif özetinde maliyet, satış fiyatı, net kâr ve amortisman yüzdeleri birlikte izleniyor.

## 3. Mevcut uygulamanın güçlü tarafları

- Proje, tahmin versiyonu, hiyerarşik WBS ve aktiviteler modellenmiş.
- Personel, ekipman ve malzeme kaynak katalogları var.
- Kaynak maliyet bileşenleri ve saat/gün/hafta/ay/birim/sabit hesap bazları var.
- Yakıt tüketimi, ekipman ekibi, proje dolaylı kadrosu ve vergi alanları backend modelinde var.
- Aktivite tarihleri ile Gantt ekranı ve temel kaynak ataması çalışıyor.
- Para birimi ve kur dönüşümü mevcut.
- Backend maliyet servisi kategori bazlı toplam üretebiliyor.
- Tahmin versiyonu, vardiya, çalışma takvimi, proje oranları ve ek maliyet kalemleri için başlangıç domain sınıfları mevcut.

## 4. Fark ve eksik matrisi

| Alan | Excel'lerde | Uygulama durumu | Eksik |
|---|---|---|---|
| BOQ/keşif | Birim, miktar, birim fiyat, teklif tutarı | Uygulandı | CRUD, WBS/aktivite bağlantısı, çoklu para birimi ve izlenebilirlik ekranı tamamlandı; revizyon karşılaştırması Faz 5'te |
| Üretkenlik | Miktar / günlük kapasite = süre | Uygulandı | Çalışma takvimine göre otomatik süre ve tarih hesabı tamamlandı; senaryo karşılaştırması Faz 5'te |
| Bağımlılıklar | Program ilişkileri dönemsel olarak kurulmuş | Kısmi | FS/SS/FF/SF, gecikme ve çevrim kontrolü tamamlandı; kritik yol görselleştirmesi ileride |
| Zaman fazlı kaynak | Ay/hafta bazında adet ve kullanım | Çok kısmi | Atama tek tarih aralığı; dönemsel eğri/histogram yok |
| Çalışma takvimi | Ay, gün ve vardiya varsayımları | Uygulandı | API/UI, vardiya ücretli saatleri, çalışma günü planlaması ve maliyet motoru bağlantısı tamamlandı |
| Ekipman kullanım modu | Çalışma ve bekleme saati ayrı | Domain kısmi | Hesap motoru işletme/bekleme saatlerini kullanmıyor |
| Ekipman maliyeti | Kira/amortisman + bakım + sigorta | Uygulandı | Sahip/kiralık profil ile aylık amortisman, bakım ve sigorta otomatik maliyetleri tamamlandı; ayrı ROI metriği ileride |
| Yakıt | Çalışma ve bekleme tüketimi ayrı | Kısmi | Tek tüketim oranı var; standby, yük faktörü ve fiyat dönemi yok |
| Ekipman ekibi | Makineye bağlı zorunlu ekip | Backend var | UI ve ayrıntılı raporda yönetim yok |
| Personel | Ücret + mesai + izin + vergi + vize | Kısmi | Ücret paketi, ülke yükleri, fazla mesai kuralı eksik |
| Dolaylı kadro | Ay bazlı site yönetimi | Backend kısmi | UI yok; aylık kadro histogramı yok |
| Malzeme | Miktar × fiyat, sınıf/tonaj | Uygulandı | İhtiyaç × fire hesabı, tedarikçi, termin, MOQ, fiyat geçerliliği ve para birimi tamamlandı |
| Taşeron | Ayrı alt defter ve markup | Yok | Taşeron kaynağı/sözleşmesi ve teklif karşılaştırması yok |
| Mob/demob | Ayrı hesap ve fazlara dağıtım | Kısmi | Genel kategori var; mesafe/süre/taşıma tabanlı model yok |
| Geçici tesis/dolaylı | Süre veya kişi sayısı tabanlı | Domain kısmi | Kullanılabilir CRUD/UI yok; formül sürücüleri yok |
| On-cost/markup | Teminat, risk, finansman, vergi, kâr | Uygulandı | Sıralı yüzde kuralları, yürüyen matrah ve satış fiyatı motoru tamamlandı |
| Maliyet raporu | Tek zincir, kategori ve alt defterler | Kısmi | Frontend ve backend farklı hesap yapıyordu; ilk adımda giderildi |
| Nakit akışı | Zaman planından dönemsel maliyet | Yok | Aylık maliyet ve nakit akışı yok |
| Versiyon/senaryo | Teklif revizyon mantığı | Domain var | Kopyalama, karşılaştırma, kilitleme/onay API/UI yok |
| İçe/dışa aktarma | Excel teslim formatı | Yok | Excel import, eşleme, hata raporu ve export yok |
| Veri doğrulama | Hücre/formül bağımlı | Kısmi | İş kuralı validasyonları ve eksik veri uyarıları yetersiz |

## 5. Hesap doğruluğu için tespit edilen teknik borçlar

1. Frontend raporu ile backend farklı algoritma kullanıyordu. **Çözüldü:** rapor ve genel bakış toplamı backend sonucuna bağlandı.
2. `PERCENTAGE` hesap bazı şu anda sıfır üretiyor; on-cost motoru ayrıca tasarlanmalı.
3. Ekipman ekibinde kişi adedi saatlik maliyete tam uygulanmıyordu. **Çözüldü:** ekip ve ekipman adedi ile utilization birlikte uygulanıyor.
4. Proje kadrosunda saatlik ücret hesabında miktar/tahsis oranı etkisi eksik kalabiliyordu. **Çözüldü:** miktar, tahsis oranı ve çalışma takvimi saati hesaba katılıyor.
5. Malzeme `requiredQuantity` ve `wastePercentage` alanları hesapta kullanılmıyordu. **Çözüldü.**
6. `operatingHoursPerDay`, `standbyHoursPerDay` ve `utilizationRate` kullanılmıyordu. **Çözüldü:** çalışma/bekleme yakıtı ayrıldı; vardiya bazlı ayrıntı sonraki fazda ele alınacak.
7. Maliyet bileşenlerinin geçerlilik tarihleri hesap sırasında filtrelenmiyordu. **Çözüldü.**
8. Para birimi dönüşümü global kaynak kataloğu fiyatlarını değiştiriyordu. **Çözüldü:** katalog para birimli varsayılan fiyat, tahmin versiyonu ise proje para birimli fiyat anlık görüntüsü kullanıyor.
9. API'de ek maliyet, proje oranı ve tahmin versiyonu yönetimi işlemlerinin çoğu yok. Takvim ve vardiya API/UI yönetimi **çözüldü.**

## 6. Önerilen uygulama sırası

### Faz 0 — Güvenilir hesap temeli

1. Tek backend maliyet kaynağı ve WBS/aktivite ayrıntılı raporu. **Tamamlandı.**
2. Hesap motoru test matrisi: saat/gün/ay, adet, kullanım, ekip, vergi, fire. **Tamamlandı.**
3. Çalışma/bekleme saati, utilization, personel adedi ve malzeme firesi hatalarını düzelt. **Tamamlandı.**
4. Proje bazlı fiyat ve kur yaklaşımını global katalogdan ayır. **Tamamlandı.**

FE karşılıkları da tamamlandı: ekipman kataloğunda çalışma/bekleme tüketimi ayrı girilir; aktivite kaynak atama penceresinde ekipman çalışma/bekleme saatleri ile malzeme ihtiyaç/fire alanları düzenlenir; Maliyet Kütüphanesi'nde global katalog fiyatı ve aktif proje fiyatı ayrı sütunlarda gösterilir.

### Faz 1 — BOQ ve üretkenlik tabanlı program

5. BOQ modeli ve CRUD: kod, açıklama, birim, miktar, birim fiyat, para birimi, WBS bağlantısı. **Tamamlandı.**
6. Aktiviteye günlük üretim/kapasite ve otomatik süre hesabı ekle. **Tamamlandı.**
7. Aktivite bağımlılıkları, takvim, vardiya ve çalışma günü hesabı ekle. **Tamamlandı.**
8. BOQ → aktivite → WBS izlenebilirliğini kur. **Tamamlandı.**

FE karşılıkları da tamamlandı: menüdeki **BOQ ve Planlama** ekranından BOQ CRUD/bağlantıları, aktivite miktarı ve günlük kapasitesi, otomatik süre, FS/SS/FF/SF bağımlılıkları, gecikme, proje takvimi ve vardiyalar yönetilir. Gantt tarihleri ve backend maliyet hesabı aynı çalışma takvimini kullanır.

### Faz 2 — Zaman fazlı kaynak planlama

9. Atamalara aylık/haftalık zaman fazı ve dağılım eğrisi ekle.
10. Ekipman deployment ve personel histogram ekranlarını oluştur.
11. Doğrudan/dolaylı kaynak ayrımını ve proje kadrosu ekranını tamamla.
12. Çalışan/bekleyen ekipman saatleri ve farklı yakıt tüketimlerini hesapla.

### Faz 3 — Maliyet alt defterleri

13. Personel ücret paketi ve ülke bazlı yükler.
14. Sahip/kiralık ekipman, amortisman, bakım ve sigorta stratejileri. **Tamamlandı.**
15. Malzeme fire, tedarik, fiyat geçerlilik ve kur bilgileri. **Tamamlandı.**
16. Taşeron teklif/sözleşme modeli.
17. Mobilizasyon/demobilizasyon ve geçici tesis hesap sihirbazları.
18. Proje seviyesi dolaylı maliyet kalemleri CRUD/UI.

### Faz 4 — Teklif fiyatı ve finansal raporlar

19. Sıralı on-cost/markup kuralları. **Tamamlandı.**
20. Risk, contingency, teminat, finansman, vergi ve kâr hesabı. **Tamamlandı.**
21. Maliyet, satış fiyatı, brüt/net kâr ve ekipman yatırım getirisi özeti. **Kısmi tamamlandı:** maliyet, BOQ, ek maliyet, satış, brüt/net kâr ve marj Overview ile Fiyatlandırma ekranında; ayrı ROI metriği henüz yok.
22. Aylık maliyet dağılımı ve nakit akışı.

FE karşılıkları: **Fiyatlandırma ve Kâr** ekranında sıralı genel gider, risk, contingency, teminat, finansman, vergi ve kâr kuralları düzenlenir. Her kural tahmini maliyeti veya o ana kadarki yürüyen toplamı matrah alabilir. Overview; güvenilir backend maliyetini, satış fiyatını, ek maliyet/risk toplamını, net kârı, marjı ve BOQ farkını gösterir. Aylık nakit akışı (madde 22) bu teslimata dahil değildir.

**Cost Library kapsamı:** katalog kaydı global varsayılandır ve katalogdaki düzenleme tüm projelerin gelecekte alacağı fiyatı etkiler. Daha önce projeye alınmış fiyatlar tarihsel bütçeyi sessizce değiştirmemek için tahmin versiyonunda snapshot olarak kalır. Kullanıcı ilgili satırdaki yenileme işlemiyle aktif projenin snapshot'ını katalogdan açıkça günceller. Aynı ekranda ekipman özmal/kiralık, alış-hurda değeri, faydalı ömür, bakım ve sigorta oranları; malzemede tedarikçi, termin süresi, minimum sipariş ve varsayılan fire düzenlenir.

**Kaynak paylaşım ve silme kapsamı:** yeni personel, ekipman veya malzeme varsayılan olarak yalnızca oluşturulduğu projede kullanılabilir. `Ortak kaynak` işaretlendiğinde diğer projelerin kaynak kataloglarında ve yeni aktivite atamalarında da görünür. Kaynağın sahibi proje paylaşımı sonradan açıp kapatabilir; diğer projeler kapsamı değiştiremez. Paylaşım kapatıldığında başka projelerdeki mevcut atamalar tarihsel hesap bütünlüğü için korunur, fakat o kaynak yeni atamalarda kullanılamaz. Sahip proje kullanılmayan bir kaynağı onay penceresinden silebilir. Aktivite, ekip veya proje kadrosunda kullanılan kaynakların silinmesi veri bütünlüğü için engellenir; önce ilgili atamalar kaldırılmalıdır.

### Faz 5 — Kurumsal kullanım

23. Tahmin versiyonu kopyalama, karşılaştırma, baseline ve onay akışı.
24. Excel import şablonu, kolon eşleme ve doğrulama raporu.
25. Excel/PDF teklif, BOQ, kaynak histogramı ve maliyet özeti exportları.
26. Yetki, audit trail ve değişiklik geçmişi.

## 7. İlk geliştirme adımının kabul kriterleri

- Backend tek çağrıda proje seviyesi, WBS ve aktivite maliyetlerini döndürür.
- Ayrıntı toplamları genel toplamla aynıdır.
- Rapor ekranı kendi formülünü çalıştırmaz; backend sonucunu gösterir.
- Personel, ekipman, malzeme, yakıt ile dolaylı/vergi tutarları görünür.
- Aktivite tarihi, kaynak ataması, fiyat veya para birimi değiştiğinde rapor yenilenir.
- Genel bakış ekranındaki proje toplamı aynı backend sonucunu kullanır.
- Backend testleri ve frontend production build başarılıdır.
