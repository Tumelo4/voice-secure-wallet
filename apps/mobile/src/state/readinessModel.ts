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
  { name: "Voice & Fallback", status: "complete", evidence: "Voice challenge, replay checks, OTP fallback scenario" },
  { name: "Admin, Support & Recovery", status: "complete", evidence: "Repair escalation, recovery reenrollment, audit flow" },
  { name: "Observability & DR", status: "modeled", evidence: "Policy validators and CI gate, infra still pending" },
  { name: "Hardening & Launch", status: "modeled", evidence: "Launch evidence model, real staging evidence still pending" },
  { name: "API Adapters", status: "complete", evidence: "HTTP payment commands and wallet balance reads mapped to domain services" },
  { name: "API Runtime Boundary", status: "complete", evidence: "Bearer auth, trace IDs, rate limits, and request logs guard API adapters" },
  { name: "Mobile Fetch Transport", status: "complete", evidence: "React Native fetch adapter, network error mapping, and token provider boundary" },
  { name: "Mobile Token Session", status: "complete", evidence: "Secure token vault port, refresh-window policy, and refresh-failure cleanup" },
  { name: "Mobile Redux API Flows", status: "complete", evidence: "Thunk-style wallet and payment flows update Redux request state" },
  { name: "Mobile Resilience Policy", status: "complete", evidence: "Retry backoff, offline payment queue, idempotent enqueue, and ordered drain policy" },
  { name: "API Local HTTP Listener", status: "active", evidence: "JDK HTTP listener forwards real socket requests through runtime guards" },
];

const blockers: Blocker[] = [
  {
    title: "Durable infrastructure adapters",
    detail: "PostgreSQL, Kafka, Redis, and pgvector adapters still need real integration tests.",
  },
  {
    title: "Network server integration",
    detail: "A local JDK listener exists; external auth provider, distributed rate limits, mTLS, and production ingress are still pending.",
  },
  {
    title: "Mobile secure storage",
    detail: "The mobile token vault port exists; native OS keystore wiring still needs implementation.",
  },
  {
    title: "Mobile screen commands",
    detail: "Redux API flows exist; form screens, user-triggered dispatch, optimistic UX, and retry/backoff policy still need production wiring.",
  },
  {
    title: "Terraform",
    detail: "VPC, ECS/Fargate, RDS/MSK/ElastiCache, mTLS, and object-lock storage are not provisioned.",
  },
  {
    title: "Pact",
    detail: "Local event contracts exist; Pact and Schema Registry compatibility are still pending.",
  },
  {
    title: "Launch evidence",
    detail: "Chaos, load, security scanning, DR restore, and 48-hour staging runs still need measured proof.",
  },
];

const testSuites: TestSuiteEvidence[] = [
  { name: "API adapters", passing: 5 },
  { name: "API local HTTP listener", passing: 3 },
  { name: "API runtime", passing: 5 },
  { name: "Acceptance", passing: 3 },
  { name: "Compliance", passing: 2 },
  { name: "Contracts", passing: 3 },
  { name: "Events", passing: 6 },
  { name: "Fraud", passing: 4 },
  { name: "Identity", passing: 5 },
  { name: "Launch", passing: 4 },
  { name: "Ledger", passing: 6 },
  { name: "Mobile API client", passing: 4 },
  { name: "Mobile fetch transport", passing: 4 },
  { name: "Mobile resilience policy", passing: 6 },
  { name: "Mobile Redux API flows", passing: 5 },
  { name: "Mobile token session", passing: 5 },
  { name: "Mobile UI", passing: 5 },
  { name: "Notifications", passing: 4 },
  { name: "Ops", passing: 5 },
  { name: "Payments", passing: 9 },
  { name: "Recovery", passing: 3 },
  { name: "Support", passing: 4 },
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
    generatedAt: "2026-07-02T09:00:00+02:00",
    stack: uiStack,
    services,
    phases,
    blockers,
    testSuites,
    summary: {
      services: { ready: services.length, total: services.length },
      tests: { passing: testsPassing, total: testsPassing },
      ci: { status: "passing", label: "Service CI passing" },
      launchGates: { complete: 4, total: 16 },
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
        label: "Services ready",
        value: `${state.summary.services.ready} / ${state.summary.services.total}`,
        detail: "Bounded contexts modeled",
        accessibilityLabel: `Services ready ${state.summary.services.ready} of ${state.summary.services.total}`,
      },
      {
        label: "Tests passing",
        value: `${state.summary.tests.passing} / ${state.summary.tests.total}`,
        detail: "Unit, BDD, contract, and mobile checks",
        accessibilityLabel: `Tests passing ${state.summary.tests.passing} of ${state.summary.tests.total}`,
      },
      {
        label: "CI status",
        value: state.summary.ci.label,
        detail: "GitHub Actions evidence",
        accessibilityLabel: `CI status ${state.summary.ci.label}`,
      },
      {
        label: "Launch gates",
        value: `${state.summary.launchGates.complete} / ${state.summary.launchGates.total}`,
        detail: "Production evidence still growing",
        accessibilityLabel: `Launch gates ${state.summary.launchGates.complete} of ${state.summary.launchGates.total}`,
      },
    ];
  },
  mobileClassNames(): MobileClassNames {
    return {
      screen: "flex-1 bg-amber-50",
      card: "rounded-3xl border border-stone-200 bg-white/80 p-5 shadow-sm",
      activePhase: "border-orange-500 bg-amber-100",
      metricGrid: "flex-row flex-wrap",
    };
  },
};
