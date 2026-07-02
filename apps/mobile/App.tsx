import "./global.css";

import { StatusBar } from "expo-status-bar";
import { Provider } from "react-redux";
import { ReadinessDashboard } from "./src/components/ReadinessDashboard";
import { store } from "./src/state/store";

export default function App() {
  return (
    <Provider store={store}>
      <ReadinessDashboard />
      <StatusBar style="dark" />
    </Provider>
  );
}
