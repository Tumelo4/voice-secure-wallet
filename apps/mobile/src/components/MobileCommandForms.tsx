import { Pressable, Text, TextInput, View } from "react-native";
import { useState } from "react";
import type { StartPaymentCommand } from "../api/voiceSecureApiClient";
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
  walletBalanceStatus: string;
  paymentStartStatus: string;
  onWalletCommand?: (accountId: string) => void | Promise<void>;
  onPaymentCommand?: (command: StartPaymentCommand) => void | Promise<void>;
}

export function MobileCommandForms({
  walletBalanceStatus,
  paymentStartStatus,
  onWalletCommand,
  onPaymentCommand,
}: MobileCommandFormsProps) {
  const [walletForm, setWalletForm] = useState<WalletBalanceCommandForm>(() => createWalletBalanceCommandForm());
  const [paymentForm, setPaymentForm] = useState<PaymentCommandForm>(() => createPaymentCommandForm());
  const [formMessage, setFormMessage] = useState("Ready for user-triggered commands.");

  const submitWallet = () => {
    try {
      const accountId = walletAccountIdFromForm(walletForm);
      setFormMessage(`Wallet command ready for ${accountId}.`);
      void onWalletCommand?.(accountId);
    } catch (error) {
      setFormMessage(messageFrom(error));
    }
  };

  const submitPayment = () => {
    try {
      const command = paymentCommandFromForm(paymentForm);
      setFormMessage(`Payment command ready for ${command.currency} ${command.amount}.`);
      void onPaymentCommand?.(command);
    } catch (error) {
      setFormMessage(messageFrom(error));
    }
  };

  return (
    <View className="mt-4 rounded-[32px] border border-emerald-100 bg-emerald-50/80 p-5">
      <Text className="text-xs font-black uppercase tracking-[3px] text-emerald-700">Screen command forms</Text>
      <Text className="mt-2 text-2xl font-black tracking-[-1px] text-emerald-950">Wallet and payment actions</Text>
      <Text className="mt-2 text-sm leading-6 text-stone-600">
        These React Native form controls validate user input before it reaches
        the Redux API flow boundary.
      </Text>

      <View className="mt-4 rounded-3xl border border-white/80 bg-white/80 p-4">
        <Text className="text-[10px] font-black uppercase tracking-[2px] text-emerald-700">Wallet balance</Text>
        <TextInput
          accessibilityLabel="Wallet account id"
          className="mt-3 rounded-2xl border border-emerald-100 bg-white px-4 py-3 text-base text-emerald-950"
          onChangeText={(value) => setWalletForm((form) => updateWalletBalanceCommandForm(form, "accountId", value))}
          placeholder="wallet-account-id"
          value={walletForm.accountId}
        />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Load wallet balance"
          className="mt-3 rounded-2xl bg-emerald-900 px-4 py-3"
          onPress={submitWallet}
        >
          <Text className="text-center text-sm font-black uppercase tracking-[2px] text-white">
            Load Balance
          </Text>
        </Pressable>
        <Text className="mt-2 text-xs font-bold text-stone-500">Status: {walletBalanceStatus}</Text>
      </View>

      <View className="mt-4 rounded-3xl border border-white/80 bg-white/80 p-4">
        <Text className="text-[10px] font-black uppercase tracking-[2px] text-orange-700">Start payment</Text>
        <View className="mt-2 flex-row flex-wrap">
          <CommandInput label="Saga id" value={paymentForm.sagaId} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "sagaId", value))} />
          <CommandInput label="Idempotency key" value={paymentForm.idempotencyKey} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "idempotencyKey", value))} />
          <CommandInput label="User id" value={paymentForm.userId} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "userId", value))} />
          <CommandInput label="From account" value={paymentForm.fromAccountId} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "fromAccountId", value))} />
          <CommandInput label="To account" value={paymentForm.toAccountId} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "toAccountId", value))} />
          <CommandInput label="Amount" value={paymentForm.amount} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "amount", value))} />
          <CommandInput label="Currency" value={paymentForm.currency} onChange={(value) => setPaymentForm((form) => updatePaymentCommandForm(form, "currency", value))} />
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Start payment"
          className="mt-3 rounded-2xl bg-orange-600 px-4 py-3"
          onPress={submitPayment}
        >
          <Text className="text-center text-sm font-black uppercase tracking-[2px] text-white">
            Start Payment
          </Text>
        </Pressable>
        <Text className="mt-2 text-xs font-bold text-stone-500">Status: {paymentStartStatus}</Text>
      </View>

      <Text className="mt-3 text-sm font-bold text-emerald-800">{formMessage}</Text>
    </View>
  );
}

function CommandInput({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <View className="mb-2 mr-2 min-w-[132px] flex-1">
      <Text className="text-[10px] font-black uppercase tracking-[2px] text-stone-500">{label}</Text>
      <TextInput
        accessibilityLabel={label}
        className="mt-1 rounded-2xl border border-stone-100 bg-white px-3 py-2 text-sm text-emerald-950"
        onChangeText={onChange}
        value={value}
      />
    </View>
  );
}

function messageFrom(error: unknown): string {
  return error instanceof Error ? error.message : "mobile command form is invalid";
}
