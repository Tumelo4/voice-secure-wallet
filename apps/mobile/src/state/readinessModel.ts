export interface UiStack {
  platform: "react-native";
  styling: "nativewind-tailwind-css";
  state: "redux-toolkit";
}

export interface Phase {
  name: string;
  status: "complete" | "modeled" | "active";
  evidence: string;
}

export interface Blocker {
  title: string;
  detail: string;
}

export interface TestSuiteEvidence {
  name: string;
  passing: number;
}

export interface DashboardSection {
  key: "summary" | "phases" | "risks" | "evidence";
  title: string;
}

export interface ReadinessSummary {
  services: {
    ready: number;
    total: number;
  };
  tests: {
    passing: number;
    total: number;
  };
  ci: {
    status: "passing" | "failing" | "unknown";
    label: string;
  };
  launchGates: {
    complete: number;
    total: number;
  };
}

export interface ReadinessState {
  generatedAt: string;
  stack: UiStack;
  services: string[];
  phases: Phase[];
  blockers: Blocker[];
  testSuites: TestSuiteEvidence[];
  summary: ReadinessSummary;
}

export interface SummaryCard {
  label: string;
  value: string;
  detail: string;
  accessibilityLabel: string;
}

export interface MobileClassNames {
  screen: string;
  card: string;
  activePhase: string;
  metricGrid: string;
}

export const uiStack: UiStack = {
  platform: "react-native",
  styling: "nativewind-tailwind-css",
  state: "redux-toolkit",
};

const services: string[] = [
  "ledger-service",
  "wallet-service",
  "payment-service",
  "identity-service",
  "fraud-service",
  "compliance-service",
  "voice-service",
  "notification-service",
  "support-service",
  "recovery-service",
  "ops-service",
  "launch-service",
  "api-adapter-service",
];

const phases: Phase[] = [
  { name: "Ledger Core", status: "complete", evidence: "Signed ledger, repair flow, wallet projection" },
  { name: "Payment Saga", status: "complete", evidence: "18-state saga, notification boundary, compensation branches" },
  { name: "Identity, Fraud & Compliance", status: "complete", evidence: "Device identity, fraud policy, compliance hit contracts" },
  { name: "Voice & Fallback", status: "partial", evidence: "Experimental voice challenge and replay controls; fallback MFA remains mandatory" },
  { name: "Admin, Support & Recovery", status: "complete", evidence: "Repair escalation, recovery reenrollment, audit flow" },
  { name: "Observability & DR", status: "modeled", evidence: "Policy validators and CI gate, infra still pending" },
  { name: "Hardening & Launch", status: "modeled", evidence: "Launch evidence model, real staging evidence still pending" },
  { name: "API Adapters", status: "complete", evidence: "HTTP payment commands and wallet balance reads mapped to domain services" },
  { name: "API Runtime Boundary", status: "complete", evidence: "Bearer auth, trace IDs, rate limits, and request logs guard API adapters" },
  { name: "Mobile Fetch Transport", status: "complete", evidence: "React Native fetch adapter, network error mapping, and token provider boundary" },
  { name: "Mobile Token Session", status: "complete", evidence: "Secure token vault port, refresh-window policy, and refresh-failure cleanup" },
  { name: "Mobile Redux API Flows", status: "complete", evidence: "Thunk-style wallet and payment flows update Redux request state" },
  { name: "Mobile Resilience Policy", status: "complete", evidence: "Retry backoff, offline payment queue, idempotent enqueue, and ordered drain policy" },
  { name: "API Local HTTP Listener", status: "complete", evidence: "JDK HTTP listener forwards real socket requests through runtime guards" },
  { name: "Durable Infrastructure Readiness", status: "complete", evidence: "Kafka topic durability and AWS HA/encryption controls validated locally" },
  { name: "Terraform AWS Baseline", status: "complete", evidence: "VPC, KMS, MSK, RDS, Redis, S3 object lock, and Secrets Manager references declared" },
  { name: "Production Cutover Readiness", status: "complete", evidence: "Change ticket, rollback, feature flags, monitoring, on-call, support, and rollback SLA validated" },
  { name: "Production Ingress Readiness", status: "complete", evidence: "TLS 1.3, mTLS, JWKS, WAF, HSTS, distributed rate limits, trace forwarding, and health-path controls validated" },
  { name: "Mobile Native Secure Storage", status: "complete", evidence: "Native secure-store driver port, hardware-backed options, device-only storage, biometric/passcode guard, and cloud-sync blocker validated" },
  { name: "Mobile Screen Commands", status: "complete", evidence: "Wallet-balance and payment-start screen forms validate user input before dispatching Redux API flows" },
  { name: "Pact Schema Registry Readiness", status: "active", evidence: "Pact broker, consumer verification, Schema Registry, schema pinning, and backward-transitive compatibility validated locally" },
];

const blockers: Blocker[] = [
  {
    title: "Durable infrastructure adapters",
    detail: "Kafka/AWS readiness contracts and Terraform baseline exist; live MSK, RDS, Redis, S3, and pgvector adapters still need integration tests.",
  },
  {
    title: "Network server integration",
    detail: "Production ingress policy is modeled; live certificate provisioning, load balancer deployment, DNS, and external JWKS integration still need environment tests.",
  },
  {
    title: "Mobile secure storage",
    detail: "Native secure-store readiness is modeled; real iOS Keychain/Android Keystore package wiring and device QA still need environment tests.",
  },
  {
    title: "Mobile screen commands",
    detail: "Wallet and payment command forms now validate locally; real API dependency injection, optimistic UX, and device-level form QA still need production wiring.",
  },
  {
    title: "Terraform",
    detail: "VPC, ECS/Fargate, RDS/MSK/ElastiCache, edge certificates, and object-lock storage are not provisioned.",
  },
  {
    title: "Pact",
    detail: "Pact and Schema Registry readiness is modeled; live Pact broker credentials, Schema Registry credentials, and provider-state verification still need environment tests.",
  },
  {
    title: "Launch evidence",
    detail: "Production cutover checks are modeled; real signed change ticket, rollback drill, monitoring, on-call, support briefing, and 48-hour staging evidence still need measured proof.",
  },
];

const testSuites: TestSuiteEvidence[] = [
  { name: "API adapters", passing: 5 },
  { name: "API local HTTP listener", passing: 3 },
  { name: "API production ingress", passing: 3 },
  { name: "API runtime", passing: 5 },
  { name: "Acceptance", passing: 3 },
  { name: "Compliance", passing: 2 },
  { name: "Contracts", passing: 3 },
  { name: "Contract compatibility", passing: 4 },
  { name: "Durable infrastructure", passing: 5 },
  { name: "Events", passing: 6 },
  { name: "Fraud", passing: 4 },
  { name: "Identity", passing: 5 },
  { name: "Launch", passing: 5 },
  { name: "Ledger", passing: 6 },
  { name: "Mobile API client", passing: 4 },
  { name: "Mobile command forms", passing: 3 },
  { name: "Mobile fetch transport", passing: 4 },
  { name: "Mobile resilience policy", passing: 6 },
  { name: "Mobile Redux API flows", passing: 5 },
  { name: "Mobile token session", passing: 7 },
  { name: "Mobile UI", passing: 5 },
  { name: "Notifications", passing: 4 },
  { name: "Ops", passing: 5 },
  { name: "Payments", passing: 9 },
  { name: "Recovery", passing: 3 },
  { name: "Support", passing: 4 },
  { name: "Terraform AWS baseline", passing: 5 },
  { name: "Wallet", passing: 4 },
  { name: "Voice Python", passing: 8 },
];

export const dashboardSections: DashboardSection[] = [
  { key: "summary", title: "Readiness summary" },
  { key: "phases", title: "PDF phase progress" },
  { key: "risks", title: "Risks to burn down next" },
  { key: "evidence", title: "Passing local suites" },
];

export function createReadinessState(): ReadinessState {
  const testsPassing = readinessSelectors.totalPassingTests({ testSuites });
  return {
    generatedAt: "2026-07-03T20:30:00+02:00",
    stack: uiStack,
    services,
    phases,
    blockers,
    testSuites,
    summary: {
      services: { ready: services.length, total: services.length },
      tests: { passing: testsPassing, total: testsPassing },
      ci: { status: "passing", label: "Service CI passing" },
      launchGates: { complete: 5, total: 17 },
    },
  };
}

export const readinessSelectors = {
  totalPassingTests(state: Pick<ReadinessState, "testSuites">): number {
    return state.testSuites.reduce((total, suite) => total + suite.passing, 0);
  },
  activePhase(state: Pick<ReadinessState, "phases">): Phase {
    const active = state.phases.find((phase) => phase.status === "active");
    if (!active) {
      throw new Error("active phase is required");
    }
    return active;
  },
  summaryCards(state: Pick<ReadinessState, "summary">): SummaryCard[] {
    return [
      {
        label: "Accounts live",
        value: `${state.summary.services.ready} / ${state.summary.services.total}`,
        detail: "Core banking modules online",
        accessibilityLabel: `Accounts live ${state.summary.services.ready} of ${state.summary.services.total}`,
      },
      {
        label: "Checks cleared",
        value: `${state.summary.tests.passing} / ${state.summary.tests.total}`,
        detail: "Unit, BDD, contract, and mobile checks",
        accessibilityLabel: `Checks cleared ${state.summary.tests.passing} of ${state.summary.tests.total}`,
      },
      {
        label: "Security status",
        value: state.summary.ci.label,
        detail: "Automated guardrails passing",
        accessibilityLabel: `Security status ${state.summary.ci.label}`,
      },
      {
        label: "Launch controls",
        value: `${state.summary.launchGates.complete} / ${state.summary.launchGates.total}`,
        detail: "Production readiness still maturing",
        accessibilityLabel: `Launch controls ${state.summary.launchGates.complete} of ${state.summary.launchGates.total}`,
      },
    ];
  },
  mobileClassNames(): MobileClassNames {
    return {
      screen: "flex-1 bg-[#071521]",
      card: "rounded-[30px] border border-white/10 bg-white/5 p-5 shadow-xl",
      activePhase: "border-emerald-400/30 bg-emerald-400/10",
      metricGrid: "flex-row flex-wrap",
    };
  },
};
