import { RecordingPresets, requestRecordingPermissionsAsync, setAudioModeAsync, useAudioRecorder } from "expo-audio";
import * as FileSystem from "expo-file-system/legacy";
import { useMemo } from "react";
import type { VoiceRecorder } from "./voiceCaptureSession.ts";

export function useExpoVoiceRecorder(): VoiceRecorder {
  const recorder = useAudioRecorder({ ...RecordingPresets.HIGH_QUALITY, directory: "cache" });
  return useMemo(() => ({
    async requestPermission() {
      return (await requestRecordingPermissionsAsync()).granted;
    },
    async start() {
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
      await recorder.prepareToRecordAsync();
      recorder.record();
    },
    async stop() {
      await recorder.stop();
      const uri = recorder.uri;
      if (!uri) throw new Error("Voice recording did not produce an audio file.");
      try {
        return {
          contentBase64: await FileSystem.readAsStringAsync(uri, { encoding: FileSystem.EncodingType.Base64 }),
          codec: uri.endsWith(".webm") ? "audio/webm" : "audio/mp4",
          sampleRateHz: RecordingPresets.HIGH_QUALITY.sampleRate,
        };
      } finally {
        await FileSystem.deleteAsync(uri, { idempotent: true });
        await setAudioModeAsync({ allowsRecording: false });
      }
    },
    async cancel() {
      const status = recorder.getStatus();
      if (status.isRecording) await recorder.stop();
      if (recorder.uri) await FileSystem.deleteAsync(recorder.uri, { idempotent: true });
      await setAudioModeAsync({ allowsRecording: false });
    },
  }), [recorder]);
}
