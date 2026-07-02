import { SafeAreaView, ScrollView, Text, View } from "react-native";
import {
  selectActivePhase,
  selectMobileClassNames,
  selectReadiness,
  selectSummaryCards,
} from "../state/readinessSlice";
import { useAppSelector } from "../state/store";

export function ReadinessDashboard() {
  const readiness = useAppSelector(selectReadiness);
  const activePhase = useAppSelector(selectActivePhase);
  const cards = useAppSelector(selectSummaryCards);
  const classes = useAppSelector(selectMobileClassNames);

  return (
    <SafeAreaView className={classes.screen}>
      <ScrollView className="flex-1">
        <View className="px-5 pb-10 pt-6">
        <View className="mb-5 rounded-[32px] border border-white/70 bg-amber-100/80 p-6 shadow-sm">
          <Text className="mb-3 text-xs font-black uppercase tracking-[3px] text-orange-700">
            VoiceSecure Wallet
          </Text>
          <Text className="text-5xl font-black leading-[50px] tracking-[-4px] text-emerald-950">
            Mobile readiness command room.
          </Text>
          <Text className="mt-4 text-base leading-7 text-stone-600">
            React Native, NativeWind/Tailwind, and Redux now carry the readiness
            dashboard for service slices, TDD evidence, and launch blockers.
          </Text>
        </View>

        <View className={classes.metricGrid}>
          {cards.map((card) => (
            <View key={card.label} className={`${classes.card} mb-3 mr-3 flex-1`} accessible accessibilityLabel={card.accessibilityLabel}>
              <Text className="text-[10px] font-black uppercase tracking-[2px] text-stone-500">{card.label}</Text>
              <Text className="mt-3 text-3xl font-black text-emerald-950">{card.value}</Text>
              <Text className="mt-2 text-sm leading-5 text-stone-600">{card.detail}</Text>
            </View>
          ))}
        </View>

        <View className={classes.card}>
          <Text className="text-xs font-black uppercase tracking-[3px] text-orange-700">Active phase</Text>
          <Text className="mt-2 text-3xl font-black tracking-[-2px] text-emerald-950">{activePhase.name}</Text>
          <Text className="mt-3 text-base leading-7 text-stone-600">{activePhase.evidence}</Text>
        </View>

        <View className="mt-4">
          {readiness.phases.map((phase, index) => (
            <View
              key={phase.name}
              className={`mb-3 rounded-3xl border p-4 ${
                phase.status === "active" ? classes.activePhase : "border-stone-200 bg-white/70"
              }`}
            >
              <Text className="text-xs font-black uppercase tracking-[2px] text-stone-500">
                {String(index + 1).padStart(2, "0")} {phase.status}
              </Text>
              <Text className="mt-1 text-xl font-black text-emerald-950">{phase.name}</Text>
              <Text className="mt-1 text-sm leading-6 text-stone-600">{phase.evidence}</Text>
            </View>
          ))}
        </View>

        <View className="mt-3">
          <Text className="mb-3 text-xs font-black uppercase tracking-[3px] text-orange-700">Risks to burn down</Text>
          {readiness.blockers.map((blocker) => (
            <View key={blocker.title} className="mb-3 rounded-3xl border border-orange-200 bg-orange-50/80 p-4">
              <Text className="text-lg font-black text-orange-800">{blocker.title}</Text>
              <Text className="mt-1 text-sm leading-6 text-stone-600">{blocker.detail}</Text>
            </View>
          ))}
        </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
