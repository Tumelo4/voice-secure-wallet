import "./global.css";

import { StatusBar } from "expo-status-bar";
import { Provider } from "react-redux";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { ReadinessDashboard } from "./src/components/ReadinessDashboard";
import { store } from "./src/state/store";
import { createMobileApiClient } from "./src/api/mobileRuntime";

const apiClient = createMobileApiClient();

export default function App() {
  return (
    <SafeAreaProvider>
      <Provider store={store}>
        <ReadinessDashboard apiClient={apiClient} />
        <StatusBar style="dark" />
      </Provider>
    </SafeAreaProvider>
  );
}
