import assert from "node:assert/strict";
import test from "node:test";
import worker from "./worker.mjs";

const payload = { userId: "test/user", receiptId: "test/receipt" };
const receipt = {
  receiptId: payload.receiptId,
  productId: "carehando_premium",
  productType: "ENTITLED",
  cancelDate: null,
  testTransaction: false,
};
const env = {
  AMAZON_RVS_SECRET: "synthetic/test-key",
  VERIFICATION_LIMIT: { limit: async () => ({ success: true }) },
};

function request(body = JSON.stringify(payload)) {
  return new Request("https://carehando.example/verify", { method: "POST", body });
}

test("rejects other routes, methods, invalid and oversized bodies before calling Amazon", async (t) => {
  const upstream = t.mock.method(globalThis, "fetch");
  assert.equal((await worker.fetch(new Request("https://carehando.example/"), env)).status, 404);
  const get = await worker.fetch(new Request("https://carehando.example/verify"), env);
  assert.equal(get.status, 405);
  assert.equal(get.headers.get("allow"), "POST");
  for (const body of ["{}", "null", "?", JSON.stringify({ ...payload, userId: ".." }), "x".repeat(4097)]) {
    assert.equal((await worker.fetch(request(body), env)).status, 400);
  }
  assert.equal((await worker.fetch(request(), { ...env, AMAZON_RVS_SECRET: "" })).status, 503);
  assert.equal((await worker.fetch(request(), {
    ...env,
    VERIFICATION_LIMIT: { limit: async () => ({ success: false }) },
  })).status, 429);
  assert.equal(upstream.mock.callCount(), 0);
});

test("accepts an active Carehando entitlement and opts into Amazon beta receipts for LAT", async (t) => {
  let amazonReceipt = receipt;
  t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "https://appstore-sdk.amazon.com/version/1.0/verifyReceiptId" +
      "/developer/synthetic%2Ftest-key/user/test%2Fuser/receiptId/test%2Freceipt");
    assert.equal(options.redirect, "manual");
    return Response.json(amazonReceipt);
  });
  const verify = async (overrides = {}) => {
    const response = await worker.fetch(request(), { ...env, ...overrides });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("cache-control"), "no-store");
    return (await response.json()).entitled;
  };
  assert.equal(await verify(), true);
  const lat = { AMAZON_RVS_ALLOW_BETA: "1" };
  for (const change of [
    { productId: "other" }, { productType: "CONSUMABLE" }, { cancelDate: 1 },
    { receiptId: "other" }, { testTransaction: true },
  ]) {
    amazonReceipt = { ...receipt, ...change };
    assert.equal(await verify(lat), false);
  }
  amazonReceipt = { ...receipt, betaProduct: true, testTransaction: true };
  assert.equal(await verify(), false);
  assert.equal(await verify(lat), true);
  amazonReceipt = { ...amazonReceipt, cancelDate: 1 };
  assert.equal(await verify(lat), false);
});

test("distinguishes revoked receipts from upstream failures", async (t) => {
  let status = 410;
  t.mock.method(globalThis, "fetch", async () => new Response("", { status }));
  const revoked = await worker.fetch(request(), env);
  assert.equal(revoked.status, 200);
  assert.deepEqual(await revoked.json(), { entitled: false });
  for (status of [200, 301, 429, 500]) {
    assert.equal((await worker.fetch(request(), env)).status, 502);
  }
});
