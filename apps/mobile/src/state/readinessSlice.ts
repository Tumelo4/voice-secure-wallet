import { createSlice } from "@reduxjs/toolkit";
import {
  createReadinessState,
  readinessSelectors as modelSelectors,
} from "./readinessModel.mjs";
import type { RootState } from "./store";

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

export const selectReadiness = (state: RootState) => state.readiness;
export const selectActivePhase = (state: RootState) => modelSelectors.activePhase(state.readiness);
export const selectSummaryCards = (state: RootState) => modelSelectors.summaryCards(state.readiness);
export const selectMobileClassNames = (state: RootState) => modelSelectors.mobileClassNames(state.readiness);
