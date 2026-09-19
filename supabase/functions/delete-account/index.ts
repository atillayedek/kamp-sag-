// Supabase Edge Function: delete-account
// App Store / Google Play kuralı gereği kullanıcı kendi hesabını ve verisini gerçekten silebilmelidir.
// Akış: JWT'den kullanıcı doğrulanır -> Storage'daki belgeleri silinir -> auth.users satırı silinir.
// Kullanıcıya ait tüm tablo verisi ON DELETE CASCADE ile gider (bkz. supabase/migrations/2026091600xx);
// başkalarının kayıtlarını bozabilecek referanslar SET NULL'dır (bkz. 202609190002).
// Kullanıcı yalnızca KENDİ hesabını silebilir: hedef her zaman doğrulanmış JWT'nin sahibidir, gövdeden id alınmaz.

import { createClient } from "jsr:@supabase/supabase-js@2";

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
    const { data: { user }, error: userError } = await userClient.auth.getUser();
    if (userError || !user) return json(401, { error: "Bu işlem için giriş yapmanız gerekiyor." });

    const serviceClient = createClient(supabaseUrl, serviceRoleKey);

    const bucket = serviceClient.storage.from("student-documents");
    const { data: files, error: listError } = await bucket.list(user.id);
    if (listError) {
      console.error("delete-account list error", listError);
      return json(500, { error: "Hesabınız silinemedi. Lütfen tekrar deneyin." });
    }
    if (files && files.length > 0) {
      const { error: removeError } = await bucket.remove(files.map((f) => `${user.id}/${f.name}`));
      if (removeError) {
        console.error("delete-account remove error", removeError);
        return json(500, { error: "Hesabınız silinemedi. Lütfen tekrar deneyin." });
      }
    }

    const { error: deleteError } = await serviceClient.auth.admin.deleteUser(user.id);
    if (deleteError) {
      console.error("delete-account deleteUser error", deleteError);
      return json(500, { error: "Hesabınız silinemedi. Lütfen tekrar deneyin." });
    }

    console.log(JSON.stringify({ event: "delete_account", userId: user.id, success: true }));
    return json(200, { deleted: true });
  } catch (error) {
    console.error("delete-account unexpected error", error);
    return json(500, { error: "Hesabınız silinemedi. Lütfen tekrar deneyin." });
  }
});
