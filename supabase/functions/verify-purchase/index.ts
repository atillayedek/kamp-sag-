// Supabase Edge Function: verify-purchase
//
// Google Play abonelik satın almasını SUNUCUDA doğrular ve kullanıcının hakkını (user_entitlements) yazar.
// İstemci hak yazamaz (RLS'te yazma politikası yok); tek yol bu fonksiyondur (service role).
//
// Akış: JWT -> kullanıcı; {productId, purchaseToken} doğrula -> ürünün planını bul (yalnızca etkin planlar) ->
//       Play Developer API `subscriptionsv2.get` -> logic.ts karar verir (ürün eşleşmesi, hesaba bağlılık, durum, süre) ->
//       gerekirse `acknowledge` (Play 3 gün içinde onaylanmayan aboneliği iade eder) -> hak upsert.
//
// Gerekli secret: GOOGLE_PLAY_SERVICE_ACCOUNT (Play Console'da yetkilendirilmiş service account JSON'u).
// İsteğe bağlı: ANDROID_PACKAGE_NAME (varsayılan com.kampusagi.android).
// Secret yoksa 503 döner — hak ASLA doğrulanmadan verilmez (docs/BLOCKERS.md B5).
//
// Yenileme/iptal bildirimleri (RTDN, Pub/Sub) YOK: hak `expires_at` ile sunucu saatinde sona erer; istemci uygulama
// açıldığında mevcut satın almayı yeniden doğrulatarak yenilemeyi alır.

import { createClient } from "jsr:@supabase/supabase-js@2";
import { decideEntitlement, parseRequest } from "./logic.ts";

const RATE_LIMIT = 30;
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000;
const ANDROID_PUBLISHER = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

const base64Url = (bytes: Uint8Array) =>
  btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");

const textToBase64Url = (text: string) => base64Url(new TextEncoder().encode(text));

function pemToDer(pem: string): Uint8Array {
  const body = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
}

/** Service account ile Play Developer API için kısa ömürlü OAuth erişim jetonu alır (RS256 JWT bearer). */
async function googleAccessToken(serviceAccount: { client_email: string; private_key: string }): Promise<string> {
  const issuedAt = Math.floor(Date.now() / 1000);
  const header = textToBase64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = textToBase64Url(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: issuedAt,
    exp: issuedAt + 3600,
  }));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  const assertion = `${header}.${claims}.${base64Url(new Uint8Array(signature))}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!response.ok) throw new Error(`Google OAuth reddetti: ${response.status}`);
  return (await response.json()).access_token as string;
}

async function enforceRateLimit(
  serviceClient: ReturnType<typeof createClient>,
  userId: string,
): Promise<boolean> {
  const id = `${userId}:verify-purchase`;
  const now = Date.now();
  const { data: existing } = await serviceClient.from("rate_limits").select("count, window_start").eq("id", id).maybeSingle();
  if (!existing) {
    await serviceClient.from("rate_limits").insert({ id, count: 1, window_start: new Date(now).toISOString() });
    return true;
  }
  if (now - new Date(existing.window_start as string).getTime() > RATE_LIMIT_WINDOW_MS) {
    await serviceClient.from("rate_limits").update({ count: 1, window_start: new Date(now).toISOString() }).eq("id", id);
    return true;
  }
  if ((existing.count as number) >= RATE_LIMIT) return false;
  await serviceClient.from("rate_limits").update({ count: (existing.count as number) + 1 }).eq("id", id);
  return true;
}

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

    const request = parseRequest(await req.json().catch(() => null));
    if (!request) return json(400, { error: "Satın alma bilgisi geçersiz." });

    const serviceAccountJson = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT");
    if (!serviceAccountJson) {
      console.error("verify-purchase: GOOGLE_PLAY_SERVICE_ACCOUNT tanımlı değil");
      return json(503, { error: "Satın alma doğrulaması şu an kullanılamıyor. Lütfen daha sonra tekrar deneyin." });
    }

    const serviceClient = createClient(supabaseUrl, serviceRoleKey);

    if (!(await enforceRateLimit(serviceClient, user.id))) {
      return json(429, { error: "Çok fazla istek gönderdiniz. Lütfen bir süre sonra tekrar deneyin." });
    }

    const { data: plan } = await serviceClient
      .from("subscription_plans")
      .select("id")
      .eq("play_product_id", request.productId)
      .eq("is_active", true)
      .maybeSingle();
    if (!plan) return json(404, { error: "Bu plan şu an satın alınamıyor." });

    const packageName = Deno.env.get("ANDROID_PACKAGE_NAME") ?? "com.kampusagi.android";
    const accessToken = await googleAccessToken(JSON.parse(serviceAccountJson));
    const bearer = { Authorization: `Bearer ${accessToken}` };

    const tokenPath = encodeURIComponent(request.purchaseToken);
    const playResponse = await fetch(`${ANDROID_PUBLISHER}/${packageName}/purchases/subscriptionsv2/tokens/${tokenPath}`, {
      headers: bearer,
    });
    if (playResponse.status === 400 || playResponse.status === 404 || playResponse.status === 410) {
      return json(400, { error: "Satın alma doğrulanamadı." });
    }
    if (!playResponse.ok) {
      // 401/403: service account Play Console'da yetkili değil; 5xx: Google tarafı. İkisi de kullanıcı hatası değil.
      console.error("verify-purchase Play API hatası", playResponse.status, await playResponse.text());
      return json(503, { error: "Satın alma doğrulaması şu an kullanılamıyor. Lütfen daha sonra tekrar deneyin." });
    }

    const decision = decideEntitlement(await playResponse.json(), {
      productId: request.productId,
      userId: user.id,
      now: new Date(),
    });
    if (!decision.ok) return json(decision.status, { error: decision.message });

    if (decision.needsAcknowledge) {
      const ack = await fetch(
        `${ANDROID_PUBLISHER}/${packageName}/purchases/subscriptions/${encodeURIComponent(request.productId)}/tokens/${tokenPath}:acknowledge`,
        { method: "POST", headers: { ...bearer, "content-type": "application/json" }, body: "{}" },
      );
      if (!ack.ok) {
        console.error("verify-purchase acknowledge hatası", ack.status, await ack.text());
        return json(502, { error: "Satın alma onaylanamadı. Lütfen tekrar deneyin." });
      }
    }

    const { error: upsertError } = await serviceClient.from("user_entitlements").upsert({
      user_id: user.id,
      plan_id: plan.id,
      play_product_id: request.productId,
      purchase_token: request.purchaseToken,
      expires_at: decision.expiresAt,
      auto_renewing: decision.autoRenewing,
      updated_at: new Date().toISOString(),
    }, { onConflict: "user_id" });
    if (upsertError) {
      if (upsertError.code === "23505") return json(409, { error: "Bu satın alma başka bir hesaba tanımlı." });
      console.error("verify-purchase upsert hatası", upsertError);
      return json(500, { error: "Planın etkinleştirilemedi. Lütfen tekrar deneyin." });
    }

    console.log(JSON.stringify({ event: "verify_purchase", userId: user.id, plan: plan.id, success: true }));
    return json(200, { plan: plan.id, expiresAt: decision.expiresAt });
  } catch (error) {
    console.error("verify-purchase unexpected error", error);
    return json(500, { error: "Planın etkinleştirilemedi. Lütfen tekrar deneyin." });
  }
});
