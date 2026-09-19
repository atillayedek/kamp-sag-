// Supabase Edge Function: submit-student-document
// Akış: iOS PDF'i doğrudan Storage'a (student-documents bucket, kendi
// {user_id}/document.pdf yoluna) yükler -> bu fonksiyonu çağırır -> fonksiyon
// dosyanın gerçekten var olduğunu ve kullanıcıya ait olduğunu doğrular ->
// student_verifications kaydı oluşturur (status=PENDING) -> DB trigger'ları
// profiles.verification_status/account_status'u otomatik günceller
// (bkz. supabase/migrations/202609160003, 202609160009).
//
// KARAR (belgelenmiş, kesinleşmiş değil): Kullanıcı zaten APPROVED ise
// yeniden başvuru REDDEDİLİR (§12 madde 9 iki seçenek sunuyordu: reddet ya da
// yönet — daha basit ve güvenli olan "reddet" seçildi).

import { createClient } from "jsr:@supabase/supabase-js@2";

const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
const ALLOWED_MIME_TYPE = "application/pdf";
const RATE_LIMIT = 5; // taslak değer, netleşmedi
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000;

async function enforceRateLimit(
  serviceClient: ReturnType<typeof createClient>,
  userId: string,
): Promise<{ ok: true } | { ok: false; message: string }> {
  const id = `${userId}:submit-student-document`;
  const now = Date.now();
  const { data: existing } = await serviceClient
    .from("rate_limits")
    .select("count, window_start")
    .eq("id", id)
    .maybeSingle();

  if (!existing) {
    await serviceClient.from("rate_limits").insert({ id, count: 1, window_start: new Date(now).toISOString() });
    return { ok: true };
  }
  const windowStart = new Date(existing.window_start as string).getTime();
  if (now - windowStart > RATE_LIMIT_WINDOW_MS) {
    await serviceClient.from("rate_limits").update({ count: 1, window_start: new Date(now).toISOString() }).eq("id", id);
    return { ok: true };
  }
  if ((existing.count as number) >= RATE_LIMIT) {
    return { ok: false, message: "Çok fazla başvuru denemesi yaptınız. Lütfen bir süre sonra tekrar deneyin." };
  }
  await serviceClient.from("rate_limits").update({ count: (existing.count as number) + 1 }).eq("id", id);
  return { ok: true };
}

Deno.serve(async (req: Request) => {
  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Bu işlem için giriş yapmanız gerekiyor." }), { status: 401 });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

    // Kullanıcı ID'si YALNIZCA doğrulanmış JWT'den alınır — istemcinin
    // gönderdiği herhangi bir UID alanına asla güvenilmez (bkz. §12 madde 2-3).
    const userClient = createClient(supabaseUrl, anonKey, { global: { headers: { Authorization: authHeader } } });
    const { data: { user }, error: userError } = await userClient.auth.getUser();
    if (userError || !user) {
      return new Response(JSON.stringify({ error: "Bu işlem için giriş yapmanız gerekiyor." }), { status: 401 });
    }

    const body = await req.json().catch(() => null);
    const storagePath = body?.storagePath;
    if (typeof storagePath !== "string" || storagePath.length === 0) {
      return new Response(JSON.stringify({ error: "Belge yolu geçersiz." }), { status: 400 });
    }

    // Dosya yolunun kullanıcının KENDİ klasörüne ait olduğunu doğrula
    // (Storage RLS zaten bunu yükleme anında zorunlu kılar, ama istemciden
    // gelen path'e burada da güvenilmez — savunma derinliği).
    if (!storagePath.startsWith(`${user.id}/`)) {
      return new Response(JSON.stringify({ error: "Belge yolu bu kullanıcıya ait değil." }), { status: 403 });
    }

    const serviceClient = createClient(supabaseUrl, serviceRoleKey);

    const rateLimitResult = await enforceRateLimit(serviceClient, user.id);
    if (!rateLimitResult.ok) {
      return new Response(JSON.stringify({ error: rateLimitResult.message }), { status: 429 });
    }

    // Dosyanın gerçekten yüklendiğini, MIME type ve boyutunu doğrula.
    // Bucket seviyesinde allowed_mime_types/file_size_limit zaten uygulanıyor
    // (bkz. supabase/migrations/202609160020) — bu ikinci bir savunma katmanı.
    const pathParts = storagePath.split("/");
    const fileName = pathParts.pop()!;
    const folder = pathParts.join("/");
    const { data: listResult, error: listError } = await serviceClient
      .storage
      .from("student-documents")
      .list(folder, { search: fileName });

    const fileInfo = listResult?.find((f) => f.name === fileName);
    if (listError || !fileInfo) {
      return new Response(JSON.stringify({ error: "Yüklenen belge bulunamadı. Lütfen tekrar yükleyin." }), { status: 400 });
    }
    if (fileInfo.metadata?.mimetype !== ALLOWED_MIME_TYPE) {
      return new Response(JSON.stringify({ error: "Yalnızca PDF formatındaki belgeler kabul edilir." }), { status: 400 });
    }
    if (typeof fileInfo.metadata?.size === "number" && fileInfo.metadata.size > MAX_FILE_SIZE_BYTES) {
      return new Response(JSON.stringify({ error: "Belge boyutu 10 MB sınırını aşıyor." }), { status: 400 });
    }

    const { data: profile } = await serviceClient
      .from("profiles")
      .select("verification_status")
      .eq("id", user.id)
      .single();

    if (profile?.verification_status === "APPROVED") {
      return new Response(
        JSON.stringify({ error: "Hesabınız zaten onaylı. Yeniden başvuru gönderemezsiniz." }),
        { status: 409 },
      );
    }

    const { data: pendingExisting } = await serviceClient
      .from("student_verifications")
      .select("id")
      .eq("user_id", user.id)
      .eq("status", "PENDING")
      .maybeSingle();

    if (pendingExisting) {
      return new Response(
        JSON.stringify({ error: "Zaten incelenmekte olan bir başvurunuz var." }),
        { status: 409 },
      );
    }

    // status/user_id/created_at/updated_at İSTEMCİDEN kabul edilmez; hepsi
    // burada, service_role ile set edilir (bkz. §12 madde 14).
    const { error: insertError } = await serviceClient
      .from("student_verifications")
      .insert({ user_id: user.id, document_path: storagePath, status: "PENDING" });

    if (insertError) {
      console.error("submit-student-document insert error", insertError);
      return new Response(JSON.stringify({ error: "Başvurunuz gönderilemedi. Lütfen tekrar deneyin." }), { status: 500 });
    }

    console.log(JSON.stringify({ event: "submit_student_document", userId: user.id, success: true }));

    return new Response(JSON.stringify({ accepted: true }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  } catch (error) {
    console.error("submit-student-document unexpected error", error);
    return new Response(JSON.stringify({ error: "Başvurunuz gönderilemedi. Lütfen tekrar deneyin." }), { status: 500 });
  }
});
