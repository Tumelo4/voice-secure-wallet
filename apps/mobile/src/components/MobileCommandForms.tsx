import { Pressable, Text, TextInput, View } from "react-native";
import { useState } from "react";
import type { StartPaymentCommand } from "../api/voiceSecureApiClient";
import type { BankingLayoutMode } from "./bankingLayout";

const accountOptions = [
  { id: "11111111-1111-4111-8111-111111111111", label: "Everyday • 5124" },
  { id: "33333333-3333-4333-8333-333333333333", label: "Savings • 2088" },
] as const;

const beneficiaryOptions = [
  { id: "22222222-2222-4222-8222-222222222222", label: "Maya Nkosi" },
  { id: "44444444-4444-4444-8444-444444444444", label: "City Power" },
] as const;
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
      setFormMessage(`Transfer ready for ${command.amount.currency} ${command.amount.value}.`);
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
        <Text className="mt-3 text-sm font-medium text-slate-700">Account</Text>
        <View className="mt-2 flex-row flex-wrap">
          {accountOptions.map((account) => (
            <ChoiceChip
              key={account.id}
              label={account.label}
              selected={walletForm.accountId === account.id}
              onPress={() => setWalletForm((form) => updateWalletBalanceCommandForm(form, "accountId", account.id))}
            />
          ))}
        </View>
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
        <Text className="mt-3 text-sm font-medium text-slate-700">From</Text>
        <View className="mt-2 flex-row flex-wrap">
          {accountOptions.map((account) => (
            <ChoiceChip
              key={account.id}
              label={account.label}
              selected={paymentForm.sourceAccountId === account.id}
              onPress={() => setPaymentForm((form) => updatePaymentCommandForm(form, "sourceAccountId", account.id))}
            />
          ))}
        </View>
        <Text className="mt-3 text-sm font-medium text-slate-700">Beneficiary</Text>
        <View className="mt-2 flex-row flex-wrap">
          {beneficiaryOptions.map((beneficiary) => (
            <ChoiceChip
              key={beneficiary.id}
              label={beneficiary.label}
              selected={paymentForm.beneficiaryId === beneficiary.id}
              onPress={() => setPaymentForm((form) => updatePaymentCommandForm(form, "beneficiaryId", beneficiary.id))}
            />
          ))}
        </View>
        <View className="mt-3 flex-row flex-wrap justify-between">
          <CommandInput compact={isCompact} label="Amount" value={paymentForm.amount} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "amount", value))} />
          <View className="mb-3" style={{ width: isCompact ? "100%" : "48%" }}>
            <Text className="mb-1 text-xs font-medium text-slate-600">Currency</Text>
            <View accessibilityLabel="Payment currency" className="min-h-[52px] justify-center rounded-2xl border border-slate-200 bg-slate-100 px-4 py-3">
              <Text className="text-base font-semibold text-slate-700">{paymentForm.currency}</Text>
            </View>
          </View>
          <CommandInput compact={isCompact} label="Payment reference" value={paymentForm.reference} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "reference", value))} />
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

function ChoiceChip({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable
      accessibilityLabel={label}
      accessibilityRole="radio"
      accessibilityState={{ checked: selected }}
      className={`mb-2 mr-2 min-h-[48px] justify-center rounded-full border px-4 py-3 ${
        selected ? "border-blue-700 bg-blue-50" : "border-slate-300 bg-white"
      }`}
      onPress={onPress}
    >
      <Text className={`text-sm font-semibold ${selected ? "text-blue-800" : "text-slate-700"}`}>{label}</Text>
    </Pressable>
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
