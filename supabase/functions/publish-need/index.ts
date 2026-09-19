// Supabase Edge Function: publish-need
// "Taslak göster -> Kullanıcı düzenler -> Yayınla -> Postgres" akışının son
// adımı (bkz. project-goals.md §22). İstemciden gelen (kullanıcı tarafından
// düzenlenmiş olabilecek) taslak asla ham güvenilmez: aynı .strict() şema ile
// SUNUCU TARAFINDA yeniden doğrulanır. university_id/author_id/status/
// timestamps yalnızca burada, service_role ile set edilir.

import { createClient } from "jsr:@supabase/supabase-js@2";
import { z } from "npm:zod@3.23.8";

const NEED_CATEGORIES = ["SPORTS", "ACADEMIC", "SOCIAL", "HOUSING", "TRANSPORT", "OTHER"] as const;
const NEED_HELP_TYPES = [
  "LOOKING_FOR_PEOPLE",
  "OFFERING_HELP",
  "LOOKING_FOR_ITEM",
  "OFFERING_ITEM",
  "LOOKING_FOR_INFO",
] as const;
const NEED_URGENCY_LEVELS = ["LOW", "NORMAL", "HIGH", "URGENT"] as const;
const RAW_TEXT_MAX_LENGTH = 500;
const RATE_LIMIT = 20; // taslak değer, netleşmedi
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000;

const ParsedNeedSchema = z
  .object({
    title: z.string().trim().min(1).max(80),
    category: z.enum(NEED_CATEGORIES),
    helpType: z.enum(NEED_HELP_TYPES),
    tags: z.array(z.string().trim().min(1).max(30)).max(6),
    participantCount: z.number().int().min(1).max(50).nullable(),
    urgency: z.enum(NEED_URGENCY_LEVELS),
    startsAt: z.string().datetime().nullable(),
    skills: z.array(z.string().trim().min(1).max(30)).max(6),
  })
  .strict();

async function enforceRateLimit(
  serviceClient: ReturnType<typeof createClient>,
  userId: string,
  bucket: string,
  limit: number,
  windowMs: number,
): Promise<{ ok: true } | { ok: false; message: string }> {
  const id = `${userId}:${bucket}`;
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
  if (now - windowStart > windowMs) {
    await serviceClient.from("rate_limits").update({ count: 1, window_start: new Date(now).toISOString() }).eq("id", id);
    return { ok: true };
  }
  if ((existing.count as number) >= limit) {
    return { ok: false, message: "Çok fazla istek gönderdiniz. Lütfen bir süre sonra tekrar deneyin." };
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

    const userClient = createClient(supabaseUrl, anonKey, { global: { headers: { Authorization: authHeader } } });
    const { data: { user }, error: userError } = await userClient.auth.getUser();
    if (userError || !user) {
      return new Response(JSON.stringify({ error: "Bu işlem için giriş yapmanız gerekiyor." }), { status: 401 });
    }

    const body = await req.json().catch(() => null);
    const rawText = body?.rawText;
    if (typeof rawText !== "string" || rawText.trim().length === 0 || rawText.length > RAW_TEXT_MAX_LENGTH) {
      return new Response(JSON.stringify({ error: "İhtiyaç metni geçersiz." }), { status: 400 });
    }

    const parsedDraft = ParsedNeedSchema.safeParse(body?.draft);
    if (!parsedDraft.success) {
      return new Response(JSON.stringify({ error: "İlan taslağı geçersiz veya eksik." }), { status: 400 });
    }

    const serviceClient = createClient(supabaseUrl, serviceRoleKey);

    const rateLimitResult = await enforceRateLimit(serviceClient, user.id, "publish-need", RATE_LIMIT, RATE_LIMIT_WINDOW_MS);
    if (!rateLimitResult.ok) {
      return new Response(JSON.stringify({ error: rateLimitResult.message }), { status: 429 });
    }

    // universityId İSTEMCİDEN ASLA kabul edilmez; kullanıcının kendi
    // profilinden okunur (bkz. AI_Guidelines §4.14 karşılığı).
    const { data: profile, error: profileError } = await serviceClient
      .from("profiles")
      .select("university_id, account_status, verification_status")
      .eq("id", user.id)
      .single();

    if (profileError || !profile?.university_id) {
      return new Response(
        JSON.stringify({ error: "Üniversite bilginiz eksik görünüyor. Lütfen profilinizi tamamlayın." }),
        { status: 412 },
      );
    }
    if (profile.account_status !== "ACTIVE" || profile.verification_status !== "APPROVED") {
      return new Response(
        JSON.stringify({ error: "Bu işlem için öğrenci doğrulamanızın onaylanmış olması gerekiyor." }),
        { status: 403 },
      );
    }

    const draft = parsedDraft.data;
    const { data: inserted, error: insertError } = await serviceClient
      .from("requirements")
      .insert({
        author_id: user.id,
        university_id: profile.university_id,
        raw_text: rawText,
        title: draft.title,
        category: draft.category,
        help_type: draft.helpType,
        tags: draft.tags,
        skills: draft.skills,
        participant_count: draft.participantCount,
        urgency: draft.urgency,
        starts_at: draft.startsAt,
        status: "PUBLISHED",
      })
      .select("id")
      .single();

    if (insertError || !inserted) {
      console.error("publish-need insert error", insertError);
      return new Response(JSON.stringify({ error: "İlanınız yayınlanamadı. Lütfen tekrar deneyin." }), { status: 500 });
    }

    console.log(JSON.stringify({ event: "publish_need", userId: user.id, requirementId: inserted.id, success: true }));

    return new Response(JSON.stringify({ requirementId: inserted.id }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  } catch (error) {
    console.error("publish-need unexpected error", error);
    return new Response(JSON.stringify({ error: "İlanınız yayınlanamadı. Lütfen tekrar deneyin." }), { status: 500 });
  }
});
