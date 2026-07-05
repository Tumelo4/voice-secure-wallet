import assert from "node:assert/strict";
import test from "node:test";

import {
  createReadinessState,
  dashboardSections,
  readinessSelectors,
  uiStack,
} from "../src/state/readinessModel.ts";
import { selectMobileClassNames, selectSummaryCards } from "../src/state/readinessSlice.ts";
import { store } from "../src/state/store.ts";

test("mobile UI declares the requested React Native, Tailwind, and Redux stack", () => {
  assert.equal(uiStack.platform, "react-native");
  assert.equal(uiStack.styling, "nativewind-tailwind-css");
  assert.equal(uiStack.state, "redux-toolkit");
});

test("readiness state keeps service and validation evidence", () => {
  const state = createReadinessState();

  assert.equal(state.services.length, 13);
  assert.equal(readinessSelectors.totalPassingTests(state), 134);
  assert.equal(readinessSelectors.activePhase(state).name, "Pact Schema Registry Readiness");
  assert.ok(state.testSuites.some((suite) => suite.name === "API production ingress"));
  assert.ok(state.testSuites.some((suite) => suite.name === "API local HTTP listener"));
  assert.ok(state.testSuites.some((suite) => suite.name === "API runtime"));
  assert.ok(state.testSuites.some((suite) => suite.name === "Contract compatibility"));
  assert.ok(state.testSuites.some((suite) => suite.name === "Durable infrastructure"));
  assert.ok(state.testSuites.some((suite) => suite.name === "Mobile API client"));
  assert.ok(state.testSuites.some((suite) => suite.name === "Mobile command forms"));
  assert.ok(state.testSuites.some((suite) => suite.name === "Mobile fetch transport"));
  assert.ok(state.testSuites.some((suite) => suite.name === "Mobile resilience policy"));
  assert.ok(state.testSuites.some((suite) => suite.name === "Mobile Redux API flows"));
  assert.ok(state.testSuites.some((suite) => suite.name === "Mobile token session"));
  assert.ok(state.testSuites.some((suite) => suite.name === "Terraform AWS baseline"));
});

test("summary cards expose mobile accessible labels and current counts", () => {
  const state = createReadinessState();
  const cards = readinessSelectors.summaryCards(state);

  assert.deepEqual(cards.map((card) => card.accessibilityLabel), [
    "Accounts live 13 of 13",
    "Checks cleared 134 of 134",
    "Security status Service CI passing",
    "Launch controls 5 of 17",
  ]);
});

test("redux readiness selectors return stable references for unchanged state", () => {
  const state = store.getState();

  assert.equal(selectSummaryCards(state), selectSummaryCards(state));
  assert.equal(selectMobileClassNames(state), selectMobileClassNames(state));
});

test("dashboard sections preserve the product narrative order", () => {
  assert.deepEqual(dashboardSections.map((section) => section.key), [
    "summary",
    "phases",
    "risks",
    "evidence",
  ]);
});

test("tailwind class tokens avoid static web css and support mobile layout", () => {
  const classes = readinessSelectors.mobileClassNames();

  assert.ok(classes.screen.includes("bg-[#071521]"));
  assert.ok(classes.card.includes("rounded-[30px]"));
  assert.ok(classes.activePhase.includes("border-emerald-400/30"));
  assert.ok(classes.metricGrid.includes("flex-row"));
});
