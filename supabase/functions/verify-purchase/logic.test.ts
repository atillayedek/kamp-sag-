// Çalıştırma: `node --test supabase/functions/verify-purchase/logic.test.ts` (Node 22.18+/24: TypeScript doğrudan çalışır)
import assert from "node:assert/strict";
import { test } from "node:test";
import { decideEntitlement, parseRequest, type PlaySubscription } from "./logic.ts";

const NOW = new Date("2026-09-19T12:00:00Z");
const USER = "11111111-1111-1111-1111-111111111111";
const PRODUCT = "kampusagi_premium";

function subscription(overrides: Partial<PlaySubscription> = {}): PlaySubscription {
  return {
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    externalAccountIdentifiers: { obfuscatedExternalAccountId: USER },
    lineItems: [{ productId: PRODUCT, expiryTime: "2026-10-19T12:00:00Z", autoRenewingPlan: { autoRenewEnabled: true } }],
    ...overrides,
  };
}

const context = { productId: PRODUCT, userId: USER, now: NOW };

test("aktif ve kullanıcıya bağlı abonelik hak verir", () => {
  const decision = decideEntitlement(subscription(), context);
  assert.deepEqual(decision, { ok: true, expiresAt: "2026-10-19T12:00:00.000Z", autoRenewing: true, needsAcknowledge: false });
});

test("onaylanmamış (acknowledge bekleyen) satın alma için onay istenir", () => {
  const decision = decideEntitlement(subscription({ acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING" }), context);
  assert.equal(decision.ok && decision.needsAcknowledge, true);
});

test("ek süre (grace) ve iptal edilmiş ama süresi dolmamış abonelik erişim verir", () => {
  for (const state of ["SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"]) {
    assert.equal(decideEntitlement(subscription({ subscriptionState: state }), context).ok, true, state);
  }
});

test("otomatik yenileme kapalıysa autoRenewing false olur", () => {
  const decision = decideEntitlement(
    subscription({ lineItems: [{ productId: PRODUCT, expiryTime: "2026-10-19T12:00:00Z" }] }),
    context,
  );
  assert.equal(decision.ok && decision.autoRenewing, false);
});

test("başka hesaba bağlı jeton reddedilir (jeton yeniden oynatma)", () => {
  const decision = decideEntitlement(
    subscription({ externalAccountIdentifiers: { obfuscatedExternalAccountId: "22222222-2222-2222-2222-222222222222" } }),
    context,
  );
  assert.deepEqual(decision, { ok: false, status: 403, message: "Bu satın alma bu hesaba ait değil." });
});

test("hesap kimliği hiç bağlanmamışsa reddedilir", () => {
  assert.equal(decideEntitlement(subscription({ externalAccountIdentifiers: undefined }), context).ok, false);
  assert.equal(decideEntitlement(subscription({ externalAccountIdentifiers: {} }), context).ok, false);
});

test("istenen ürünle eşleşmeyen jeton reddedilir", () => {
  const decision = decideEntitlement(subscription(), { ...context, productId: "kampusagi_community_pro" });
  assert.equal(decision.ok, false);
  assert.equal(!decision.ok && decision.status, 400);
});

test("ürün satırı olmayan yanıt reddedilir", () => {
  assert.equal(decideEntitlement(subscription({ lineItems: [] }), context).ok, false);
  assert.equal(decideEntitlement(subscription({ lineItems: undefined }), context).ok, false);
});

test("bekleyen ödeme 402 döner ve hak vermez", () => {
  const decision = decideEntitlement(subscription({ subscriptionState: "SUBSCRIPTION_STATE_PENDING" }), context);
  assert.equal(!decision.ok && decision.status, 402);
});

test("askıda, duraklatılmış ve süresi dolmuş abonelik erişim vermez", () => {
  for (const state of ["SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED", "SUBSCRIPTION_STATE_EXPIRED", "BILINMEYEN", undefined]) {
    const decision = decideEntitlement(subscription({ subscriptionState: state }), context);
    assert.equal(decision.ok, false, String(state));
    assert.equal(!decision.ok && decision.status, 403, String(state));
  }
});

test("bitiş zamanı geçmiş, eksik veya bozuk ise erişim verilmez (durum aktif görünse bile)", () => {
  for (const expiryTime of ["2026-09-19T12:00:00Z", "2026-09-01T00:00:00Z", undefined, "yarın"]) {
    const decision = decideEntitlement(subscription({ lineItems: [{ productId: PRODUCT, expiryTime }] }), context);
    assert.equal(decision.ok, false, String(expiryTime));
  }
});

test("istek gövdesi doğrulaması", () => {
  assert.deepEqual(parseRequest({ productId: "kampusagi_premium", purchaseToken: "abc.DEF-123_xyz" }), {
    productId: "kampusagi_premium",
    purchaseToken: "abc.DEF-123_xyz",
  });
  assert.equal(parseRequest(null), null);
  assert.equal(parseRequest("metin"), null);
  assert.equal(parseRequest({}), null);
  assert.equal(parseRequest({ productId: "kampusagi_premium" }), null);
  assert.equal(parseRequest({ productId: "../etc/passwd", purchaseToken: "abc" }), null);
  assert.equal(parseRequest({ productId: "x y", purchaseToken: "abc" }), null);
  assert.equal(parseRequest({ productId: "kampusagi_premium", purchaseToken: "" }), null);
  assert.equal(parseRequest({ productId: "kampusagi_premium", purchaseToken: "a b" }), null);
  assert.equal(parseRequest({ productId: "kampusagi_premium", purchaseToken: "a/b\nc" }), null);
  assert.equal(parseRequest({ productId: "kampusagi_premium", purchaseToken: "a".repeat(4097) }), null);
  assert.equal(parseRequest({ productId: 5, purchaseToken: "abc" }), null);
});
