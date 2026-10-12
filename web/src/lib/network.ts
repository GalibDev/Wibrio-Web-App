export type Availability = "available" | "unsupported" | "native-required";
export type ProviderId = "demo" | "webrtc" | "native";
export interface Capability {
  name: string;
  availability: Availability;
  description: string;
}
export interface NetworkProvider {
  readonly id: ProviderId;
  readonly label: string;
  readonly simulated: boolean;
  capabilities(): Capability[];
  start(): Promise<void>;
  stop(): Promise<void>;
}
export class DemoNetworkProvider implements NetworkProvider {
  readonly id = "demo";
  readonly label = "Demo network";
  readonly simulated = true;
  capabilities(): Capability[] {
    return [
      {
        name: "Local demo",
        availability: "available",
        description: "Simulated peers and messages stored on this device.",
      },
      {
        name: "Wi-Fi Direct",
        availability: "native-required",
        description: "Requires a compatible native companion.",
      },
      {
        name: "Bluetooth mesh",
        availability: "native-required",
        description: "Browser Bluetooth does not provide mesh routing.",
      },
    ];
  }
  async start() {}
  async stop() {}
}
export class WebRtcNetworkProvider implements NetworkProvider {
  readonly id = "webrtc";
  readonly label = "WebRTC";
  readonly simulated = false;
  capabilities(): Capability[] {
    return [
      {
        name: "WebRTC transport",
        availability: "unsupported",
        description:
          "Integration pending. Requires signaling and explicit peer pairing.",
      },
      {
        name: "Wi-Fi Direct",
        availability: "native-required",
        description: "Not accessible through WebRTC.",
      },
    ];
  }
  async start(): Promise<void> {
    throw new Error(
      "WebRTC provider is not implemented in Phase 1. Choose Demo network.",
    );
  }
  async stop() {}
}
export class NativeCompanionProvider implements NetworkProvider {
  readonly id = "native";
  readonly label = "Native companion";
  readonly simulated = false;
  capabilities(): Capability[] {
    return [
      {
        name: "Companion bridge",
        availability: "native-required",
        description:
          "No native companion bridge is connected. Integration is planned.",
      },
    ];
  }
  async start(): Promise<void> {
    throw new Error("No native companion is connected. Choose Demo network.");
  }
  async stop() {}
}
export const providers: Record<ProviderId, NetworkProvider> = {
  demo: new DemoNetworkProvider(),
  webrtc: new WebRtcNetworkProvider(),
  native: new NativeCompanionProvider(),
};
