// Google Play abonelik doğrulamasının SAF karar mantığı (ağ/DB yok) — Node'da test edilir (logic.test.ts).
// Girdi: Play Developer API `purchases.subscriptionsv2.get` yanıtı + beklenen ürün/kullanıcı. Çıktı: hak verilsin mi?

export interface PlaySubscription {
  subscriptionState?: string;
  acknowledgementState?: string;
  externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
  lineItems?: Array<{
    productId?: string;
    expiryTime?: string;
    autoRenewingPlan?: { autoRenewEnabled?: boolean };
  }>;
}

export type Decision =
  | { ok: true; expiresAt: string; autoRenewing: boolean; needsAcknowledge: boolean }
  | { ok: false; status: number; message: string };

/**
 * Erişim verilen durumlar: aktif, ödeme sorunu için tanınan ek süre (grace) ve iptal edilmiş ama süresi dolmamış abonelik.
 * ON_HOLD / PAUSED / EXPIRED / PENDING erişim vermez.
 */
const ENTITLED_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  "SUBSCRIPTION_STATE_CANCELED",
]);

export function decideEntitlement(
  subscription: PlaySubscription,
  context: { productId: string; userId: string; now: Date },
): Decision {
  const lineItem = subscription.lineItems?.find((item) => item.productId === context.productId);
  if (!lineItem) {
    return { ok: false, status: 400, message: "Bu satın alma seçtiğin plana ait değil." };
  }

  // Satın alma, oturum açan hesaba bağlanmış olmalı (istemci `obfuscatedAccountId` = kullanıcı kimliği verir).
  // Aksi halde başkasının jetonu kendi hesabına tanımlanabilirdi.
  if (subscription.externalAccountIdentifiers?.obfuscatedExternalAccountId !== context.userId) {
    return { ok: false, status: 403, message: "Bu satın alma bu hesaba ait değil." };
  }

  if (subscription.subscriptionState === "SUBSCRIPTION_STATE_PENDING") {
    return { ok: false, status: 402, message: "Ödemen henüz tamamlanmadı. Onaylanınca planın etkinleşecek." };
  }

  if (!subscription.subscriptionState || !ENTITLED_STATES.has(subscription.subscriptionState)) {
    return { ok: false, status: 403, message: "Aboneliğin süresi dolmuş veya askıda." };
  }

  const expiry = lineItem.expiryTime ? new Date(lineItem.expiryTime) : null;
  if (!expiry || Number.isNaN(expiry.getTime()) || expiry.getTime() <= context.now.getTime()) {
    return { ok: false, status: 403, message: "Aboneliğin süresi dolmuş veya askıda." };
  }

  return {
    ok: true,
    expiresAt: expiry.toISOString(),
    autoRenewing: lineItem.autoRenewingPlan?.autoRenewEnabled === true,
    needsAcknowledge: subscription.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING",
  };
}

const PRODUCT_ID_PATTERN = /^[A-Za-z0-9._-]{1,100}$/;
const MAX_TOKEN_LENGTH = 4096;

/** İstek gövdesini doğrular; geçersizse `null`. Jeton boşluk içeremez (URL yoluna yerleştirilir). */
export function parseRequest(body: unknown): { productId: string; purchaseToken: string } | null {
  if (typeof body !== "object" || body === null) return null;
  const { productId, purchaseToken } = body as Record<string, unknown>;
  if (typeof productId !== "string" || !PRODUCT_ID_PATTERN.test(productId)) return null;
  if (typeof purchaseToken !== "string" || purchaseToken.length === 0 || purchaseToken.length > MAX_TOKEN_LENGTH) return null;
  if (/\s/.test(purchaseToken)) return null;
  return { productId, purchaseToken };
}
