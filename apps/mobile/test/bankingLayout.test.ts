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
  assert.equal(bankingLayoutModeForWidth(390), "compact");
  assert.equal(usesSideRail(bankingLayoutModeForWidth(390)), false);
  assert.equal(quickActionColumns(bankingLayoutModeForWidth(390)), 2);
  assert.equal(composerColumns(bankingLayoutModeForWidth(390)), 1);
  assert.equal(contentMaxWidth(bankingLayoutModeForWidth(390)), 0);
});

test("banking layout uses a side rail on tablet widths", () => {
  assert.equal(bankingLayoutModeForWidth(834), "medium");
  assert.equal(usesSideRail(bankingLayoutModeForWidth(834)), true);
  assert.equal(quickActionColumns(bankingLayoutModeForWidth(834)), 2);
  assert.equal(composerColumns(bankingLayoutModeForWidth(834)), 2);
  assert.equal(contentMaxWidth(bankingLayoutModeForWidth(834)), 920);
});

test("banking layout expands on desktop widths", () => {
  assert.equal(bankingLayoutModeForWidth(1280), "expanded");
  assert.equal(usesSideRail(bankingLayoutModeForWidth(1280)), true);
  assert.equal(quickActionColumns(bankingLayoutModeForWidth(1280)), 4);
  assert.equal(composerColumns(bankingLayoutModeForWidth(1280)), 2);
  assert.equal(contentMaxWidth(bankingLayoutModeForWidth(1280)), 1180);
});

