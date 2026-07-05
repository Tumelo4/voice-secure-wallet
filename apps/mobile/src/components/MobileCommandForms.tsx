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
    <View className="mt-4 overflow-hidden rounded-[32px] border border-white/10 bg-white/5 p-5">
      <View className="absolute right-0 top-0 h-28 w-28 rounded-full bg-emerald-400/10" />
      <View className="absolute -bottom-10 left-4 h-36 w-36 rounded-full bg-sky-400/10" />
      <Text className="text-[10px] font-black uppercase tracking-[4px] text-emerald-200/70">Money movement</Text>
      <Text className="mt-2 text-2xl font-black tracking-[-1px] text-white">Transfers and payments</Text>
      <Text className="mt-2 text-sm leading-6 text-slate-300">
        Secure React Native forms validate user input before anything reaches
        the Redux request boundary.
      </Text>

      <View className="mt-4 rounded-[28px] border border-white/10 bg-[#0b1724] p-4">
        <Text className="text-[10px] font-black uppercase tracking-[3px] text-emerald-200/70">Check balance</Text>
        <TextInput
          accessibilityLabel="Wallet account id"
          className="mt-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-base text-white"
          onChangeText={(value) => setWalletForm((form) => updateWalletBalanceCommandForm(form, "accountId", value))}
          placeholderTextColor="#8ba1b2"
          placeholder="wallet-account-id"
          value={walletForm.accountId}
        />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Load wallet balance"
          className="mt-3 rounded-2xl bg-emerald-400 px-4 py-3"
          onPress={submitWallet}
        >
          <Text className="text-center text-sm font-black uppercase tracking-[2px] text-[#071521]">
            Check Balance
          </Text>
        </Pressable>
        <Text className="mt-2 text-xs font-bold text-slate-400">Status: {walletBalanceStatus}</Text>
      </View>

      <View className="mt-4 rounded-[28px] border border-white/10 bg-[#0b1724] p-4">
        <Text className="text-[10px] font-black uppercase tracking-[3px] text-sky-200/70">Send payment</Text>
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
          className="mt-3 rounded-2xl bg-sky-400 px-4 py-3"
          onPress={submitPayment}
        >
          <Text className="text-center text-sm font-black uppercase tracking-[2px] text-[#071521]">
            Send Payment
          </Text>
        </Pressable>
        <Text className="mt-2 text-xs font-bold text-slate-400">Status: {paymentStartStatus}</Text>
      </View>

      <Text className="mt-3 text-sm font-bold text-emerald-200">{formMessage}</Text>
    </View>
  );
}

function CommandInput({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <View className="mb-2 mr-2 min-w-[132px] flex-1">
      <Text className="text-[10px] font-black uppercase tracking-[2px] text-slate-400">{label}</Text>
      <TextInput
        accessibilityLabel={label}
        className="mt-1 rounded-2xl border border-white/10 bg-white/5 px-3 py-2 text-sm text-white"
        onChangeText={onChange}
        placeholderTextColor="#8ba1b2"
        value={value}
      />
    </View>
  );
}

function messageFrom(error: unknown): string {
  return error instanceof Error ? error.message : "Please review the transfer details.";
}
