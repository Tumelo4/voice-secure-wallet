import assert from "node:assert/strict";
import test from "node:test";

import {
  createReadinessState,
  dashboardSections,
  readinessSelectors,
  uiStack,
} from "../src/state/readinessModel.ts";

test("mobile UI declares the requested React Native, Tailwind, and Redux stack", () => {
  assert.equal(uiStack.platform, "react-native");
  assert.equal(uiStack.styling, "nativewind-tailwind-css");
  assert.equal(uiStack.state, "redux-toolkit");
});

test("readiness state keeps service and validation evidence", () => {
  const state = createReadinessState();

  assert.equal(state.services.length, 13);
  assert.equal(readinessSelectors.totalPassingTests(state), 122);
  assert.equal(readinessSelectors.activePhase(state).name, "Terraform AWS Baseline");
  assert.ok(state.testSuites.some((suite) => suite.name === "API local HTTP listener"));
  assert.ok(state.testSuites.some((suite) => suite.name === "API runtime"));
  assert.ok(state.testSuites.some((suite) => suite.name === "Durable infrastructure"));
  assert.ok(state.testSuites.some((suite) => suite.name === "Mobile API client"));
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
    "Services ready 13 of 13",
    "Tests passing 122 of 122",
    "CI status Service CI passing",
    "Launch gates 4 of 16",
  ]);
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
  const state = createReadinessState();
  const classes = readinessSelectors.mobileClassNames(state);

  assert.ok(classes.screen.includes("bg-amber-50"));
  assert.ok(classes.card.includes("rounded-3xl"));
  assert.ok(classes.activePhase.includes("border-orange-500"));
  assert.ok(classes.metricGrid.includes("flex-row"));
});
