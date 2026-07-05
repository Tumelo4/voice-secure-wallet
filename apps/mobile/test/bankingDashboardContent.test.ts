import assert from "node:assert/strict";
import test from "node:test";

import {
  bankingAccountCards,
  bankingHero,
  bankingQuickActions,
  bankingTabs,
  bankingTransactionGroups,
} from "../src/components/bankingDashboardContent.ts";

test("banking hero keeps balance front and center", () => {
  assert.equal(bankingHero.brand, "VoiceSecure Bank");
  assert.equal(bankingHero.greeting, "Good afternoon, Tumelo");
  assert.equal(bankingHero.accountName, "Everyday Account");
  assert.equal(bankingHero.balance, "R 48,250.75");
  assert.equal(bankingHero.securityNote, "Protected");
});

test("banking tabs match the requested consumer banking navigation", () => {
  assert.deepEqual(
    bankingTabs.map((tab) => tab.label),
    ["Home", "Payments", "Cards", "Insights", "Profile"],
  );
  assert.deepEqual(
    bankingTabs.map((tab) => tab.icon),
    ["⌂", "⇄", "▭", "◔", "◉"],
  );
});

test("banking overview includes cards, quick actions, and grouped transactions", () => {
  assert.equal(bankingAccountCards.length, 3);
  assert.deepEqual(
    bankingQuickActions.map((action) => action.label),
    ["Pay", "Send", "Top up", "Scan"],
  );
  assert.deepEqual(
    bankingQuickActions.map((action) => action.icon),
    ["↗", "⇄", "+", "▣"],
  );
  assert.deepEqual(
    bankingQuickActions.map((action) => action.intent),
    ["pay", "send", "topup", "pay"],
  );
  assert.deepEqual(
    bankingTransactionGroups.map((group) => group.dateLabel),
    ["Today", "Yesterday", "Monday"],
  );
  assert.equal(bankingTransactionGroups[0].items[0].merchant, "Salary deposit");
  assert.equal(bankingTransactionGroups[0].items[1].category, "Groceries");
  assert.equal(bankingTransactionGroups[0].items[2].icon, "U");
  assert.equal(bankingTransactionGroups[1].items[1].icon, "S");
});
