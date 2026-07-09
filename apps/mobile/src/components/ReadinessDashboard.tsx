import { useEffect, useRef, useState } from "react";
import {
  Animated,
  Easing,
  Pressable,
  SafeAreaView,
  ScrollView,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from "react-native";
import { MobileCommandForms } from "./MobileCommandForms";
import {
  bankingAccountCards,
  bankingCardDeck,
  bankingHero,
  bankingInsights,
  bankingProfileRows,
  bankingQuickActions,
  bankingSpendingBars,
  bankingTabs,
  bankingTransactionGroups,
  type BankingTabKey,
} from "./bankingDashboardContent";
import {
  bankingScreenChromeForWidth,
  bankingLayoutModeForWidth,
  composerColumns,
  quickActionColumns,
  usesSideRail,
  type BankingLayoutMode,
} from "./bankingLayout";
import {
  advanceVoiceSecureFlow,
  approveVoiceSecureFallback,
  createTransactionDraft,
  createVoiceSecureFlow,
  type BankingTransactionIntent,
  type TransactionDraft,
  type VoiceSecureFlow,
} from "../state/bankingVoiceSecure";

export function ReadinessDashboard() {
  const { width } = useWindowDimensions();
  const layoutMode = bankingLayoutModeForWidth(width);
  const screenChrome = bankingScreenChromeForWidth(width);
  const isCompact = layoutMode === "compact";
  const showRail = usesSideRail(layoutMode);
  const [activeTab, setActiveTab] = useState<BankingTabKey>("home");
  const [draft, setDraft] = useState<TransactionDraft>(() => createTransactionDraft("pay"));
  const [flow, setFlow] = useState<VoiceSecureFlow | null>(null);
  const [walletCommandStatus, setWalletCommandStatus] = useState("Ready to check balance.");
  const [paymentCommandStatus, setPaymentCommandStatus] = useState("Ready to send payment.");

  const micPulse = useRef(new Animated.Value(0)).current;
  const wavePulse = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (!flow || flow.stage === "confirmed") {
      micPulse.stopAnimation();
      wavePulse.stopAnimation();
      micPulse.setValue(0);
      wavePulse.setValue(0);
      return;
    }

    const micLoop = Animated.loop(
      Animated.sequence([
        Animated.timing(micPulse, {
          toValue: 1,
          duration: 900,
          easing: Easing.inOut(Easing.ease),
          useNativeDriver: true,
        }),
        Animated.timing(micPulse, {
          toValue: 0,
          duration: 900,
          easing: Easing.inOut(Easing.ease),
          useNativeDriver: true,
        }),
      ]),
    );

    const waveLoop = Animated.loop(
      Animated.sequence([
        Animated.timing(wavePulse, {
          toValue: 1,
          duration: 700,
          easing: Easing.inOut(Easing.quad),
          useNativeDriver: true,
        }),
        Animated.timing(wavePulse, {
          toValue: 0,
          duration: 700,
          easing: Easing.inOut(Easing.quad),
          useNativeDriver: true,
        }),
      ]),
    );

    micLoop.start();
    waveLoop.start();

    return () => {
      micLoop.stop();
      waveLoop.stop();
    };
  }, [flow, micPulse, wavePulse]);

  const openPayments = (intent: BankingTransactionIntent) => {
    setDraft((current) => ({ ...current, intent }));
    setActiveTab("payments");
    setFlow(null);
  };

  const beginVoiceSecure = (nextDraft: TransactionDraft = draft) => {
    setDraft(nextDraft);
    setFlow(createVoiceSecureFlow(nextDraft));
  };

  const confirmVoice = () => {
    setFlow((current) => (current ? advanceVoiceSecureFlow(current, "match") : current));
  };

  const retryVoice = () => {
    setFlow((current) => (current ? advanceVoiceSecureFlow(current, "miss") : current));
  };

  const useFallback = (method: "PIN" | "Face ID" | "Fingerprint") => {
    setFlow((current) => (current ? approveVoiceSecureFallback(current, method) : current));
  };

  const resetPayment = (tab: BankingTabKey = "home") => {
    setFlow(null);
    setActiveTab(tab);
  };

  return (
    <SafeAreaView className="flex-1 overflow-hidden bg-[#f4f7fb]">
      <View className="flex-1 flex-row overflow-hidden">
        {showRail ? <NavigationRail activeTab={activeTab} onChange={setActiveTab} /> : null}

        <ScrollView
          className={screenChrome.scrollViewClassName}
          contentContainerStyle={{
            paddingBottom: isCompact ? 192 : 56,
            paddingHorizontal: screenChrome.contentPaddingHorizontal,
          }}
        >
          <View
            style={{
              alignSelf: "center",
              width: "100%",
              paddingTop: isCompact ? 16 : 24,
              ...(screenChrome.contentMaxWidth ? { maxWidth: screenChrome.contentMaxWidth } : {}),
            }}
          >
            <Header layoutMode={layoutMode} />

            {activeTab === "home" ? (
              <HomeTab
                layoutMode={layoutMode}
                walletCommandStatus={walletCommandStatus}
                paymentCommandStatus={paymentCommandStatus}
                onOpenPayments={openPayments}
                onWalletCommand={(accountId) => setWalletCommandStatus(`Prepared for ${accountId}.`)}
                onPaymentCommand={(command) =>
                  setPaymentCommandStatus(`Prepared for ${command.currency} ${command.amount.toFixed(2)}.`)
                }
              />
            ) : null}

            {activeTab === "payments" ? (
              <PaymentsTab
                layoutMode={layoutMode}
                draft={draft}
                flow={flow}
                micPulse={micPulse}
                wavePulse={wavePulse}
                onDraftChange={setDraft}
                onBeginVoiceSecure={beginVoiceSecure}
                onConfirmVoice={confirmVoice}
                onRetryVoice={retryVoice}
                onFallback={useFallback}
                onDone={() => resetPayment("home")}
                onSendAnother={() => resetPayment("payments")}
              />
            ) : null}

            {activeTab === "cards" ? <CardsTab layoutMode={layoutMode} /> : null}
            {activeTab === "insights" ? <InsightsTab layoutMode={layoutMode} /> : null}
            {activeTab === "profile" ? <ProfileTab layoutMode={layoutMode} /> : null}
          </View>
        </ScrollView>
      </View>

      {showRail ? null : <BottomTabs activeTab={activeTab} onChange={setActiveTab} />}
    </SafeAreaView>
  );
}

function Header({ layoutMode }: { layoutMode: BankingLayoutMode }) {
  const isCompact = layoutMode === "compact";

  return (
    <View className={`mb-6 ${isCompact ? "flex-col" : "flex-row items-start justify-between"}`}>
      <View className={isCompact ? "" : "max-w-[720px] pr-4"}>
        <Text className="text-[11px] font-medium tracking-[0.08em] text-slate-500">
          {bankingHero.brand}
        </Text>
        <Text className="mt-2 text-3xl font-semibold tracking-[-0.03em] text-slate-900">
          {bankingHero.greeting}
        </Text>
        <Text className="mt-2 text-sm leading-6 text-slate-600">
          Consumer banking with a calm, native feel and VoiceSecure on every transaction.
        </Text>
      </View>

      <View className={`mt-4 rounded-full border border-slate-200 bg-white px-4 py-2 shadow-sm ${isCompact ? "self-start" : ""}`}>
        <Text className="text-xs font-medium text-emerald-700">
          {bankingHero.securityNote}
        </Text>
      </View>
    </View>
  );
}

function HomeTab({
  layoutMode,
  walletCommandStatus,
  paymentCommandStatus,
  onOpenPayments,
  onWalletCommand,
  onPaymentCommand,
}: {
  layoutMode: BankingLayoutMode;
  walletCommandStatus: string;
  paymentCommandStatus: string;
  onOpenPayments: (intent: BankingTransactionIntent) => void;
  onWalletCommand: (accountId: string) => void;
  onPaymentCommand: (command: { amount: number; currency: string }) => void;
}) {
  const isCompact = layoutMode === "compact";
  const isExpanded = layoutMode === "expanded";

  return (
    <View style={{ flexDirection: isExpanded ? "row" : "column" }}>
      <View style={{ flex: isExpanded ? 1.12 : undefined, marginRight: isExpanded ? 20 : 0 }}>
        <View className="overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-sm">
          <View className="h-1.5 bg-[#0b57d0]" />
          <View className="p-5">
            <Text className="text-[11px] font-medium tracking-[0.08em] text-slate-500">
              {bankingHero.accountName}
            </Text>
            <Text className="mt-2 text-sm font-medium text-slate-600">
              {bankingHero.balanceLabel}
            </Text>
            <Text className="mt-2 text-5xl font-semibold tracking-[-0.05em] text-slate-900">
              {bankingHero.balance}
            </Text>
            <Text className="mt-3 text-sm leading-6 text-slate-600">
              {bankingHero.statusNote}
            </Text>

            <View className="mt-5 flex-row flex-wrap">
              <HeroChip label="Card ending" value="5124" />
              <HeroChip label="Savings" value="R 12,400.00" />
              <HeroChip label="Travel" value="R 3,840.00" />
            </View>
          </View>
        </View>

        <SectionHeading
          title="Move money"
          detail="Pay a bill, transfer money, top up, or scan a QR code."
        />
        <View className="mt-4 flex-row flex-wrap justify-between">
          {bankingQuickActions.map((action) => (
            <QuickActionButton
              key={action.label}
              action={action}
              layoutMode={layoutMode}
              onPress={() => onOpenPayments(action.intent)}
            />
          ))}
        </View>

        <SectionHeading
          title="Account overview"
          detail="At-a-glance account details and activity."
        />
        {isCompact ? (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} className="mt-4 -mx-1">
            {bankingAccountCards.map((card) => (
              <AccountCard key={card.name} card={card} layoutMode={layoutMode} />
            ))}
          </ScrollView>
        ) : (
          <View className="mt-4 flex-row flex-wrap justify-between">
            {bankingAccountCards.map((card) => (
              <AccountCard key={card.name} card={card} layoutMode={layoutMode} />
            ))}
          </View>
        )}

        <MobileCommandForms
          walletBalanceStatus={walletCommandStatus}
          paymentStartStatus={paymentCommandStatus}
          onWalletCommand={onWalletCommand}
          onPaymentCommand={onPaymentCommand}
        />
      </View>

      <View style={{ flex: isExpanded ? 0.88 : undefined, marginLeft: isExpanded ? 20 : 0, marginTop: isExpanded ? 0 : 24 }}>
        <SectionHeading
          title="Recent activity"
          detail="Transactions grouped by date with merchant, category, and amount."
        />
        <View className="mt-4">
          {bankingTransactionGroups.map((group) => (
            <TransactionGroupCard key={group.dateLabel} group={group} />
          ))}
        </View>
      </View>
    </View>
  );
}

interface PaymentsTabProps {
  layoutMode: BankingLayoutMode;
  draft: TransactionDraft;
  flow: VoiceSecureFlow | null;
  micPulse: Animated.Value;
  wavePulse: Animated.Value;
  onDraftChange: (draft: TransactionDraft) => void;
  onBeginVoiceSecure: (draft: TransactionDraft) => void;
  onConfirmVoice: () => void;
  onRetryVoice: () => void;
  onFallback: (method: "PIN" | "Face ID" | "Fingerprint") => void;
  onDone: () => void;
  onSendAnother: () => void;
}

function PaymentsTab({
  layoutMode,
  draft,
  flow,
  micPulse,
  wavePulse,
  onDraftChange,
  onBeginVoiceSecure,
  onConfirmVoice,
  onRetryVoice,
  onFallback,
  onDone,
  onSendAnother,
}: PaymentsTabProps) {
  const intentLabel = transactionIntentLabel(draft.intent);
  const successTitle = draft.intent === "topup" ? "Top up complete" : "Payment sent";
  const successDetail =
    draft.intent === "topup"
      ? `Your top up for ${draft.recipient} is complete.`
      : `${intentLabel} to ${draft.recipient} is complete.`;

  return (
    <View>
      <SectionHeading
        title="Send money with VoiceSecure"
        detail="Fill in the details, then verify with VoiceSecure before anything is sent."
      />

      {flow?.stage === "confirmed" ? (
        <SuccessCard
          title={successTitle}
          detail={successDetail}
          amount={draft.amount}
          recipient={draft.recipient}
          method={flow.statusLabel}
          onDone={onDone}
          onSendAnother={onSendAnother}
        />
      ) : flow ? (
        <VoiceSecureCard
          draft={draft}
          flow={flow}
          micPulse={micPulse}
          wavePulse={wavePulse}
          onConfirmVoice={onConfirmVoice}
          onRetryVoice={onRetryVoice}
          onFallback={onFallback}
        />
      ) : (
        <PaymentComposer
          layoutMode={layoutMode}
          draft={draft}
          intentLabel={intentLabel}
          onDraftChange={onDraftChange}
          onBeginVoiceSecure={onBeginVoiceSecure}
        />
      )}
    </View>
  );
}

function PaymentComposer({
  layoutMode,
  draft,
  intentLabel,
  onDraftChange,
  onBeginVoiceSecure,
}: {
  layoutMode: BankingLayoutMode;
  draft: TransactionDraft;
  intentLabel: string;
  onDraftChange: (draft: TransactionDraft) => void;
  onBeginVoiceSecure: (draft: TransactionDraft) => void;
}) {
  const fieldColumns = composerColumns(layoutMode);
  const setField = (field: keyof TransactionDraft, value: string) => {
    onDraftChange({ ...draft, [field]: value });
  };

  return (
    <View className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
      <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-500">
        Transfer details
      </Text>
      <Text className="mt-2 text-2xl font-semibold tracking-[-0.03em] text-slate-900">
        {intentLabel} money in a few taps
      </Text>
      <Text className="mt-2 text-sm leading-6 text-slate-600">
        Keep the details simple. VoiceSecure steps in before final confirmation.
      </Text>

      <View className="mt-5 rounded-[24px] border border-slate-200 bg-slate-50 p-4">
        <LabeledField
          label="Recipient"
          value={draft.recipient}
          placeholder="Maya Nkosi"
          onChangeText={(value) => setField("recipient", value)}
        />
        <View
          className="mt-4"
          style={{ flexDirection: fieldColumns === 1 ? "column" : "row" }}
        >
          <View
            style={{
              flex: 1,
              marginRight: fieldColumns === 1 ? 0 : 12,
              marginBottom: fieldColumns === 1 ? 16 : 0,
            }}
          >
            <LabeledField
              label="Amount"
              value={draft.amount}
              placeholder="750.00"
              keyboardType="decimal-pad"
              onChangeText={(value) => setField("amount", value)}
            />
          </View>
          <View style={{ flex: 1 }}>
            <LabeledField
              label="Note"
              value={draft.note}
              placeholder="Dinner split"
              onChangeText={(value) => setField("note", value)}
            />
          </View>
        </View>
      </View>

      <View className="mt-5 flex-row flex-wrap justify-between">
        <ActionPill
          label="Pay"
          selected={draft.intent === "pay"}
          layoutMode={layoutMode}
          onPress={() => {
            const nextDraft = { ...draft, intent: "pay" as BankingTransactionIntent };
            onBeginVoiceSecure(nextDraft);
          }}
        />
        <ActionPill
          label="Send"
          selected={draft.intent === "send"}
          layoutMode={layoutMode}
          onPress={() => {
            const nextDraft = { ...draft, intent: "send" as BankingTransactionIntent };
            onBeginVoiceSecure(nextDraft);
          }}
        />
        <ActionPill
          label="Top up"
          selected={draft.intent === "topup"}
          layoutMode={layoutMode}
          onPress={() => {
            const nextDraft = { ...draft, intent: "topup" as BankingTransactionIntent };
            onBeginVoiceSecure(nextDraft);
          }}
        />
      </View>
      <Text className="mt-2 text-xs font-medium text-slate-500">
        VoiceSecure opens the moment you tap an action.
      </Text>
    </View>
  );
}

function VoiceSecureCard({
  draft,
  flow,
  micPulse,
  wavePulse,
  onConfirmVoice,
  onRetryVoice,
  onFallback,
}: {
  draft: TransactionDraft;
  flow: VoiceSecureFlow;
  micPulse: Animated.Value;
  wavePulse: Animated.Value;
  onConfirmVoice: () => void;
  onRetryVoice: () => void;
  onFallback: (method: "PIN" | "Face ID" | "Fingerprint") => void;
}) {
  const micScale = micPulse.interpolate({ inputRange: [0, 1], outputRange: [0.96, 1.08] });
  const micOpacity = micPulse.interpolate({ inputRange: [0, 1], outputRange: [0.8, 1] });
  const waveOpacity = wavePulse.interpolate({ inputRange: [0, 1], outputRange: [0.45, 1] });
  const waveScale = wavePulse.interpolate({ inputRange: [0, 1], outputRange: [0.72, 1.1] });

  return (
    <View className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
      <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-500">
        VoiceSecure
      </Text>
      <Text className="mt-2 text-3xl font-semibold tracking-[-0.03em] text-slate-900">
        Verifying your voice...
      </Text>
      <Text className="mt-2 text-sm leading-6 text-slate-600">{flow.prompt}</Text>

      <View className="mt-5 rounded-[24px] border border-slate-200 bg-slate-50 p-4">
        <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-500">
          Review
        </Text>
        <Text className="mt-2 text-lg font-semibold text-slate-900">{draft.recipient}</Text>
        <Text className="mt-1 text-sm text-slate-500">{transactionIntentLabel(draft.intent)}</Text>
        <Text className="mt-3 text-4xl font-semibold tracking-[-0.04em] text-slate-900">
          R {draft.amount}
        </Text>
      </View>

      <View className="mt-5 items-center">
        <Animated.View
          style={{ transform: [{ scale: micScale }], opacity: micOpacity }}
          className="items-center justify-center rounded-full border border-emerald-100 bg-emerald-50 p-6 shadow-sm"
        >
          <Text className="text-5xl">🎤</Text>
        </Animated.View>
        <Text className="mt-4 text-sm font-medium text-emerald-700">
          {flow.statusLabel}
        </Text>
      </View>

      <View className="mt-4 flex-row items-end justify-center">
        {[18, 32, 24, 42, 26, 34, 20].map((height, index) => (
          <Animated.View
            key={`${height}-${index}`}
            style={{ height, opacity: waveOpacity, transform: [{ scaleY: waveScale }] }}
            className={`mx-1 w-2 rounded-full ${waveBarClass(index)}`}
          />
        ))}
      </View>

      <Text className="mt-4 text-center text-sm font-medium text-slate-600">
        Say "Confirm payment" to continue
      </Text>
      <Text className="mt-1 text-center text-sm text-slate-500">{flow.message}</Text>

      <View className="mt-5 flex-row flex-wrap justify-center">
        <VoicePill label="I said it" tone="emerald" onPress={onConfirmVoice} />
        <VoicePill label="Try again" tone="slate" onPress={onRetryVoice} />
      </View>

      {flow.stage === "fallback" ? (
        <View className="mt-4 rounded-[24px] border border-amber-100 bg-amber-50 p-4">
          <Text className="text-sm font-semibold text-amber-900">
            We couldn't verify your voice. Try again or use another way.
          </Text>
          <View className="mt-4 flex-row flex-wrap">
            {flow.fallbackOptions.map((method) => (
              <VoicePill key={method} label={method} tone="amber" onPress={() => onFallback(method)} />
            ))}
          </View>
        </View>
      ) : null}
    </View>
  );
}

function SuccessCard({
  title,
  detail,
  amount,
  recipient,
  method,
  onDone,
  onSendAnother,
}: {
  title: string;
  detail: string;
  amount: string;
  recipient: string;
  method: string;
  onDone: () => void;
  onSendAnother: () => void;
}) {
  return (
    <View className="rounded-[28px] border border-emerald-100 bg-white p-5 shadow-sm">
      <View className="items-center rounded-[24px] bg-emerald-50 p-5">
        <Text className="text-5xl">✓</Text>
        <Text className="mt-4 text-[11px] font-medium tracking-[0.06em] text-emerald-700">
          {method}
        </Text>
        <Text className="mt-2 text-3xl font-semibold tracking-[-0.03em] text-emerald-950">{title}</Text>
        <Text className="mt-2 text-sm leading-6 text-emerald-900/75">{detail}</Text>
        <Text className="mt-4 text-4xl font-semibold tracking-[-0.04em] text-emerald-950">
          R {amount}
        </Text>
        <Text className="mt-1 text-sm text-emerald-900/75">To {recipient}</Text>
      </View>

      <View className="mt-5 flex-row flex-wrap justify-between">
        <Pressable
          onPress={onDone}
          className="mb-3 rounded-full bg-[#0b57d0] px-5 py-4"
          style={{ width: "48%" }}
        >
          <Text className="text-center text-sm font-semibold text-white">
            Done
          </Text>
        </Pressable>
        <Pressable
          onPress={onSendAnother}
          className="mb-3 rounded-full border border-slate-200 bg-white px-5 py-4"
          style={{ width: "48%" }}
        >
          <Text className="text-center text-sm font-semibold text-slate-900">
            Send another
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

function CardsTab({ layoutMode }: { layoutMode: BankingLayoutMode }) {
  const isCompact = layoutMode === "compact";

  return (
    <View>
      <SectionHeading
        title="A clean card stack"
        detail="Freeze, reveal, and manage cards with a calm banking feel."
      />

      <View className="mt-4" style={{ flexDirection: isCompact ? "column" : "row", flexWrap: "wrap", justifyContent: "space-between" }}>
        {bankingCardDeck.map((card) => (
          <View
            key={card.name}
            className={`mb-4 overflow-hidden rounded-[28px] border p-5 shadow-sm ${cardClass(card.tone)}`}
            style={{ width: isCompact ? "100%" : "48%" }}
          >
            <Text className="text-[11px] font-medium tracking-[0.06em] text-white/80">
              {card.name}
            </Text>
            <Text className="mt-3 text-2xl font-semibold tracking-[-0.03em] text-white">
              {card.maskedNumber}
            </Text>
            <Text className="mt-2 text-sm font-medium text-white/75">{card.expiry}</Text>
            <Text className="mt-4 text-sm leading-6 text-white/80">{card.detail}</Text>
          </View>
        ))}
      </View>

      <View className="flex-row flex-wrap justify-between">
        <ActionPill layoutMode={layoutMode} label="Freeze card" selected={false} onPress={() => undefined} />
        <ActionPill layoutMode={layoutMode} label="Reveal details" selected={false} onPress={() => undefined} />
        <ActionPill layoutMode={layoutMode} label="Set limit" selected={false} onPress={() => undefined} />
      </View>
    </View>
  );
}

function InsightsTab({ layoutMode }: { layoutMode: BankingLayoutMode }) {
  const isCompact = layoutMode === "compact";

  return (
    <View>
      <SectionHeading
        title="See where money goes"
        detail="A monthly spend snapshot with categories that read clearly."
      />

      <View className="mt-4 rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
        <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-500">
          Monthly spending
        </Text>
        <Text className="mt-2 text-3xl font-semibold tracking-[-0.03em] text-slate-900">
          R 6,940.00
        </Text>
        <Text className="mt-2 text-sm text-slate-600">Balanced against your usual spend patterns.</Text>

        <View className="mt-5 flex-row items-end justify-between">
          {bankingSpendingBars.map((height, index) => (
            <View key={`${height}-${index}`} className="items-center justify-end">
              <View style={{ height }} className={`w-8 rounded-t-2xl ${insightBarClass(index)}`} />
            </View>
          ))}
        </View>
      </View>

      <View className="mt-4" style={{ flexDirection: isCompact ? "column" : "row", flexWrap: "wrap", justifyContent: "space-between" }}>
        {bankingInsights.map((insight) => (
          <View
            key={insight.label}
            className="mb-3 rounded-[24px] border border-slate-200 bg-white p-4 shadow-sm"
            style={{ width: isCompact ? "100%" : "32%" }}
          >
            <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-500">
              {insight.label}
            </Text>
            <Text className="mt-2 text-2xl font-semibold text-slate-900">{insight.value}</Text>
            <Text className="mt-1 text-sm text-slate-600">{insight.detail}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

function ProfileTab({ layoutMode }: { layoutMode: BankingLayoutMode }) {
  const isCompact = layoutMode === "compact";

  return (
    <View>
      <SectionHeading
        title="Security and profile"
        detail="VoiceSecure stays active on every transaction, with friendly backup routes."
      />

      <View className="mt-4 rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
        <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-500">
          VoiceSecure Bank member
        </Text>
        <Text className="mt-2 text-3xl font-semibold tracking-[-0.03em] text-slate-900">
          Tumelo M.
        </Text>
        <Text className="mt-2 text-sm text-slate-600">Premier everyday banking experience.</Text>
      </View>

      <View className="mt-4" style={{ flexDirection: isCompact ? "column" : "row", flexWrap: "wrap", justifyContent: "space-between" }}>
        {bankingProfileRows.map((row) => (
          <View
            key={row.label}
            className="mb-3 rounded-[24px] border border-slate-200 bg-white p-4 shadow-sm"
            style={{ width: isCompact ? "100%" : "32%" }}
          >
            <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-500">
              {row.label}
            </Text>
            <Text className="mt-2 text-lg font-semibold text-slate-900">{row.value}</Text>
            <Text className="mt-1 text-sm text-slate-600">{row.detail}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

function BottomTabs({
  activeTab,
  onChange,
}: {
  activeTab: BankingTabKey;
  onChange: (tab: BankingTabKey) => void;
}) {
  return (
    <View className="absolute inset-x-0 bottom-0 px-4 pb-4">
      <View className="flex-row rounded-[28px] border border-slate-200 bg-white px-2 py-2 shadow-lg">
        {bankingTabs.map((tab) => {
          const isActive = tab.key === activeTab;
          return (
            <Pressable
              key={tab.key}
              onPress={() => onChange(tab.key)}
              className={`flex-1 items-center rounded-[20px] px-2 py-2 ${isActive ? "bg-[#0b57d0]" : "bg-transparent"}`}
            >
              <View
                className={`h-8 w-8 items-center justify-center rounded-full ${
                  isActive ? "bg-white/15" : "bg-slate-100"
                }`}
              >
                <Text
                  className={`text-sm font-semibold ${isActive ? "text-white" : "text-slate-600"}`}
                >
                  {tab.icon}
                </Text>
              </View>
              <Text
                className={`mt-1 text-[12px] font-medium ${
                  isActive ? "text-white" : "text-slate-500"
                }`}
              >
                {tab.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

function QuickActionButton({
  action,
  layoutMode,
  onPress,
}: {
  action: (typeof bankingQuickActions)[number];
  layoutMode: BankingLayoutMode;
  onPress: () => void;
}) {
  const width = quickActionColumns(layoutMode) === 4 ? "23%" : "48%";

  return (
    <Pressable
      onPress={onPress}
      className={`mb-3 rounded-[24px] border px-4 py-4 shadow-sm ${quickActionClass(action.tone)}`}
      style={{ width, minHeight: 92 }}
    >
      <View className="flex-row items-center">
        <View className="mr-3 h-10 w-10 items-center justify-center rounded-full bg-white/80">
          <Text className="text-sm font-semibold text-slate-900">{action.icon}</Text>
        </View>
        <View className="flex-1">
          <Text className="text-sm font-semibold text-slate-900" numberOfLines={1}>
            {action.label}
          </Text>
          <Text className="mt-1 text-xs font-medium text-slate-600" numberOfLines={1}>
            {action.detail}
          </Text>
        </View>
      </View>
    </Pressable>
  );
}

function SectionHeading({
  title,
  detail,
}: {
  title: string;
  detail: string;
}) {
  return (
    <View className="mt-8">
      <Text className="mt-2 text-2xl font-semibold tracking-[-0.03em] text-slate-900">{title}</Text>
      <Text className="mt-2 text-sm leading-6 text-slate-600">{detail}</Text>
    </View>
  );
}

function HeroChip({ label, value }: { label: string; value: string }) {
  return (
    <View className="mb-3 mr-3 min-w-[120px] flex-1 rounded-[18px] border border-slate-200 bg-slate-50 px-4 py-3">
      <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-500">{label}</Text>
      <Text className="mt-1 text-sm font-semibold text-slate-900">{value}</Text>
    </View>
  );
}

function AccountCard({
  card,
  layoutMode,
}: {
  card: (typeof bankingAccountCards)[number];
  layoutMode: BankingLayoutMode;
}) {
  const isCompact = layoutMode === "compact";

  return (
    <View
      className={`rounded-[24px] border p-5 shadow-sm ${accountCardClass(card.tone)}`}
      style={{
        width: isCompact ? 250 : "32%",
        marginRight: isCompact ? 12 : 0,
        marginBottom: isCompact ? 0 : 12,
        minHeight: 160,
        justifyContent: "space-between",
      }}
    >
      <View>
        <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-500">
          {card.name}
        </Text>
        <Text className="mt-3 text-sm text-slate-600">{card.detail}</Text>
      </View>
      <View className="mt-4">
        <Text className="text-lg font-semibold text-slate-900">{card.meta}</Text>
        <Text className="mt-1 text-xs font-medium text-slate-500">Tap to view account details</Text>
      </View>
    </View>
  );
}

function TransactionGroupCard({ group }: { group: (typeof bankingTransactionGroups)[number] }) {
  return (
    <View className="mb-4 rounded-[24px] border border-slate-200 bg-white p-4 shadow-sm">
      <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-500">
        {group.dateLabel}
      </Text>
      <View className="mt-3">
        {group.items.map((item, index) => (
          <View
            key={`${group.dateLabel}-${item.merchant}`}
            className={`${index > 0 ? "border-t border-slate-100 pt-4" : ""} flex-row items-center justify-between pb-4`}
          >
            <View className="mr-3 flex-row items-center">
              <View className={`mr-3 h-12 w-12 items-center justify-center rounded-full ${transactionIconClass(item.tone)}`}>
                <Text className="text-sm font-semibold text-white">{item.icon}</Text>
              </View>
              <View>
                <Text className="text-base font-semibold text-slate-900">{item.merchant}</Text>
                <Text className="mt-1 text-sm text-slate-500">
                  {item.category} · {item.status}
                </Text>
              </View>
            </View>
            <Text className={`text-sm font-semibold ${item.tone === "credit" ? "text-emerald-700" : "text-slate-900"}`}>
              {item.amount}
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}

function ActionPill({
  label,
  selected,
  layoutMode,
  onPress,
}: {
  label: string;
  selected: boolean;
  layoutMode: BankingLayoutMode;
  onPress: () => void;
}) {
  const isCompact = layoutMode === "compact";
  const width = composerColumns(layoutMode) === 1 ? "48%" : "31%";

  return (
    <Pressable
      onPress={onPress}
      className={`mb-3 rounded-full px-4 py-3 ${selected ? "bg-[#0b57d0]" : "border border-slate-200 bg-white"}`}
      style={{ width: isCompact ? "48%" : width, minHeight: 52 }}
    >
      <Text className={`text-sm font-semibold ${selected ? "text-white" : "text-slate-900"}`}>
        {label}
      </Text>
    </Pressable>
  );
}

function VoicePill({
  label,
  tone,
  onPress,
}: {
  label: string;
  tone: "emerald" | "slate" | "amber";
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      className={`mb-3 mr-3 rounded-full px-4 py-3 ${voicePillClass(tone)}`}
    >
      <Text className={`text-xs font-semibold ${tone === "slate" ? "text-slate-900" : "text-white"}`}>
        {label}
      </Text>
    </Pressable>
  );
}

function LabeledField({
  label,
  value,
  placeholder,
  onChangeText,
  keyboardType,
}: {
  label: string;
  value: string;
  placeholder: string;
  onChangeText: (value: string) => void;
  keyboardType?: "default" | "decimal-pad";
}) {
  return (
    <View>
      <Text className="text-[11px] font-medium tracking-[0.06em] text-slate-500">{label}</Text>
      <TextInput
        value={value}
        placeholder={placeholder}
        placeholderTextColor="#94a3b8"
        onChangeText={onChangeText}
        keyboardType={keyboardType}
        className="mt-2 rounded-[18px] border border-slate-200 bg-white px-4 py-3 text-base text-slate-900"
      />
    </View>
  );
}

function NavigationRail({
  activeTab,
  onChange,
}: {
  activeTab: BankingTabKey;
  onChange: (tab: BankingTabKey) => void;
}) {
  return (
    <View className="w-[104px] border-r border-slate-200 bg-white px-3 py-4 shadow-sm">
      <Text className="text-[11px] font-medium tracking-[0.08em] text-slate-500">
        VoiceSecure
      </Text>
      <View className="mt-6">
        {bankingTabs.map((tab) => {
          const isActive = tab.key === activeTab;
          return (
            <Pressable
              key={tab.key}
              onPress={() => onChange(tab.key)}
              className="mb-3 items-center rounded-[20px] px-2 py-3"
              style={{ backgroundColor: isActive ? "#e8f0fe" : "transparent" }}
            >
              <View
                className="h-10 w-10 items-center justify-center rounded-2xl"
                style={{ backgroundColor: isActive ? "#0b57d0" : "#f1f5f9" }}
              >
                <Text className={`text-sm font-semibold ${isActive ? "text-white" : "text-slate-600"}`}>
                  {tab.icon}
                </Text>
              </View>
              <Text className={`mt-2 text-[11px] font-medium ${isActive ? "text-slate-900" : "text-slate-500"}`}>
                {tab.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

function transactionIntentLabel(intent: BankingTransactionIntent): string {
  switch (intent) {
    case "pay":
      return "Payment";
    case "send":
      return "Transfer";
    case "topup":
      return "Top up";
  }
}

function accountCardClass(tone: (typeof bankingAccountCards)[number]["tone"]): string {
  switch (tone) {
    case "navy":
      return "border-[#dbe5f1] bg-white";
    case "emerald":
      return "border-emerald-100 bg-emerald-50";
    case "amber":
      return "border-amber-100 bg-amber-50";
  }
}

function quickActionClass(tone: (typeof bankingQuickActions)[number]["tone"]): string {
  switch (tone) {
    case "emerald":
      return "border-emerald-100 bg-emerald-50";
    case "sky":
      return "border-sky-100 bg-sky-50";
    case "amber":
      return "border-amber-100 bg-amber-50";
    case "rose":
      return "border-rose-100 bg-rose-50";
  }
}

function transactionIconClass(tone: "credit" | "debit"): string {
  return tone === "credit" ? "bg-emerald-500" : "bg-slate-700";
}

function cardClass(tone: (typeof bankingCardDeck)[number]["tone"]): string {
  switch (tone) {
    case "navy":
      return "border-[#0d1f33] bg-[#0d1f33]";
    case "emerald":
      return "border-emerald-700 bg-emerald-600";
    case "sky":
      return "border-sky-700 bg-sky-600";
  }
}

function insightBarClass(index: number): string {
  const classes = ["bg-emerald-400", "bg-sky-400", "bg-amber-400"];
  return classes[index % classes.length];
}

function waveBarClass(index: number): string {
  const classes = ["bg-emerald-200", "bg-sky-200", "bg-emerald-300", "bg-sky-300"];
  return classes[index % classes.length];
}

function voicePillClass(tone: "emerald" | "slate" | "amber"): string {
  switch (tone) {
    case "emerald":
      return "bg-emerald-600";
    case "slate":
      return "bg-slate-100";
    case "amber":
      return "bg-amber-500";
  }
}
