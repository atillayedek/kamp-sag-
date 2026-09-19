-- iOS PendingReviewView'in "Başvuru Durumunu Yenile" dışında canlı güncelleme
-- alabilmesi için (opsiyonel iyileştirme). Postgres Changes realtime, mevcut
-- RLS politikalarına tabidir (bkz. profiles_select) — güvenlik modeli değişmez.
alter publication supabase_realtime add table public.profiles;
