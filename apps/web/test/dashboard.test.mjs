import assert from "node:assert/strict";
import test from "node:test";

import {
  createDashboardModel,
  renderPhaseTimeline,
  renderRiskPanel,
  renderSummaryCards,
} from "../src/dashboard.mjs";

test("dashboard model summarizes service, test, and launch readiness", () => {
  const model = createDashboardModel();

  assert.equal(model.summary.services.total, 12);
  assert.equal(model.summary.services.ready, 12);
  assert.equal(model.summary.tests.total, 74);
  assert.equal(model.summary.tests.passing, 74);
  assert.equal(model.summary.ci.status, "passing");
  assert.equal(model.summary.launchGates.complete, 4);
  assert.ok(model.summary.launchGates.total > model.summary.launchGates.complete);
});

test("phase timeline keeps PDF plan order and highlights current phase", () => {
  const model = createDashboardModel();
  const names = model.phases.map((phase) => phase.name);

  assert.deepEqual(names.slice(0, 4), [
    "Ledger Core",
    "Payment Saga",
    "Identity, Fraud & Compliance",
    "Voice & Fallback",
  ]);
  assert.equal(model.phases.find((phase) => phase.status === "active").name, "Identity, Fraud & Compliance");
  assert.ok(renderPhaseTimeline(model).includes("Identity, Fraud &amp; Compliance"));
});

test("risk panel calls out remaining production blockers", () => {
  const model = createDashboardModel();
  const html = renderRiskPanel(model);

  assert.ok(html.includes("Durable adapters"));
  assert.ok(html.includes("Terraform"));
  assert.ok(html.includes("Pact"));
  assert.ok(html.includes("Launch evidence"));
});

test("summary cards render accessible labels and current evidence", () => {
  const model = createDashboardModel();
  const html = renderSummaryCards(model);

  assert.ok(html.includes('aria-label="Services ready"'));
  assert.ok(html.includes('aria-label="Tests passing"'));
  assert.ok(html.includes("Service CI passing"));
  assert.ok(html.includes("74 / 74"));
});
