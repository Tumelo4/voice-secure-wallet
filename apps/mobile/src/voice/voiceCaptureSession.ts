import { ApiClientError, type VoiceChallengeResult, type VoiceSecureApiClient } from "../api/voiceSecureApiClient.ts";

export interface CapturedVoiceAudio {
  contentBase64: string;
  codec: string;
  sampleRateHz: number;
}

export interface VoiceRecorder {
  requestPermission(): Promise<boolean>;
  start(): Promise<void>;
  stop(): Promise<CapturedVoiceAudio>;
  cancel(): Promise<void>;
}

export type VoiceCaptureState =
  | { status: "idle" }
  | { status: "requesting-permission" }
  | { status: "recording"; phrase: string; expiresAt: string }
  | { status: "uploading"; phrase: string }
  | { status: "retryable"; phrase: string; message: string; retryAfter?: string }
  | { status: "completed"; verificationStatus: string }
  | { status: "cancelled" }
  | { status: "failed"; message: string };

export class VoiceCaptureSession {
  state: VoiceCaptureState = { status: "idle" };
  private captured?: CapturedVoiceAudio;
  private readonly api: VoiceSecureApiClient;
  private readonly recorder: VoiceRecorder;
  private readonly challenge: VoiceChallengeResult;
  private readonly now: () => Date;

  constructor(
    api: VoiceSecureApiClient,
    recorder: VoiceRecorder,
    challenge: VoiceChallengeResult,
    now: () => Date = () => new Date(),
  ) {
    this.api = api;
    this.recorder = recorder;
    this.challenge = challenge;
    this.now = now;
  }

  async begin(): Promise<void> {
    this.state = { status: "requesting-permission" };
    if (!(await this.recorder.requestPermission())) {
      this.state = { status: "failed", message: "Microphone permission is required for voice authorisation." };
      return;
    }
    if (this.expired()) {
      this.state = { status: "failed", message: "The voice challenge expired. Request a new challenge." };
      return;
    }
    await this.recorder.start();
    this.state = { status: "recording", phrase: this.challenge.phrase, expiresAt: this.challenge.expiresAt };
  }

  async submit(): Promise<void> {
    if (this.state.status === "recording") this.captured = await this.recorder.stop();
    if (!this.captured) throw new Error("voice recording is not available");
    if (this.expired()) {
      this.captured = undefined;
      this.state = { status: "failed", message: "The voice challenge expired. Record against a new challenge." };
      return;
    }
    this.state = { status: "uploading", phrase: this.challenge.phrase };
    try {
      const result = await this.api.verifyVoice({
        challenge: this.challenge,
        capturedAt: this.now().toISOString(),
        audio: this.captured,
      });
      this.captured = undefined;
      this.state = { status: "completed", verificationStatus: result.status };
    } catch (error) {
      if (error instanceof ApiClientError && (error.status >= 500 || error.status === 429)) {
        this.state = { status: "retryable", phrase: this.challenge.phrase, message: error.message, retryAfter: error.retryAfter };
        return;
      }
      this.captured = undefined;
      this.state = { status: "failed", message: error instanceof Error ? error.message : "Voice verification failed." };
    }
  }

  async cancel(): Promise<void> {
    await this.recorder.cancel();
    this.captured = undefined;
    this.state = { status: "cancelled" };
  }

  private expired(): boolean {
    return this.now().getTime() >= Date.parse(this.challenge.expiresAt);
  }
}
