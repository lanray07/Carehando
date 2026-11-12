const PRODUCT_ID = "carehando_premium";

function reply(status, body = {}, headers = {}) {
  return Response.json(body, {
    status,
    headers: { "Cache-Control": "no-store", ...headers },
  });
}

async function readJson(message, limit) {
  const reader = message.body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let text = "";
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > limit) throw new RangeError("JSON exceeds size limit");
      text += decoder.decode(value, { stream: true });
    }
    return JSON.parse(text + decoder.decode());
  } catch (error) {
    await reader.cancel().catch(() => {});
    throw error;
  } finally {
    reader.releaseLock();
  }
}

function validIdentifier(value) {
  return typeof value === "string" && value.length > 0 && value.length <= 1024 &&
    value !== "." && value !== ".." && value.isWellFormed();
}

export default {
  async fetch(request, env) {
    if (new URL(request.url).pathname !== "/verify") return reply(404);
    if (request.method !== "POST") return reply(405, {}, { Allow: "POST" });

    let userId;
    let receiptId;
    try {
      ({ userId, receiptId } = await readJson(request, 4096));
    } catch {
      return reply(400);
    }
    if (!validIdentifier(userId) || !validIdentifier(receiptId)) return reply(400);
    if (!env.AMAZON_RVS_SECRET) return reply(503);

    try {
      const key = request.headers.get("CF-Connecting-IP") || "unknown";
      const { success } = await env.VERIFICATION_LIMIT.limit({ key });
      if (!success) return reply(429);

      const sandbox = env.AMAZON_RVS_SANDBOX === "1";
      const prefix = sandbox ? "/sandbox" : "";
      const upstream = await fetch(
        `https://appstore-sdk.amazon.com${prefix}/version/1.0/verifyReceiptId` +
          `/developer/${encodeURIComponent(env.AMAZON_RVS_SECRET)}` +
          `/user/${encodeURIComponent(userId)}/receiptId/${encodeURIComponent(receiptId)}`,
        { redirect: "manual", cache: "no-store", signal: AbortSignal.timeout(5000) },
      );
      if (upstream.status !== 200) {
        await upstream.body?.cancel();
        return upstream.status === 410 ? reply(200, { entitled: false }) : reply(502);
      }

      const purchase = await readJson(upstream, 16_384);
      if (!purchase || typeof purchase !== "object" || !Object.hasOwn(purchase, "cancelDate")) {
        return reply(502);
      }
      const beta = purchase.betaProduct === true;
      const live = purchase.testTransaction === false && !beta;
      const entitled = purchase.receiptId === receiptId &&
        purchase.productId === PRODUCT_ID &&
        purchase.productType === "ENTITLED" &&
        purchase.cancelDate === null &&
        (sandbox || live || (env.AMAZON_RVS_ALLOW_BETA === "1" && beta));
      return reply(200, { entitled });
    } catch {
      return reply(502);
    }
  },
};
