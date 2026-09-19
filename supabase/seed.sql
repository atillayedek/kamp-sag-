-- Gerçek referans verisi — SAHTE/DEMO İÇERİK DEĞİLDİR (bkz. §7, §39: production'da
-- mock kullanıcı/gönderi/mesaj yasağı). Bu dosya yalnızca uygulamanın çalışması
-- için ZORUNLU referans/konfigürasyon verisini içerir: gerçek üniversite listesi
-- (kayıt formundaki seçim kutusu için, bkz. §10), her üniversite için otomatik
-- topluluk kaydı + genel topluluk, ve birkaç başlangıç rozeti tanımı. Hiçbir
-- sahte kullanıcı, gönderi, yorum, mesaj veya bildirim BURADA YOKTUR.

insert into public.universities (name, short_name, city, domain) values
  ('Boğaziçi Üniversitesi', 'Boğaziçi', 'İstanbul', 'boun.edu.tr'),
  ('Orta Doğu Teknik Üniversitesi', 'ODTÜ', 'Ankara', 'metu.edu.tr'),
  ('İstanbul Teknik Üniversitesi', 'İTÜ', 'İstanbul', 'itu.edu.tr'),
  ('Koç Üniversitesi', 'Koç', 'İstanbul', 'ku.edu.tr'),
  ('Sabancı Üniversitesi', 'Sabancı', 'İstanbul', 'sabanciuniv.edu'),
  ('Bilkent Üniversitesi', 'Bilkent', 'Ankara', 'bilkent.edu.tr'),
  ('Hacettepe Üniversitesi', 'Hacettepe', 'Ankara', 'hacettepe.edu.tr'),
  ('Ankara Üniversitesi', 'Ankara Üniversitesi', 'Ankara', 'ankara.edu.tr'),
  ('Ege Üniversitesi', 'Ege', 'İzmir', 'ege.edu.tr'),
  ('Marmara Üniversitesi', 'Marmara', 'İstanbul', 'marmara.edu.tr'),
  ('Yıldız Teknik Üniversitesi', 'YTÜ', 'İstanbul', 'yildiz.edu.tr'),
  ('Galatasaray Üniversitesi', 'GSÜ', 'İstanbul', 'gsu.edu.tr')
on conflict (short_name) do nothing;

insert into public.communities (name, type, university_id)
select u.name, 'UNIVERSITY', u.id
from public.universities u
where not exists (
  select 1 from public.communities c where c.university_id = u.id and c.type = 'UNIVERSITY'
);

insert into public.communities (name, type)
select 'Tüm Üniversiteler', 'GENERAL'
where not exists (select 1 from public.communities where type = 'GENERAL');

insert into public.badges (code, title, description, icon) values
  ('first_help', 'İlk Yardımlaşma', 'Bir öğrenciye ilk kez yardım ettin.', 'hand.raised.fill'),
  ('ten_helps', '10 Yardım', 'Kampüsünde 10 kez yardım ettin.', 'star.fill'),
  ('verified_student', 'Doğrulanmış Öğrenci', 'Öğrenci belgen onaylandı.', 'checkmark.seal.fill')
on conflict (code) do nothing;
