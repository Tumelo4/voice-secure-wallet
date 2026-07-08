export type BankingLayoutMode = "compact" | "medium" | "expanded";

export function bankingLayoutModeForWidth(width: number): BankingLayoutMode {
  if (width >= 1024) {
    return "expanded";
  }

  if (width >= 768) {
    return "medium";
  }

  return "compact";
}

export function usesSideRail(mode: BankingLayoutMode): boolean {
  return mode !== "compact";
}

export function contentMaxWidth(mode: BankingLayoutMode): number {
  switch (mode) {
    case "compact":
      return 0;
    case "medium":
      return 920;
    case "expanded":
      return 1180;
  }
}

export function quickActionColumns(mode: BankingLayoutMode): number {
  return mode === "expanded" ? 4 : 2;
}

export function composerColumns(mode: BankingLayoutMode): number {
  return mode === "compact" ? 1 : 2;
}

