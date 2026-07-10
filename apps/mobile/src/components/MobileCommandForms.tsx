import { Pressable, Text, TextInput, View } from "react-native";
import { useState } from "react";
import type { StartPaymentCommand } from "../api/voiceSecureApiClient";
import type { BankingLayoutMode } from "./bankingLayout";
import {
  createPaymentCommandForm,
  createWalletBalanceCommandForm,
  paymentCommandFromForm,
  updatePaymentCommandForm,
  updateWalletBalanceCommandForm,
  walletAccountIdFromForm,
  type PaymentCommandForm,
  type WalletBalanceCommandForm,
} from "../state/mobileCommandForms";

export interface MobileCommandFormsProps {
  layoutMode: BankingLayoutMode;
  walletBalanceStatus: string;
  paymentStartStatus: string;
  onWalletCommand?: (accountId: string) => void | Promise<void>;
  onPaymentCommand?: (command: StartPaymentCommand) => void | Promise<void>;
}

export function MobileCommandForms({
  layoutMode,
  walletBalanceStatus,
  paymentStartStatus,
  onWalletCommand,
  onPaymentCommand,
}: MobileCommandFormsProps) {
  const isCompact = layoutMode === "compact";
  const [walletForm, setWalletForm] = useState<WalletBalanceCommandForm>(() => createWalletBalanceCommandForm());
  const [paymentForm, setPaymentForm] = useState<PaymentCommandForm>(() => createPaymentCommandForm());
  const [formMessage, setFormMessage] = useState("Ready for secure transfers.");

  const submitWallet = () => {
    try {
      const accountId = walletAccountIdFromForm(walletForm);
      setFormMessage(`Balance check prepared for ${accountId}.`);
      void onWalletCommand?.(accountId);
    } catch (error) {
      setFormMessage(messageFrom(error));
    }
  };

  const submitPayment = () => {
    try {
      const command = paymentCommandFromForm(paymentForm);
      setFormMessage(`Transfer ready for ${command.currency} ${command.amount}.`);
      void onPaymentCommand?.(command);
    } catch (error) {
      setFormMessage(messageFrom(error));
    }
  };

  return (
    <View className="mt-4 overflow-hidden rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
      <View className="absolute right-0 top-0 h-28 w-28 rounded-full bg-blue-50" />
      <Text className="text-[11px] font-medium tracking-[0.06em] text-blue-700">Money movement</Text>
      <Text className="mt-2 text-2xl font-semibold tracking-[-0.03em] text-slate-900">Transfers and payments</Text>
      <Text className="mt-2 text-sm leading-6 text-slate-600">
        Secure React Native forms validate user input before anything reaches
        the Redux request boundary.
      </Text>

      <View className="mt-4 rounded-[24px] border border-slate-200 bg-slate-50 p-4">
        <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-600">Check balance</Text>
        <TextInput
          accessibilityLabel="Wallet account id"
          autoCapitalize="none"
          autoCorrect={false}
          returnKeyType="done"
          className="mt-3 min-h-[52px] rounded-2xl border border-slate-300 bg-white px-4 py-3 text-base text-slate-900"
          onChangeText={(value) => setWalletForm((form) => updateWalletBalanceCommandForm(form, "accountId", value))}
          placeholderTextColor="#64748b"
          placeholder="wallet-account-id"
          value={walletForm.accountId}
        />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Load wallet balance"
          className="mt-3 min-h-[48px] justify-center rounded-full bg-[#0b57d0] px-5 py-3"
          onPress={submitWallet}
        >
          <Text className="text-center text-sm font-semibold text-white">
            Check Balance
          </Text>
        </Pressable>
        <Text accessibilityLiveRegion="polite" className="mt-2 text-xs font-medium text-slate-600">Status: {walletBalanceStatus}</Text>
      </View>

      <View className="mt-4 rounded-[24px] border border-slate-200 bg-slate-50 p-4">
        <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-600">Send payment</Text>
        <View className="mt-2 flex-row flex-wrap justify-between">
          <CommandInput compact={isCompact} label="Saga id" value={paymentForm.sagaId} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "sagaId", value))} />
          <CommandInput compact={isCompact} label="Idempotency key" value={paymentForm.idempotencyKey} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "idempotencyKey", value))} />
          <CommandInput compact={isCompact} label="User id" value={paymentForm.userId} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "userId", value))} />
          <CommandInput compact={isCompact} label="From account" value={paymentForm.fromAccountId} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "fromAccountId", value))} />
          <CommandInput compact={isCompact} label="To account" value={paymentForm.toAccountId} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "toAccountId", value))} />
          <CommandInput compact={isCompact} label="Amount" value={paymentForm.amount} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "amount", value))} />
          <CommandInput compact={isCompact} label="Currency" value={paymentForm.currency} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "currency", value))} />
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Start payment"
          className="mt-3 min-h-[48px] justify-center rounded-full bg-[#0b57d0] px-5 py-3"
          onPress={submitPayment}
        >
          <Text className="text-center text-sm font-semibold text-white">
            Send Payment
          </Text>
        </Pressable>
        <Text accessibilityLiveRegion="polite" className="mt-2 text-xs font-medium text-slate-600">Status: {paymentStartStatus}</Text>
      </View>

      <Text accessibilityLiveRegion="polite" className="mt-3 text-sm font-medium text-blue-800">{formMessage}</Text>
    </View>
  );
}

function CommandInput({ compact, label, value, onChange }: { compact: boolean; label: string; value: string; onChange: (value: string) => void }) {
  return (
    <View className="mb-3" style={{ width: compact ? "100%" : "48%" }}>
      <Text className="text-[11px] font-medium text-slate-600">{label}</Text>
      <TextInput
        accessibilityLabel={label}
        autoCapitalize="none"
        autoCorrect={false}
        returnKeyType="next"
        className="mt-2 min-h-[52px] rounded-2xl border border-slate-300 bg-white px-4 py-3 text-base text-slate-900"
        onChangeText={onChange}
        placeholderTextColor="#64748b"
        value={value}
      />
    </View>
  );
}

function messageFrom(error: unknown): string {
  return error instanceof Error ? error.message : "Please review the transfer details.";
}
