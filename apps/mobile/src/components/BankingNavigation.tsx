import { Pressable, Text, View } from "react-native";
import { bankingTabs, type BankingTabKey } from "./bankingDashboardContent";

interface BankingNavigationProps {
  activeTab: BankingTabKey;
  onChange: (tab: BankingTabKey) => void;
}

export function BottomTabs({ activeTab, bottomInset, onChange }: BankingNavigationProps & { bottomInset: number }) {
  return (
    <View className="absolute inset-x-0 bottom-0 px-4" style={{ paddingBottom: Math.max(12, bottomInset) }}>
      <View accessibilityRole="tablist" className="flex-row rounded-[28px] border border-slate-200 bg-white px-2 py-2 shadow-lg">
        {bankingTabs.map((tab) => {
          const isActive = tab.key === activeTab;
          return (
            <Pressable key={tab.key} accessibilityLabel={tab.label} accessibilityRole="tab" accessibilityState={{ selected: isActive }} onPress={() => onChange(tab.key)} className={`min-h-[56px] flex-1 items-center justify-center rounded-[20px] px-2 py-2 ${isActive ? "bg-[#0b57d0]" : "bg-transparent"}`}>
              <View className={`h-8 w-8 items-center justify-center rounded-full ${isActive ? "bg-white/15" : "bg-slate-100"}`}>
                <Text className={`text-sm font-semibold ${isActive ? "text-white" : "text-slate-600"}`}>{tab.icon}</Text>
              </View>
              <Text className={`mt-1 text-[12px] font-medium ${isActive ? "text-white" : "text-slate-500"}`}>{tab.label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

export function NavigationRail({ activeTab, onChange }: BankingNavigationProps) {
  return (
    <View className="w-[104px] border-r border-slate-200 bg-white px-3 py-4 shadow-sm">
      <Text className="text-[11px] font-medium tracking-[0.08em] text-slate-500">VoiceSecure</Text>
      <View className="mt-6">
        {bankingTabs.map((tab) => {
          const isActive = tab.key === activeTab;
          return (
            <Pressable key={tab.key} accessibilityLabel={tab.label} accessibilityRole="tab" accessibilityState={{ selected: isActive }} onPress={() => onChange(tab.key)} className="mb-3 min-h-[64px] items-center justify-center rounded-[20px] px-2 py-3" style={{ backgroundColor: isActive ? "#e8f0fe" : "transparent" }}>
              <View className="h-10 w-10 items-center justify-center rounded-2xl" style={{ backgroundColor: isActive ? "#0b57d0" : "#f1f5f9" }}>
                <Text className={`text-sm font-semibold ${isActive ? "text-white" : "text-slate-600"}`}>{tab.icon}</Text>
              </View>
              <Text className={`mt-2 text-[11px] font-medium ${isActive ? "text-slate-900" : "text-slate-500"}`}>{tab.label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}
