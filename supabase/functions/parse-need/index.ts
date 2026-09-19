// Supabase Edge Function: parse-need
// istemci -> parse-need -> Claude API -> strict JSON validation -> ParsedNeed taslağı
// (bkz. project-goals.md §22, AI_Guidelines.md §4). Bu fonksiyon Postgres'e
// YAZMAZ — yalnızca doğrulanmış bir taslak döner (publish-need ayrı adımdır).

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
const RATE_LIMIT = 10; // 10 istek / 10 dakika — taslak değer, netleşmedi
const RATE_LIMIT_WINDOW_MS = 10 * 60 * 1000;

// Claude çıktı şeması: .strict() -> beklenmeyen alanları reddeder
// (score/admin/isVerified/accountStatus/userId/ownerId/universityId/permissions
// dahil hiçbir güvenlik-kritik alan burada YOKTUR, bu yüzden asla kabul edilmezler).
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

type ParsedNeed = z.infer<typeof ParsedNeedSchema>;

const SYSTEM_PROMPT = `You are a structured intent parser for a Turkish university student community application.

Return JSON only. Do not include any explanation, markdown formatting, or text outside the JSON object.

The JSON object must have exactly these fields: title, category, helpType, tags, participantCount, urgency, startsAt, skills.

category must be exactly one of: ${NEED_CATEGORIES.join(", ")}.
helpType must be exactly one of: ${NEED_HELP_TYPES.join(", ")}.
urgency must be exactly one of: ${NEED_URGENCY_LEVELS.join(", ")}.

Never invent:
- user IDs
- university IDs
- permissions
- account status
- security roles
- reputation values

Only extract information present in the user's text. If information is unknown, use null for participantCount and startsAt, and an empty array for tags and skills.

startsAt must be either null or a valid ISO 8601 datetime string.
Keep title under 80 characters. Keep each tag and skill under 30 characters, at most 6 items each.`;

function sanitizeControlCharacters(value: string): string {
  return value.replace(new RegExp("[\\u0000-\\u001F\\u007F]", "g"), "").trim();
}

function sanitizeStringFields(candidate: unknown): unknown {
  if (Array.isArray(candidate)) return candidate.map(sanitizeStringFields);
  if (typeof candidate === "string") return sanitizeControlCharacters(candidate);
  if (candidate !== null && typeof candidate === "object") {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(candidate as Record<string, unknown>)) {
      out[k] = sanitizeStringFields(v);
    }
    return out;
  }
  return candidate;
}

const CATEGORY_KEYWORDS: Record<string, ParsedNeed["category"]> = {
  basketbol: "SPORTS", futbol: "SPORTS", voleybol: "SPORTS", koşu: "SPORTS", spor: "SPORTS",
  python: "ACADEMIC", matematik: "ACADEMIC", fizik: "ACADEMIC", proje: "ACADEMIC", ders: "ACADEMIC", kodlama: "ACADEMIC",
  ev: "HOUSING", oda: "HOUSING", kiralık: "HOUSING", yurt: "HOUSING",
  araç: "TRANSPORT", araba: "TRANSPORT", otobüs: "TRANSPORT", yolculuk: "TRANSPORT",
  sohbet: "SOCIAL", arkadaş: "SOCIAL",
};

function fallbackParseNeed(rawText: string): ParsedNeed {
  const trimmed = rawText.trim();
  const lower = trimmed.toLocaleLowerCase("tr-TR");
  const tags = Object.keys(CATEGORY_KEYWORDS).filter((k) => lower.includes(k)).slice(0, 6);
  const category = (Object.entries(CATEGORY_KEYWORDS).find(([k]) => lower.includes(k))?.[1] ?? "OTHER");
  const participantMatch = trimmed.match(/(\d+)\s*kişi/);
  const participantCount = participantMatch
    ? Math.min(50, Math.max(1, Number(participantMatch[1]) || 1))
    : null;

  return {
    title: trimmed.slice(0, 80),
    category,
    helpType: "LOOKING_FOR_PEOPLE",
    tags,
    participantCount,
    urgency: "NORMAL",
    startsAt: null,
    skills: tags,
  };
}

async function enforceRateLimit(
  serviceClient: ReturnType<typeof createClient>,
  userId: string,
  bucket: string,
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
  if (now - windowStart > RATE_LIMIT_WINDOW_MS) {
    await serviceClient.from("rate_limits").update({ count: 1, window_start: new Date(now).toISOString() }).eq("id", id);
    return { ok: true };
  }

  if ((existing.count as number) >= RATE_LIMIT) {
    return { ok: false, message: "Çok fazla istek gönderdiniz. Lütfen bir süre sonra tekrar deneyin." };
  }

  await serviceClient.from("rate_limits").update({ count: (existing.count as number) + 1 }).eq("id", id);
  return { ok: true };
}

async function callClaude(rawText: string): Promise<{ text: string } | { error: string }> {
  const apiKey = Deno.env.get("ANTHROPIC_API_KEY");
  const model = Deno.env.get("ANTHROPIC_MODEL");
  if (!apiKey || !model) {
    return { error: "AI servisi yapılandırılmamış." };
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15_000);

  try {
    const response = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-api-key": apiKey,
        "anthropic-version": "2023-06-01",
      },
      body: JSON.stringify({
        model,
        max_tokens: 1024,
        system: SYSTEM_PROMPT,
        messages: [{ role: "user", content: `User's need text (Turkish):\n"""\n${rawText}\n"""` }],
      }),
      signal: controller.signal,
    });

    if (!response.ok) {
      return { error: `Claude API hatası (${response.status}).` };
    }

    const json = await response.json();
    const block = json.content?.[0];
    if (!block || block.type !== "text") {
      return { error: "Claude yanıtı beklenen biçimde değil." };
    }
    return { text: block.text as string };
  } catch (error) {
    const isAbort = error instanceof DOMException && error.name === "AbortError";
    return { error: isAbort ? "Claude API zaman aşımına uğradı." : "Claude API ile iletişimde hata." };
  } finally {
    clearTimeout(timeout);
  }
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
    if (typeof rawText !== "string" || rawText.trim().length === 0) {
      return new Response(JSON.stringify({ error: "İhtiyaç metni boş olamaz." }), { status: 400 });
    }
    if (rawText.length > RAW_TEXT_MAX_LENGTH) {
      return new Response(
        JSON.stringify({ error: `İhtiyaç metni en fazla ${RAW_TEXT_MAX_LENGTH} karakter olabilir.` }),
        { status: 400 },
      );
    }

    // rate_limits tablosuna RLS ile hiçbir istemci erişemez (deny-all) —
    // burada service_role ile bilinçli olarak bypass ediliyor.
    const serviceClient = createClient(supabaseUrl, serviceRoleKey);

    // Maliyet kontrolü: yalnızca onaylı öğrenciler AI analizi kullanabilir (onaysız hesap Claude bütçesini tüketemez).
    const { data: profile } = await serviceClient
      .from("profiles")
      .select("account_status, verification_status")
      .eq("id", user.id)
      .maybeSingle();
    if (profile?.account_status !== "ACTIVE" || profile?.verification_status !== "APPROVED") {
      return new Response(
        JSON.stringify({ error: "Bu işlem için öğrenci doğrulamanızın onaylanmış olması gerekiyor." }),
        { status: 403 },
      );
    }

    const rateLimitResult = await enforceRateLimit(serviceClient, user.id, "parse-need");
    if (!rateLimitResult.ok) {
      return new Response(JSON.stringify({ error: rateLimitResult.message }), { status: 429 });
    }

    const claudeResult = await callClaude(rawText);
    let need: ParsedNeed;
    let source: "claude" | "fallback";

    if ("error" in claudeResult) {
      const fallback = ParsedNeedSchema.safeParse(fallbackParseNeed(rawText));
      if (!fallback.success) {
        return new Response(
          JSON.stringify({ error: "İlanınız otomatik olarak ayrıştırılamadı. Bilgilerinizi kontrol edip tekrar deneyin." }),
          { status: 500 },
        );
      }
      need = fallback.data;
      source = "fallback";
    } else {
      try {
        const rawJson = JSON.parse(claudeResult.text);
        const sanitized = sanitizeStringFields(rawJson);
        need = ParsedNeedSchema.parse(sanitized);
        source = "claude";
      } catch {
        const fallback = ParsedNeedSchema.safeParse(fallbackParseNeed(rawText));
        if (!fallback.success) {
          return new Response(
            JSON.stringify({ error: "İlanınız otomatik olarak ayrıştırılamadı. Bilgilerinizi kontrol edip tekrar deneyin." }),
            { status: 500 },
          );
        }
        need = fallback.data;
        source = "fallback";
      }
    }

    // Loglama: yalnızca teknik metadata (bkz. AI_Guidelines §4.13) — rawText
    // veya Claude ham çıktısı BURADA loglanmaz.
    console.log(JSON.stringify({ event: "parse_need", userId: user.id, source, success: true }));

    return new Response(JSON.stringify({ need, source }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  } catch (error) {
    console.error("parse-need unexpected error", error);
    return new Response(
      JSON.stringify({ error: "İlanınız otomatik olarak ayrıştırılamadı. Bilgilerinizi kontrol edip tekrar deneyin." }),
      { status: 500 },
    );
  }
});
