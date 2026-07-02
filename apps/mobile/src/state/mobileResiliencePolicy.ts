import { ApiClientError, type PaymentStartResult, type StartPaymentCommand } from "../api/voiceSecureApiClient.ts";
import type { ApiRequestError } from "./apiRequestModel.ts";

export interface RetryPolicy {
  maxAttempts: number;
  baseDelayMs: number;
  maxDelayMs: number;
  retryableStatuses: number[];
  retryableCodes: string[];
}

export interface RetryDecision {
  shouldRetry: boolean;
  delayMs?: number;
  reason: string;
}

export interface QueuedPaymentCommand {
  command: StartPaymentCommand;
  queuedAt: string;
  attempts: number;
}

export interface OfflinePaymentQueueState {
  payments: QueuedPaymentCommand[];
  maxDepth: number;
}

export interface OfflinePaymentClientPort {
  startPayment(command: StartPaymentCommand): Promise<PaymentStartResult>;
}

export interface OfflineDrainDependencies {
  client: OfflinePaymentClientPort;
  retryPolicy?: RetryPolicy;
}

export interface OfflineDrainResult {
  state: OfflinePaymentQueueState;
  sent: number;
  blocked: boolean;
  retry?: RetryDecision;
}

export const defaultRetryPolicy: RetryPolicy = {
  maxAttempts: 4,
  baseDelayMs: 500,
  maxDelayMs: 8_000,
  retryableStatuses: [408, 429, 500, 502, 503, 504],
  retryableCodes: ["NETWORK_UNAVAILABLE", "RATE_LIMITED", "API_UNAVAILABLE"],
};

export function decideRetry(error: ApiRequestError, nextAttempt: number, policy: RetryPolicy = defaultRetryPolicy): RetryDecision {
  if (nextAttempt >= policy.maxAttempts) {
    return { shouldRetry: false, reason: "max attempts reached" };
  }
  if (!isRetryable(error, policy)) {
    return { shouldRetry: false, reason: "error is not retryable" };
  }
  return {
    shouldRetry: true,
    delayMs: Math.min(policy.baseDelayMs * 2 ** (nextAttempt - 1), policy.maxDelayMs),
    reason: "retryable mobile API failure",
  };
}

export function createOfflineQueueState(maxDepth = 25): OfflinePaymentQueueState {
  return { payments: [], maxDepth };
}

export function enqueueOfflinePayment(
  state: OfflinePaymentQueueState,
  command: StartPaymentCommand,
  queuedAt: string
): OfflinePaymentQueueState {
  if (state.payments.some((item) => item.command.idempotencyKey === command.idempotencyKey)) {
    return state;
  }
  if (state.payments.length >= state.maxDepth) {
    throw new Error("offline payment queue is full");
  }
  return {
    ...state,
    payments: [
      ...state.payments,
      {
        command,
        queuedAt,
        attempts: 0,
      },
    ],
  };
}

export async function drainOfflinePaymentQueue(
  state: OfflinePaymentQueueState,
  dependencies: OfflineDrainDependencies
): Promise<OfflineDrainResult> {
  let sent = 0;
  const remaining = [...state.payments];

  while (remaining.length > 0) {
    const item = remaining[0];
    try {
      await dependencies.client.startPayment(item.command);
      remaining.shift();
      sent += 1;
    } catch (error) {
      const attempts = item.attempts + 1;
      const retry = decideRetry(apiRequestErrorFrom(error), attempts, dependencies.retryPolicy);
      remaining[0] = { ...item, attempts };
      return {
        state: { ...state, payments: remaining },
        sent,
        blocked: true,
        retry,
      };
    }
  }

  return {
    state: { ...state, payments: remaining },
    sent,
    blocked: false,
  };
}

function isRetryable(error: ApiRequestError, policy: RetryPolicy): boolean {
  return policy.retryableStatuses.includes(error.status) || policy.retryableCodes.includes(error.code);
}

function apiRequestErrorFrom(error: unknown): ApiRequestError {
  if (error instanceof ApiClientError) {
    return {
      status: error.status,
      code: error.code,
      message: error.message,
    };
  }
  return {
    status: 503,
    code: "NETWORK_UNAVAILABLE",
    message: "network unavailable",
  };
}
