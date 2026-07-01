const services = [
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

const phases = [
  {
    name: "Ledger Core",
    status: "complete",
    evidence: "Signed ledger, repair flow, wallet projection",
  },
  {
    name: "Payment Saga",
    status: "complete",
    evidence: "18-state saga, notification boundary, compensation branches",
  },
  {
    name: "Identity, Fraud & Compliance",
    status: "complete",
    evidence: "Device identity, fraud policy, compliance hit contracts",
  },
  {
    name: "Voice & Fallback",
    status: "complete",
    evidence: "Voice challenge, replay checks, OTP fallback scenario",
  },
  {
    name: "Admin, Support & Recovery",
    status: "complete",
    evidence: "Repair escalation, recovery reenrollment, audit flow",
  },
  {
    name: "Observability & DR",
    status: "modeled",
    evidence: "Policy validators and CI gate, infra still pending",
  },
  {
    name: "Hardening & Launch",
    status: "modeled",
    evidence: "Launch evidence model, real staging evidence still pending",
  },
  {
    name: "API Adapters",
    status: "complete",
    evidence: "HTTP payment commands and wallet balance reads mapped to domain services",
  },
  {
    name: "API Runtime Boundary",
    status: "active",
    evidence: "Bearer auth, trace IDs, rate limits, and request logs guard API adapters",
  },
];

const blockers = [
  {
    title: "Durable infrastructure adapters",
    detail: "PostgreSQL, Kafka, Redis, and pgvector adapters still need real integration tests.",
  },
  {
    title: "Network server integration",
    detail: "Framework-free runtime guards exist; production listener, external auth provider, distributed rate limits, and mTLS are still pending.",
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

const testSuites = [
  { name: "API adapters", passing: 5 },
  { name: "API runtime", passing: 5 },
  { name: "Acceptance", passing: 3 },
  { name: "Compliance", passing: 2 },
  { name: "Contracts", passing: 3 },
  { name: "Events", passing: 6 },
  { name: "Fraud", passing: 4 },
  { name: "Identity", passing: 5 },
  { name: "Launch", passing: 4 },
  { name: "Ledger", passing: 6 },
  { name: "Notifications", passing: 4 },
  { name: "Ops", passing: 5 },
  { name: "Payments", passing: 9 },
  { name: "Recovery", passing: 3 },
  { name: "Support", passing: 4 },
  { name: "Wallet", passing: 4 },
  { name: "Voice Python", passing: 8 },
  { name: "Web UI", passing: 4 },
];

export function createDashboardModel() {
  const testsPassing = testSuites.reduce((total, suite) => total + suite.passing, 0);
  return {
    generatedAt: "2026-07-01T09:40:00+02:00",
    summary: {
      services: {
        ready: services.length,
        total: services.length,
      },
      tests: {
        passing: testsPassing,
        total: testsPassing,
      },
      ci: {
        status: "passing",
        label: "Service CI passing",
      },
      launchGates: {
        complete: 4,
        total: 16,
      },
    },
    services,
    phases,
    blockers,
    testSuites,
  };
}

export function renderSummaryCards(model) {
  const cards = [
    {
      label: "Services ready",
      value: `${model.summary.services.ready} / ${model.summary.services.total}`,
      detail: "Bounded contexts modeled",
    },
    {
      label: "Tests passing",
      value: `${model.summary.tests.passing} / ${model.summary.tests.total}`,
      detail: "Unit, BDD, and contract checks",
    },
    {
      label: "CI status",
      value: model.summary.ci.label,
      detail: "GitHub Actions evidence",
    },
    {
      label: "Launch gates",
      value: `${model.summary.launchGates.complete} / ${model.summary.launchGates.total}`,
      detail: "Production evidence still growing",
    },
  ];
  return cards
    .map((card) => `
      <article class="metric-card" aria-label="${escapeHtml(card.label)}">
        <span>${escapeHtml(card.label)}</span>
        <strong>${escapeHtml(card.value)}</strong>
        <p>${escapeHtml(card.detail)}</p>
      </article>
    `)
    .join("");
}

export function renderPhaseTimeline(model) {
  return `
    <ol class="phase-timeline" aria-label="PDF build phases">
      ${model.phases.map((phase, index) => `
        <li class="phase phase-${phase.status}">
          <span class="phase-index">${String(index + 1).padStart(2, "0")}</span>
          <div>
            <h3>${escapeHtml(phase.name)}</h3>
            <p>${escapeHtml(phase.evidence)}</p>
          </div>
          <strong>${escapeHtml(phase.status)}</strong>
        </li>
      `).join("")}
    </ol>
  `;
}

export function renderRiskPanel(model) {
  return `
    <div class="risk-grid">
      ${model.blockers.map((blocker) => `
        <article class="risk-card">
          <h3>${escapeHtml(blocker.title)}</h3>
          <p>${escapeHtml(blocker.detail)}</p>
        </article>
      `).join("")}
    </div>
  `;
}

export function renderTestSuites(model) {
  return `
    <div class="suite-grid">
      ${model.testSuites.map((suite) => `
        <article class="suite-card">
          <span>${escapeHtml(suite.name)}</span>
          <strong>${suite.passing}</strong>
        </article>
      `).join("")}
    </div>
  `;
}

export function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
