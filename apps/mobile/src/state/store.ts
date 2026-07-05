import { configureStore } from "@reduxjs/toolkit";
import { useSelector } from "react-redux";
import type { TypedUseSelectorHook } from "react-redux";
import { mobileApiReducer } from "./mobileApiSlice.ts";
import { readinessReducer } from "./readinessSlice.ts";

export const store = configureStore({
  reducer: {
    mobileApi: mobileApiReducer,
    readiness: readinessReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;
