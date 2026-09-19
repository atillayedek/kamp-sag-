-- PostgreSQL CREATE FUNCTION varsayılan olarak EXECUTE'u PUBLIC'e verir.
-- 202609160016'da yalnızca anon/authenticated'dan revoke edilmişti, PUBLIC'ten
-- değil — get_advisors bulgusu bu yüzden devam etti. PUBLIC'ten de revoke ediliyor.
revoke execute on function public.handle_new_user() from public;
revoke execute on function public.protect_profile_privileged_fields() from public;
revoke execute on function public.sync_profile_verification_status() from public;
revoke execute on function public.notify_verification_status_change() from public;
