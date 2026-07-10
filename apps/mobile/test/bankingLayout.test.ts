import assert from "node:assert/strict";
import test from "node:test";

import {
  bankingLayoutModeForWidth,
  bankingScreenChromeForWidth,
  compactGridItemWidth,
  composerColumns,
  contentMaxWidth,
  quickActionColumns,
  usesSideRail,
} from "../src/components/bankingLayout.ts";

test("banking layout stays compact on phone widths", () => {
  assert.equal(bankingLayoutModeForWidth(320), "compact");
  assert.equal(usesSideRail(bankingLayoutModeForWidth(320)), false);
  assert.equal(quickActionColumns(bankingLayoutModeForWidth(320)), 2);
  assert.equal(composerColumns(bankingLayoutModeForWidth(320)), 1);
  assert.equal(contentMaxWidth(bankingLayoutModeForWidth(320)), 0);
});

test("banking layout uses a side rail at the Material medium breakpoint", () => {
  assert.equal(bankingLayoutModeForWidth(600), "medium");
  assert.equal(usesSideRail(bankingLayoutModeForWidth(600)), true);
  assert.equal(quickActionColumns(bankingLayoutModeForWidth(600)), 2);
  assert.equal(composerColumns(bankingLayoutModeForWidth(600)), 2);
  assert.equal(contentMaxWidth(bankingLayoutModeForWidth(600)), 840);
});

test("banking layout expands on desktop widths", () => {
  assert.equal(bankingLayoutModeForWidth(1440), "expanded");
  assert.equal(usesSideRail(bankingLayoutModeForWidth(1440)), true);
  assert.equal(quickActionColumns(bankingLayoutModeForWidth(1440)), 4);
  assert.equal(composerColumns(bankingLayoutModeForWidth(1440)), 2);
  assert.equal(contentMaxWidth(bankingLayoutModeForWidth(1440)), 1200);
});

test("banking screen chrome keeps the main scroll area shrinkable on phones", () => {
  const chrome = bankingScreenChromeForWidth(390);

  assert.equal(chrome.scrollViewClassName.includes("min-w-0"), true);
  assert.equal(chrome.contentPaddingHorizontal, 16);
  assert.equal(chrome.contentMaxWidth, undefined);
});

test("banking screen chrome caps content on larger widths", () => {
  const chrome = bankingScreenChromeForWidth(1200);

  assert.equal(chrome.scrollViewClassName.includes("min-w-0"), true);
  assert.equal(chrome.contentPaddingHorizontal, 24);
  assert.equal(chrome.contentMaxWidth, 1200);
});

test("very narrow phones use full-width grid controls and reduced gutters", () => {
  assert.equal(compactGridItemWidth(320), "100%");
  assert.equal(compactGridItemWidth(390), "48%");
  assert.equal(bankingScreenChromeForWidth(320).contentPaddingHorizontal, 12);
});
