import type { BankingTransactionIntent } from "../state/bankingVoiceSecure";

export type BankingTabKey = "home" | "payments" | "cards" | "insights" | "profile";

export interface BankingTab {
  key: BankingTabKey;
  label: string;
  icon: string;
}

export interface BankingHeroCopy {
  brand: string;
  greeting: string;
  accountName: string;
  balanceLabel: string;
  balance: string;
  securityNote: string;
  statusNote: string;
}

export interface BankingAccountCard {
  name: string;
  balance: string;
  detail: string;
  tone: "navy" | "emerald" | "amber";
}

export interface BankingQuickAction {
  label: string;
  detail: string;
  icon: string;
  tone: "emerald" | "sky" | "amber" | "rose";
  intent: BankingTransactionIntent;
}

export interface BankingTransactionItem {
  merchant: string;
  icon: string;
  category: string;
  amount: string;
  status: "Completed" | "Processing";
  tone: "credit" | "debit";
}

export interface BankingTransactionGroup {
  dateLabel: string;
  items: BankingTransactionItem[];
}

export interface BankingInsight {
  label: string;
  value: string;
  detail: string;
  tone: "emerald" | "sky" | "amber";
}

export interface BankingCardDeckItem {
  name: string;
  maskedNumber: string;
  expiry: string;
  detail: string;
  tone: "navy" | "emerald" | "sky";
}

export interface BankingProfileRow {
  label: string;
  value: string;
  detail: string;
}

export const bankingHero: BankingHeroCopy = {
  brand: "VoiceSecure Bank",
  greeting: "Good afternoon, Tumelo",
  accountName: "Everyday Account",
  balanceLabel: "Available balance",
  balance: "R 48,250.75",
  securityNote: "Protected",
  statusNote: "Your money is protected and ready to move.",
};

export const bankingTabs: BankingTab[] = [
  { key: "home", label: "Home", icon: "⌂" },
  { key: "payments", label: "Payments", icon: "⇄" },
  { key: "cards", label: "Cards", icon: "▭" },
  { key: "insights", label: "Insights", icon: "◔" },
  { key: "profile", label: "Profile", icon: "◉" },
];

export const bankingAccountCards: BankingAccountCard[] = [
  {
    name: "Everyday Account",
    balance: "R 48,250.75",
    detail: "Main account for daily spend",
    tone: "navy",
  },
  {
    name: "Savings Pocket",
    balance: "R 12,400.00",
    detail: "Easy-access reserve",
    tone: "emerald",
  },
  {
    name: "Travel Card",
    balance: "R 3,840.00",
    detail: "International spend ready",
    tone: "amber",
  },
];

export const bankingQuickActions: BankingQuickAction[] = [
  { label: "Pay", detail: "Pay a bill", icon: "↗", tone: "emerald", intent: "pay" },
  { label: "Send", detail: "Transfer money", icon: "⇄", tone: "sky", intent: "send" },
  { label: "Top up", detail: "Add funds", icon: "+", tone: "amber", intent: "topup" },
  { label: "Scan", detail: "QR payment", icon: "▣", tone: "rose", intent: "pay" },
];

export const bankingTransactionGroups: BankingTransactionGroup[] = [
  {
    dateLabel: "Today",
    items: [
      {
        merchant: "Salary deposit",
        icon: "S",
        category: "Income",
        amount: "+ R 52,400.00",
        status: "Completed",
        tone: "credit",
      },
      {
        merchant: "Woolworths",
        icon: "W",
        category: "Groceries",
        amount: "- R 245.80",
        status: "Completed",
        tone: "debit",
      },
      {
        merchant: "Uber",
        icon: "U",
        category: "Transport",
        amount: "- R 118.40",
        status: "Completed",
        tone: "debit",
      },
    ],
  },
  {
    dateLabel: "Yesterday",
    items: [
      {
        merchant: "Electricity",
        icon: "E",
        category: "Bills",
        amount: "- R 980.00",
        status: "Completed",
        tone: "debit",
      },
      {
        merchant: "Spotify",
        icon: "S",
        category: "Subscriptions",
        amount: "- R 99.99",
        status: "Completed",
        tone: "debit",
      },
      {
        merchant: "Cashback reward",
        icon: "C",
        category: "Rewards",
        amount: "+ R 38.00",
        status: "Completed",
        tone: "credit",
      },
    ],
  },
  {
    dateLabel: "Monday",
    items: [
      {
        merchant: "Transfer to Maya",
        icon: "M",
        category: "Transfer",
        amount: "- R 1,200.00",
        status: "Completed",
        tone: "debit",
      },
      {
        merchant: "Savings Pocket",
        icon: "P",
        category: "Savings",
        amount: "+ R 380.00",
        status: "Completed",
        tone: "credit",
      },
    ],
  },
];

export const bankingInsights: BankingInsight[] = [
  {
    label: "Groceries",
    value: "R 2,340",
    detail: "Down 8% from last month",
    tone: "emerald",
  },
  {
    label: "Transport",
    value: "R 1,120",
    detail: "Mostly ride-hailing and fuel",
    tone: "sky",
  },
  {
    label: "Bills",
    value: "R 3,480",
    detail: "Utilities and subscriptions",
    tone: "amber",
  },
];

export const bankingSpendingBars = [38, 62, 54, 84, 48, 72, 92];

export const bankingCardDeck: BankingCardDeckItem[] = [
  {
    name: "Digital Card",
    maskedNumber: "•••• 5124",
    expiry: "08/28",
    detail: "Tap to reveal card details",
    tone: "navy",
  },
  {
    name: "Physical Card",
    maskedNumber: "•••• 8841",
    expiry: "09/29",
    detail: "Ready for in-store payments",
    tone: "emerald",
  },
  {
    name: "Virtual Card",
    maskedNumber: "•••• 2218",
    expiry: "12/27",
    detail: "Best for online purchases",
    tone: "sky",
  },
];

export const bankingProfileRows: BankingProfileRow[] = [
  {
    label: "VoiceSecure",
    value: "On for every transaction",
    detail: "Every payment passes through a quick voice check.",
  },
  {
    label: "Backups",
    value: "PIN, Face ID, fingerprint",
    detail: "Always available if voice verification needs another route.",
  },
  {
    label: "Support",
    value: "24/7 in-app help",
    detail: "Reach us any time if you need assistance.",
  },
];
