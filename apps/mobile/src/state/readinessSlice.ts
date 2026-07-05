import { createSelector, createSlice } from "@reduxjs/toolkit";
import {
  createReadinessState,
  readinessSelectors as modelSelectors,
} from "./readinessModel.ts";
import type { RootState } from "./store.ts";

const readinessSlice = createSlice({
  name: "readiness",
  initialState: createReadinessState(),
  reducers: {
    markCiPassing(state) {
      state.summary.ci = { status: "passing", label: "Service CI passing" };
    },
  },
});

export const { markCiPassing } = readinessSlice.actions;
export const readinessReducer = readinessSlice.reducer;

const stableMobileClassNames = modelSelectors.mobileClassNames();

export const selectReadiness = (state: RootState) => state.readiness;
export const selectActivePhase = (state: RootState) => modelSelectors.activePhase(state.readiness);
export const selectSummaryCards = createSelector(
  [(state: RootState) => state.readiness.summary],
  (summary) => modelSelectors.summaryCards({ summary }),
);
export const selectMobileClassNames = (_state: RootState) => stableMobileClassNames;
