import { FetchApiTransport } from "./fetchTransport.ts";
import { VoiceSecureApiClient } from "./voiceSecureApiClient.ts";
import { ExpoSecureStoreDriver } from "../auth/expoSecureStoreDriver.ts";
import {
  NativeSecureTokenStore,
  SecureTokenVault,
  TokenSessionAccessTokenProvider,
  TokenSessionError,
} from "../auth/tokenSession.ts";

export function createMobileApiClient(): VoiceSecureApiClient {
  const store = new NativeSecureTokenStore({
    driver: new ExpoSecureStoreDriver(),
    options: {
      namespace: "com.voicesecure.wallet.session",
      encryptedAtRest: true,
      hardwareBacked: true,
      deviceOnly: true,
      biometricOrPasscodeRequired: true,
      synchronizesToCloud: false,
    },
  });
  const vault = new SecureTokenVault({ store });
  const tokenProvider = new TokenSessionAccessTokenProvider({
    vault,
    refresh: async () => {
      throw new TokenSessionError("AUTH_REFRESH_UNAVAILABLE", "Sign in again to continue.");
    },
  });
  return new VoiceSecureApiClient({
    tokenProvider,
    traceIdFactory: createTraceId,
    transport: new FetchApiTransport({
      baseUrl: process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://127.0.0.1:8080",
    }),
  });
}

function createTraceId(): string {
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `mobile-${random}`;
}
