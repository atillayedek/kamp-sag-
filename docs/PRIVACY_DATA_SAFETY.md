# Gizlilik ve Veri Güvenliği (Google Play "Data safety" formu için özet)

> Bu belge uygulamanın **gerçekte** yaptığını özetler; Play Console formu buna göre doldurulmalıdır. Hukuki metinler (gizlilik politikası, KVKK aydınlatma)
> ürün sahibi/hukuk tarafından yazılır ve barındırılır — bkz. `docs/BLOCKERS.md` B11.

## Toplanan veri

| Veri | Amaç | Paylaşım | Zorunlu mu | Saklama |
|---|---|---|---|---|
| E-posta, şifre (Supabase Auth; şifre hash'li) | Hesap, giriş | Yok (Supabase altyapısı işleyici) | Evet | Hesap silinene dek |
| Ad soyad, kullanıcı adı, üniversite, bölüm | Profil, topluluk, eşleşme | Yalnızca ONAYLI öğrencilere görünür (RLS, `supabase/tests/005`) | Evet | Hesap silinene dek |
| Öğrenci belgesi (PDF) | Öğrenci doğrulama | Yalnızca moderatör ekibi; başka kimseye görünmez | Evet (doğrulama için) | Hesap silinene dek |
| Gönderiler, yorumlar, ihtiyaç ilanları | Uygulama işlevi | Onaylı öğrenciler (kapsam: genel / kendi üniversitesi) | İşlev için | Hesap silinene dek |
| Mesajlar | Sohbet | Yalnızca sohbetteki iki kişi; mesajlar sonradan değiştirilemez/silinemez | İşlev için | Hesap silinene dek |
| Kullanım olayları (yalnızca olay ADI + kullanıcı kimliği + zaman) | Ürün analitiği | Yok | Hayır — Profil > Gizlilik ve Konum'dan kapatılır | Hesap silinene dek |
| Abonelik durumu (Google Play jetonu, bitiş zamanı) | Satın alma doğrulama | Google Play (doğrulama) | Yalnızca satın alırsa | Hesap silinene dek |
| Bildirim tercihi | Uygulama içi bildirim ayarı | Yok | Hayır | Hesap silinene dek |

## Toplanmayan veri

- **Konum** (kesin/yaklaşık): hiç istenmez, izin yoktur. Eşleşmeler kategori, etiket ve aynı bölüm bilgisiyle hesaplanır.
- Kişiler, takvim, SMS, çağrı kaydı, fotoğraf/medya, mikrofon, kamera, cihaz/reklam kimliği: kullanılmaz.
- Üçüncü taraf analitik/reklam SDK'sı YOK.

## Güvenlik uygulamaları

- Aktarım: yalnızca HTTPS (Android varsayılan olarak clear-text'e izin vermez).
- Erişim: her tabloda satır düzeyi güvenlik (RLS); okuma yetkisi doğrulanmış (ACTIVE + APPROVED) öğrenciye ait. 9 kural test dosyası: `supabase/tests/`.
- Sırlar: API anahtarları yalnızca Edge Function secret'larında; istemcide yalnızca herkese açık (publishable) anahtar.
- `allowBackup=false`: oturum jetonları bulut yedeğine girmez.
- AI (Claude) yalnızca sunucu tarafında çağrılır; ilan metni analiz için gönderilir, istemciye anahtar verilmez.

## Kullanıcı hakları

- **Hesap ve veri silme:** uygulama içinde Profil > Hesabı Sil (belgeler dahil kalıcı silme; `delete-account`). Play politikası uygulama DIŞINDA da bir silme talebi adresi ister (B11).
- **Analitiği kapatma:** Profil > Gizlilik ve Konum.
- **Bildirim tercihi:** Profil > Bildirim Ayarları.

## Çalışma zamanı izinleri

Yok. `INTERNET` (normal izin) dışında izin istenmez; belge seçimi sistem belge seçicisiyle yapılır (depolama izni gerekmez). Play Billing kütüphanesi `BILLING` iznini manifest birleştirmesiyle ekler. Push bildirim izni (`POST_NOTIFICATIONS`) bilerek yoktur (FCM kullanılmıyor, B1).

## Play Console'da doldurulacak (dış erişim gerektirir)

1. Gizlilik politikası URL'si (barındırılmış sayfa) — B11.
2. "Hesap silme talebi" web adresi — B11.
3. Data safety formu: yukarıdaki tabloya göre (veri "şifreli aktarılıyor"; kullanıcı silme talep edebiliyor).
4. Hedef kitle/yaş: üniversite öğrencileri (18+ beklenir; ürün sahibi kararı).
