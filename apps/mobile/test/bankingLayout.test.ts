import assert from "node:assert/strict";
import test from "node:test";

import {
  bankingLayoutModeForWidth,
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

test("banking layout uses a side rail on tablet widths", () => {
  assert.equal(bankingLayoutModeForWidth(768), "medium");
  assert.equal(usesSideRail(bankingLayoutModeForWidth(768)), true);
  assert.equal(quickActionColumns(bankingLayoutModeForWidth(768)), 2);
  assert.equal(composerColumns(bankingLayoutModeForWidth(768)), 2);
  assert.equal(contentMaxWidth(bankingLayoutModeForWidth(768)), 920);
});

test("banking layout expands on desktop widths", () => {
  assert.equal(bankingLayoutModeForWidth(1440), "expanded");
  assert.equal(usesSideRail(bankingLayoutModeForWidth(1440)), true);
  assert.equal(quickActionColumns(bankingLayoutModeForWidth(1440)), 4);
  assert.equal(composerColumns(bankingLayoutModeForWidth(1440)), 2);
  assert.equal(contentMaxWidth(bankingLayoutModeForWidth(1440)), 1180);
});
