// Supabase Edge Function: recompute-matches
//
// ⚠️ ÖNEMLİ, AÇIKÇA BELİRTİLMESİ GEREKEN SINIRLAMA (uydurulmadı, gizlenmedi):
// matching_config.semantic_weight (%60) pgvector embedding gerektirir.
// Anthropic Claude'un embedding endpoint'i YOKTUR; OpenAI/Gemini bu projede
// kesinlikle kullanılamaz (kullanıcı talimatı). Bir embedding sağlayıcısı
// (ör. Voyage AI) onaylanana kadar semantic bileşeni burada HESAPLANMAZ (0
// olarak işaretlenir, score_breakdown'da "NOT_IMPLEMENTED" ile görünür).
// profile_weight/trust_weight/activity_weight de aynı şekilde HENÜZ
// hesaplanmıyor çünkü bunları besleyecek bir kullanıcı ilgi alanı/itibar
// veri modeli bu şema turunda tanımlanmadı (profiles tablosunda böyle bir
// alan yok, uydurulmadı). Yalnızca help_type_weight (kategori eşleşmesi) ve
// distance_weight (burada "aynı bölüm" yakınlık vekili olarak kullanıldı,
// gerçek coğrafi mesafe değil) GERÇEKTEN hesaplanıyor.
//
// Bu fonksiyon çalışır ve gerçek veri üretir, ama TAM eşleşme motoru DEĞİLDİR
// — bkz. memory-bank/Memory_Bank.md ve işlem sonu raporu.

import { createClient } from "jsr:@supabase/supabase-js@2";

function jaccard(a: string[], b: string[]): number {
  const setA = new Set(a.map((s) => s.toLowerCase()));
  const setB = new Set(b.map((s) => s.toLowerCase()));
  if (setA.size === 0 || setB.size === 0) return 0;
  let intersection = 0;
  for (const item of setA) if (setB.has(item)) intersection++;
  const union = new Set([...setA, ...setB]).size;
  return union === 0 ? 0 : intersection / union;
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
    const requirementId = body?.requirementId;
    if (typeof requirementId !== "string") {
      return new Response(JSON.stringify({ error: "requirementId gerekli." }), { status: 400 });
    }

    // RLS'ye tabi (kullanıcı JWT'siyle): yalnızca kendi üniversitesindeki
    // requirement'ları görebilir. Sahiplik burada ayrıca doğrulanır.
    const { data: requirement, error: reqError } = await userClient
      .from("requirements")
      .select("id, author_id, university_id, category, help_type, tags, skills")
      .eq("id", requirementId)
      .single();

    if (reqError || !requirement) {
      return new Response(JSON.stringify({ error: "İlan bulunamadı." }), { status: 404 });
    }
    if (requirement.author_id !== user.id) {
      return new Response(JSON.stringify({ error: "Bu işlemi yalnızca ilan sahibi yapabilir." }), { status: 403 });
    }

    const serviceClient = createClient(supabaseUrl, serviceRoleKey);

    const { data: config } = await serviceClient
      .from("matching_config")
      .select("*")
      .single();

    const helpTypeWeight = Number(config?.help_type_weight ?? 0.10);
    const distanceWeight = Number(config?.distance_weight ?? 0.15);
    // semantic/profile/trust/activity ağırlıkları BİLEREK kullanılmıyor —
    // bkz. dosya başındaki not.

    const { data: authorProfile } = await serviceClient
      .from("profiles")
      .select("department")
      .eq("id", user.id)
      .single();

    // Aday havuzu: aynı üniversitede, kendisi hariç, açık başka bir ihtiyaç
    // paylaşmış öğrenciler (gerçek "kullanıcı ilgi alanı" alanı yok; en
    // yakın gerçek sinyal budur — bkz. dosya başındaki not).
    const { data: candidates } = await serviceClient
      .from("requirements")
      .select("author_id, category, tags, skills, profiles!inner(department)")
      .eq("university_id", requirement.university_id)
      .eq("status", "PUBLISHED")
      .neq("author_id", user.id)
      .limit(100);

    const results: { matched_user_id: string; score: number; score_breakdown: Record<string, unknown> }[] = [];
    const seen = new Set<string>();

    for (const candidate of candidates ?? []) {
      if (seen.has(candidate.author_id)) continue;
      seen.add(candidate.author_id);

      const categoryMatch = candidate.category === requirement.category ? 1 : 0.3;
      const tagOverlap = jaccard(
        [...(requirement.tags ?? []), ...(requirement.skills ?? [])],
        [...(candidate.tags ?? []), ...(candidate.skills ?? [])],
      );
      const candidateDepartment = (candidate as unknown as { profiles: { department: string | null } }).profiles?.department;
      const sameDepartment = candidateDepartment && candidateDepartment === authorProfile?.department ? 1 : 0.4;
      const distanceProxy = Math.max(tagOverlap, sameDepartment * 0.5);

      const score = Math.round((helpTypeWeight * categoryMatch + distanceWeight * distanceProxy) * 100);

      results.push({
        matched_user_id: candidate.author_id,
        score,
        score_breakdown: {
          category_match: categoryMatch,
          tag_overlap: tagOverlap,
          same_department: candidateDepartment === authorProfile?.department,
          semantic_similarity: "NOT_IMPLEMENTED",
          profile_compatibility: "NOT_IMPLEMENTED",
          trust_score: "NOT_IMPLEMENTED",
          activity_score: "NOT_IMPLEMENTED",
          max_possible_score_given_current_implementation: Math.round((helpTypeWeight + distanceWeight) * 100),
        },
      });
    }

    results.sort((a, b) => b.score - a.score);
    const top = results.slice(0, 10);

    for (const match of top) {
      await serviceClient
        .from("matches")
        .upsert(
          {
            requirement_id: requirementId,
            matched_user_id: match.matched_user_id,
            score: match.score,
            score_breakdown: match.score_breakdown,
          },
          { onConflict: "requirement_id,matched_user_id" },
        );
    }

    console.log(JSON.stringify({ event: "recompute_matches", requirementId, matchCount: top.length }));

    return new Response(JSON.stringify({ matches: top.length }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  } catch (error) {
    console.error("recompute-matches unexpected error", error);
    return new Response(JSON.stringify({ error: "Eşleşmeler hesaplanamadı." }), { status: 500 });
  }
});
