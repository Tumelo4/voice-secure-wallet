import * as SecureStore from "expo-secure-store";
import type { NativeSecureStoreDriver, NativeSecureStoreOptions } from "./tokenSession.ts";

export class ExpoSecureStoreDriver implements NativeSecureStoreDriver {
  getItem(key: string, options: NativeSecureStoreOptions): Promise<string | null> {
    return SecureStore.getItemAsync(key, secureOptions(options));
  }

  setItem(key: string, value: string, options: NativeSecureStoreOptions): Promise<void> {
    return SecureStore.setItemAsync(key, value, secureOptions(options));
  }

  deleteItem(key: string, options: NativeSecureStoreOptions): Promise<void> {
    return SecureStore.deleteItemAsync(key, secureOptions(options));
  }
}

function secureOptions(options: NativeSecureStoreOptions): SecureStore.SecureStoreOptions {
  return {
    keychainService: options.namespace,
    requireAuthentication: options.biometricOrPasscodeRequired,
    keychainAccessible: SecureStore.WHEN_PASSCODE_SET_THIS_DEVICE_ONLY,
  };
}
