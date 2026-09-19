insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('student-documents', 'student-documents', false, 10485760, array['application/pdf'])
on conflict (id) do nothing;

-- Dosya yolu: {user_id}/document.pdf — storage.foldername(name)[1] ilk klasör
-- segmentini (user_id) verir. Kullanıcı yalnızca KENDİ klasörüne erişebilir;
-- moderatör/admin tüm belgeleri okuyabilir (bkz. §11).
create policy "student_documents_select_own_or_staff"
on storage.objects for select to authenticated
using (
  bucket_id = 'student-documents'
  and (
    (select auth.uid())::text = (storage.foldername(name))[1]
    or (select public.is_moderator())
  )
);

create policy "student_documents_insert_own"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'student-documents'
  and (select auth.uid())::text = (storage.foldername(name))[1]
);

-- Red sonrası yeniden yükleme (upsert) için gereklidir.
create policy "student_documents_update_own"
on storage.objects for update to authenticated
using (bucket_id = 'student-documents' and (select auth.uid())::text = (storage.foldername(name))[1])
with check (bucket_id = 'student-documents' and (select auth.uid())::text = (storage.foldername(name))[1]);
