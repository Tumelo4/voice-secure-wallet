import assert from "node:assert/strict";
import test from "node:test";

import {
  ApiClientError,
  VoiceSecureApiClient,
  type ApiTransport,
  type ApiTransportRequest,
} from "../src/api/voiceSecureApiClient.ts";
import { FetchApiTransport, type FetchLike } from "../src/api/fetchTransport.ts";
import type { AccessTokenProvider } from "../src/auth/tokenProvider.ts";

test("fetch transport prefixes the base URL and forwards request fields", async () => {
  const calls: Array<{ input: string; init: { method?: string; headers?: Record<string, string>; body?: string } }> = [];
  const fetcher: FetchLike = async (input, init) => {
    calls.push({ input, init });
    return fetchResponse(202, JSON.stringify({ accepted: true }), { "x-request-id": "req-1" });
  };
  const transport = new FetchApiTransport({ baseUrl: "https://api.voice.local/v1", fetcher });

  const response = await transport.send({
    method: "POST",
    path: "/payments",
    headers: { "Content-Type": "application/json", "X-Trace-Id": "trace-1" },
    body: JSON.stringify({ amount: 250 }),
  });

  assert.equal(calls[0].input, "https://api.voice.local/v1/payments");
  assert.deepEqual(calls[0].init, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Trace-Id": "trace-1" },
    body: JSON.stringify({ amount: 250 }),
  });
  assert.equal(response.status, 202);
  assert.equal(response.body, JSON.stringify({ accepted: true }));
  assert.equal(response.headers?.["x-request-id"], "req-1");
});

test("fetch transport joins base URLs and paths without duplicate slashes", async () => {
  let requestedUrl = "";
  const fetcher: FetchLike = async (input) => {
    requestedUrl = input;
    return fetchResponse(200, JSON.stringify({ balance: 1250 }), {});
  };
  const transport = new FetchApiTransport({ baseUrl: "https://api.voice.local/", fetcher });

  await transport.send({ method: "GET", path: "wallets/wallet-1/balance", headers: {} });

  assert.equal(requestedUrl, "https://api.voice.local/wallets/wallet-1/balance");
});

test("fetch transport maps network failures to deterministic API errors", async () => {
  const transport = new FetchApiTransport({
    baseUrl: "https://api.voice.local",
    fetcher: async () => {
      throw new Error("ECONNREFUSED");
    },
  });

  await assert.rejects(
    () => transport.send({ method: "GET", path: "/wallets/wallet-1/balance", headers: {} }),
    (error) => {
      assert.ok(error instanceof ApiClientError);
      assert.equal(error.status, 503);
      assert.equal(error.code, "NETWORK_UNAVAILABLE");
      assert.equal(error.message, "network unavailable");
      return true;
    }
  );
});

test("API client resolves a fresh token provider value for each request", async () => {
  const transport = new RecordingTransport({
    status: 200,
    body: JSON.stringify({
      accountId: "wallet-1",
      currency: "ZAR",
      balance: 1250,
      version: 7,
      updatedAt: "2026-07-02T12:00:00Z",
    }),
  });
  const tokenProvider = new RotatingTokenProvider(["token-one", "token-two"]);
  const client = new VoiceSecureApiClient({
    tokenProvider,
    traceIdFactory: () => "trace-token-1",
    transport,
  });

  await client.getWalletBalance("wallet-1");
  await client.getWalletBalance("wallet-1");

  assert.equal(transport.requests[0].headers.Authorization, "Bearer token-one");
  assert.equal(transport.requests[1].headers.Authorization, "Bearer token-two");
});

class RecordingTransport implements ApiTransport {
  readonly requests: ApiTransportRequest[] = [];
  private readonly response: { status: number; body: string; headers?: Record<string, string> };

  constructor(response: { status: number; body: string; headers?: Record<string, string> }) {
    this.response = response;
  }

  async send(request: ApiTransportRequest) {
    this.requests.push(request);
    return this.response;
  }
}

class RotatingTokenProvider implements AccessTokenProvider {
  private readonly tokens: string[];

  constructor(tokens: string[]) {
    this.tokens = tokens;
  }

  async getAccessToken(): Promise<string> {
    const token = this.tokens.shift();
    if (!token) {
      throw new Error("missing test token");
    }
    return token;
  }
}

function fetchResponse(status: number, body: string, headers: Record<string, string>) {
  return {
    status,
    headers: {
      forEach(callback: (value: string, key: string) => void) {
        Object.entries(headers).forEach(([key, value]) => callback(value, key));
      },
    },
    async text() {
      return body;
    },
  };
}
