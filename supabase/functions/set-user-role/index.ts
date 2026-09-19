// Supabase Edge Function: set-user-role
// Admin/moderatör yetkisinin TEK atanma yolu (Firebase'deki assignAdminClaim karşılığı).
// Yetki, Auth JWT'sindeki app_metadata.is_admin / is_moderator alanlarında yaşar; bu alanları yalnızca
// service_role (Admin API) yazabilir, istemci asla. RLS'teki is_admin()/is_moderator() bunları okur.
//
// Çağıran ADMIN olmalıdır. Yetki, JWT içindeki (bayat olabilecek) iddiaya değil, Admin API'den okunan
// güncel app_metadata'ya göre denetlenir. Adminin kendi admin bayrağını düşürmesi engellenir (kilitlenme).
// Not: hedef kullanıcının mevcut JWT'si eski iddiaları taşır; uygulamadaki "Başvuru Durumunu Yenile" /
// oturum yenileme ile güncellenir.
//
// İlk admin bu fonksiyonla atanamaz (çağıran admin olmalı) — bir kereye mahsus SQL ile atanır: docs/ADMIN.md.

import { createClient } from "jsr:@supabase/supabase-js@2";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json(405, { error: "Bu istek yöntemi desteklenmiyor." });

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) return json(401, { error: "Bu işlem için giriş yapmanız gerekiyor." });

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

    const userClient = createClient(supabaseUrl, anonKey, { global: { headers: { Authorization: authHeader } } });
    const { data: { user: caller }, error: callerError } = await userClient.auth.getUser();
    if (callerError || !caller) return json(401, { error: "Bu işlem için giriş yapmanız gerekiyor." });

    const serviceClient = createClient(supabaseUrl, serviceRoleKey);

    // Güncel yetki: JWT iddiasına değil, veritabanındaki app_metadata'ya bak.
    const { data: callerRecord, error: callerRecordError } = await serviceClient.auth.admin.getUserById(caller.id);
    if (callerRecordError || callerRecord?.user?.app_metadata?.is_admin !== true) {
      return json(403, { error: "Bu işlem için yönetici yetkisi gerekir." });
    }

    const body = await req.json().catch(() => null);
    const userId = body?.userId;
    const isModerator = body?.isModerator;
    const isAdmin = body?.isAdmin;

    if (typeof userId !== "string" || !UUID_RE.test(userId)) {
      return json(400, { error: "Geçerli bir kullanıcı kimliği gerekli." });
    }
    if (isModerator === undefined && isAdmin === undefined) {
      return json(400, { error: "isModerator veya isAdmin belirtilmeli." });
    }
    if ((isModerator !== undefined && typeof isModerator !== "boolean") ||
        (isAdmin !== undefined && typeof isAdmin !== "boolean")) {
      return json(400, { error: "isModerator ve isAdmin true/false olmalı." });
    }
    if (userId === caller.id && isAdmin === false) {
      return json(400, { error: "Kendi yönetici yetkinizi kaldıramazsınız." });
    }

    const { data: target, error: targetError } = await serviceClient.auth.admin.getUserById(userId);
    if (targetError || !target?.user) return json(404, { error: "Kullanıcı bulunamadı." });

    const current = target.user.app_metadata ?? {};
    const next = {
      ...current,
      ...(isModerator !== undefined ? { is_moderator: isModerator } : {}),
      ...(isAdmin !== undefined ? { is_admin: isAdmin } : {}),
    };

    const { error: updateError } = await serviceClient.auth.admin.updateUserById(userId, { app_metadata: next });
    if (updateError) {
      console.error("set-user-role update error", updateError);
      return json(500, { error: "Yetki güncellenemedi. Lütfen tekrar deneyin." });
    }

    console.log(JSON.stringify({
      event: "set_user_role",
      actor: caller.id,
      target: userId,
      isModerator: next.is_moderator ?? false,
      isAdmin: next.is_admin ?? false,
    }));

    return json(200, { userId, isModerator: next.is_moderator ?? false, isAdmin: next.is_admin ?? false });
  } catch (error) {
    console.error("set-user-role unexpected error", error);
    return json(500, { error: "Yetki güncellenemedi. Lütfen tekrar deneyin." });
  }
});
