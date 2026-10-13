import type { Capability, NetworkProvider } from "./network";

export type ConnectionState =
  "idle" | "pairing" | "connecting" | "connected" | "disconnected" | "failed";
export type ReceiptState =
  "pending" | "acknowledged" | "unconfirmed" | "received";
export interface PeerMessage {
  id: string;
  text: string;
  author: "you" | "peer";
  status: ReceiptState;
}
export interface PeerSnapshot {
  state: ConnectionState;
  localCode: string;
  localRole: "offer" | "answer" | null;
  verificationCode: string;
  verified: boolean;
  peerVerified: boolean;
  error: string;
  messages: PeerMessage[];
}
const initial: PeerSnapshot = {
  state: "idle",
  localCode: "",
  localRole: null,
  verificationCode: "",
  verified: false,
  peerVerified: false,
  error: "",
  messages: [],
};
const MAX_CODE = 65536;
const MAX_MESSAGES = 500;

/** Manual, non-trickle signaling. Codes contain network metadata; never log them. */
export function parsePairingCode(
  code: string,
  expected: "offer" | "answer",
): RTCSessionDescriptionInit {
  if (!code.trim() || code.length > MAX_CODE)
    throw new Error("Enter a pairing code under 64 KB.");
  let value: unknown;
  try {
    value = JSON.parse(code);
  } catch {
    throw new Error("Invalid pairing code. Paste the complete JSON code.");
  }
  if (
    !value ||
    typeof value !== "object" ||
    !("version" in value) ||
    value.version !== 1 ||
    !("type" in value) ||
    value.type !== expected ||
    !("sdp" in value) ||
    typeof value.sdp !== "string"
  )
    throw new Error(`Expected a Wibrio ${expected} code.`);
  if (
    !/^m=application /m.test(value.sdp) ||
    value.sdp.match(/^m=/gm)?.length !== 1 ||
    value.sdp.match(/^a=fingerprint:/gm)?.length !== 1 ||
    !/^a=fingerprint:sha-256 ([\dA-F]{2}:){31}[\dA-F]{2}\r?$/im.test(value.sdp)
  )
    throw new Error(
      "Pairing code must describe a fingerprinted text-only data channel.",
    );
  return { type: expected, sdp: value.sdp };
}
function fingerprint(sdp: string) {
  const match = sdp.match(
    /^a=fingerprint:sha-256 ((?:[\dA-F]{2}:){31}[\dA-F]{2})\r?$/im,
  );
  if (sdp.match(/^a=fingerprint:/gm)?.length !== 1)
    throw new Error("Ambiguous certificate fingerprints are not supported.");
  if (!match) throw new Error("Missing peer certificate fingerprint.");
  return match[1].trim().toUpperCase();
}

/** One explicit, ephemeral peer session. No STUN/TURN, discovery, media, or automatic reconnect. */
export class WebRtcNetworkProvider implements NetworkProvider {
  readonly id = "webrtc";
  readonly label = "WebRTC";
  readonly simulated = false;
  private snapshot: PeerSnapshot = initial;
  private listeners = new Set<() => void>();
  private pc: RTCPeerConnection | null = null;
  private channel: RTCDataChannel | null = null;
  private deadline: ReturnType<typeof setTimeout> | null = null;
  private receipts = new Map<string, ReturnType<typeof setTimeout>>();
  private received = new Set<string>();
  private cancelGather: (() => void) | null = null;
  private outbound = new Set<string>();
  getSnapshot = () => this.snapshot;
  getServerSnapshot = () => initial;
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
  private update(patch: Partial<PeerSnapshot>) {
    this.snapshot = { ...this.snapshot, ...patch };
    this.listeners.forEach((listener) => listener());
  }
  capabilities(): Capability[] {
    return [
      {
        name: "WebRTC text channel",
        availability:
          typeof RTCPeerConnection !== "undefined" && globalThis.isSecureContext
            ? "available"
            : "unsupported",
        description:
          "Manual offer/answer pairing. Best on the same LAN; no signaling, STUN, or TURN server is configured.",
      },
      {
        name: "Wi-Fi Direct",
        availability: "native-required",
        description: "WebRTC cannot manage wireless networks.",
      },
      {
        name: "Voice, video & files",
        availability: "unsupported",
        description:
          "Only text messaging is implemented. Call and file screens remain local previews.",
      },
    ];
  }
  async start() {
    if (typeof RTCPeerConnection === "undefined" || !globalThis.isSecureContext)
      throw new Error(
        "WebRTC requires a supported browser on HTTPS or localhost.",
      );
  }
  async stop() {
    this.terminate("disconnected");
  }
  private terminate(state: ConnectionState, error = "") {
    const pc = this.pc;
    const channel = this.channel;
    this.pc = null;
    this.channel = null;
    this.cancelGather?.();
    this.cancelGather = null;
    if (this.deadline) clearTimeout(this.deadline);
    this.deadline = null;
    this.receipts.forEach((timer) => clearTimeout(timer));
    this.receipts.clear();
    channel?.close();
    pc?.close();
    this.update({
      state,
      error,
      verified: false,
      peerVerified: false,
      localCode: "",
      localRole: null,
      verificationCode: "",
      messages: this.snapshot.messages.map((message) =>
        message.status === "pending"
          ? { ...message, status: "unconfirmed" }
          : message,
      ),
    });
  }
  private async begin() {
    await this.start();
    this.terminate("idle");
    this.received.clear();
    this.outbound.clear();
    this.update({ ...initial, state: "pairing" });
    const pc = new RTCPeerConnection({
      iceServers: [],
      bundlePolicy: "max-bundle",
    });
    this.pc = pc;
    pc.onconnectionstatechange = () => {
      if (this.pc !== pc) return;
      if (["failed", "disconnected", "closed"].includes(pc.connectionState))
        this.terminate(
          pc.connectionState === "failed" ? "failed" : "disconnected",
          "Peer connection ended. Start a new pairing to retry; unconfirmed messages are not resent.",
        );
    };
    pc.ondatachannel = (event) => {
      if (this.pc !== pc) {
        event.channel.close();
        return;
      }
      if (this.channel || event.channel.label !== "wibrio-text-v1") {
        event.channel.close();
        return;
      }
      this.attach(event.channel, pc);
    };
    this.deadline = setTimeout(() => {
      if (this.pc === pc)
        this.terminate(
          "failed",
          "Pairing expired after five minutes. Generate a fresh code.",
        );
    }, 300000);
    return pc;
  }
  private attach(channel: RTCDataChannel, pc: RTCPeerConnection) {
    this.channel = channel;
    channel.onopen = () => {
      if (this.pc !== pc) return;
      if (this.deadline) clearTimeout(this.deadline);
      this.deadline = null;
      this.update({ state: "connected", error: "" });
    };
    channel.onclose = () => {
      if (this.pc === pc)
        this.terminate(
          "disconnected",
          "Data channel closed. Pair again to reconnect.",
        );
    };
    channel.onerror = () => {
      if (this.pc === pc)
        this.terminate("failed", "Data channel failed. Pair again to retry.");
    };
    channel.onmessage = (event) => {
      if (this.pc === pc) this.receive(event.data);
    };
  }
  private gather(pc: RTCPeerConnection): Promise<void> {
    return new Promise((resolve, reject) => {
      const finish = (error?: Error) => {
        clearTimeout(timer);
        pc.removeEventListener("icegatheringstatechange", check);
        if (this.cancelGather === cancel) this.cancelGather = null;
        if (error) reject(error);
        else resolve();
      };
      const check = () => {
        if (pc.iceGatheringState === "complete") finish();
      };
      const cancel = () => finish(new Error("Pairing was cancelled."));
      const timer = setTimeout(
        () =>
          finish(
            new Error(
              "ICE gathering timed out. Check browser network access and retry.",
            ),
          ),
        15000,
      );
      this.cancelGather = cancel;
      pc.addEventListener("icegatheringstatechange", check);
      check();
    });
  }
  private code(pc: RTCPeerConnection) {
    if (!pc.localDescription) throw new Error("No local pairing description.");
    return JSON.stringify({
      version: 1,
      type: pc.localDescription.type,
      sdp: pc.localDescription.sdp,
    });
  }
  private async verifyCode(pc: RTCPeerConnection) {
    if (!pc.localDescription || !pc.remoteDescription)
      throw new Error("Both pairing descriptions are required.");
    const fingerprints = [
      fingerprint(pc.localDescription.sdp),
      fingerprint(pc.remoteDescription.sdp),
    ]
      .sort()
      .join("|");
    const hash = new Uint8Array(
      await crypto.subtle.digest(
        "SHA-256",
        new TextEncoder().encode(fingerprints),
      ),
    );
    if (this.pc !== pc) return;
    this.update({
      verificationCode: Array.from(hash.slice(0, 12), (byte) =>
        byte.toString(16).padStart(2, "0"),
      )
        .join("")
        .toUpperCase()
        .match(/.{4}/g)!
        .join(" "),
    });
  }
  async createOffer() {
    let pc: RTCPeerConnection | undefined;
    try {
      pc = await this.begin();
      this.attach(
        pc.createDataChannel("wibrio-text-v1", { ordered: true }),
        pc,
      );
      await pc.setLocalDescription(await pc.createOffer());
      await this.gather(pc);
      if (this.pc === pc)
        this.update({ localCode: this.code(pc), localRole: "offer" });
    } catch (error) {
      if (!pc || this.pc === pc)
        this.terminate(
          "failed",
          error instanceof Error ? error.message : "Unable to create offer.",
        );
    }
  }
  async acceptOffer(raw: string) {
    // Validate before touching the existing session.
    const offer = parsePairingCode(raw, "offer");
    let pc: RTCPeerConnection | undefined;
    try {
      pc = await this.begin();
      await pc.setRemoteDescription(offer);
      await pc.setLocalDescription(await pc.createAnswer());
      await this.gather(pc);
      if (this.pc !== pc) return;
      this.update({
        localCode: this.code(pc),
        localRole: "answer",
        state: "connecting",
      });
      await this.verifyCode(pc);
      this.connectionDeadline(pc);
    } catch (error) {
      if (!pc || this.pc === pc)
        this.terminate(
          "failed",
          error instanceof Error ? error.message : "Unable to accept offer.",
        );
    }
  }
  async acceptAnswer(raw: string) {
    const answer = parsePairingCode(raw, "answer");
    const pc = this.pc;
    if (!pc || pc.signalingState !== "have-local-offer")
      throw new Error("Create a fresh offer before applying an answer.");
    try {
      await pc.setRemoteDescription(answer);
      if (this.pc !== pc) return;
      this.update({ state: "connecting" });
      await this.verifyCode(pc);
      this.connectionDeadline(pc);
    } catch (error) {
      if (this.pc === pc)
        this.terminate(
          "failed",
          error instanceof Error ? error.message : "Unable to apply answer.",
        );
    }
  }
  private connectionDeadline(pc: RTCPeerConnection) {
    if (this.deadline) clearTimeout(this.deadline);
    if (this.snapshot.state === "connected") {
      this.deadline = null;
      return;
    }
    this.deadline = setTimeout(() => {
      if (this.pc === pc)
        this.terminate(
          "failed",
          "Connection timed out. Host-only ICE may not cross your networks. Try the same LAN and fresh pairing codes.",
        );
    }, 45000);
  }
  confirmPeer() {
    if (
      this.snapshot.state !== "connected" ||
      !this.snapshot.verificationCode ||
      this.channel?.readyState !== "open"
    )
      throw new Error("Wait for the connection and verification code.");
    this.channel.send(
      JSON.stringify({
        kind: "verified",
        code: this.snapshot.verificationCode,
      }),
    );
    this.update({ verified: true });
  }
  sendMessage(text: string) {
    const channel = this.channel;
    const value = text.trim();
    if (
      this.snapshot.state !== "connected" ||
      !this.snapshot.verified ||
      !this.snapshot.peerVerified ||
      channel?.readyState !== "open"
    )
      throw new Error(
        "Both peers must confirm the matching verification code before sending.",
      );
    if (!value || value.length > 4000)
      throw new Error("Enter between 1 and 4,000 characters.");
    if (this.snapshot.messages.length >= MAX_MESSAGES)
      throw new Error(
        "This session reached its 500-message limit. Start a new pairing.",
      );
    if (channel.bufferedAmount > 65536)
      throw new Error(
        "The connection is busy. Wait before sending another message.",
      );
    const id = crypto.randomUUID();
    channel.send(JSON.stringify({ kind: "text", id, text: value }));
    this.outbound.add(id);
    this.update({
      messages: [
        ...this.snapshot.messages,
        { id, text: value, author: "you", status: "pending" },
      ],
    });
    this.receipts.set(
      id,
      setTimeout(() => {
        this.receipts.delete(id);
        this.update({
          messages: this.snapshot.messages.map((message) =>
            message.id === id && message.status === "pending"
              ? { ...message, status: "unconfirmed" }
              : message,
          ),
        });
      }, 10000),
    );
  }
  private receive(raw: unknown) {
    if (typeof raw !== "string" || raw.length > 32768) return;
    let packet: unknown;
    try {
      packet = JSON.parse(raw);
    } catch {
      return;
    }
    if (!packet || typeof packet !== "object" || !("kind" in packet)) return;
    if (
      packet.kind === "verified" &&
      "code" in packet &&
      packet.code === this.snapshot.verificationCode &&
      this.snapshot.verificationCode
    ) {
      this.update({ peerVerified: true });
      return;
    }
    if (!this.snapshot.verified || !this.snapshot.peerVerified) return;
    if (
      !("id" in packet) ||
      typeof packet.id !== "string" ||
      !/^[\da-f-]{36}$/i.test(packet.id)
    )
      return;
    if (packet.kind === "ack" && this.outbound.has(packet.id)) {
      const id = packet.id;
      const timer = this.receipts.get(id);
      if (timer) clearTimeout(timer);
      this.receipts.delete(id);
      this.update({
        messages: this.snapshot.messages.map((message) =>
          message.id === id && message.author === "you"
            ? { ...message, status: "acknowledged" }
            : message,
        ),
      });
    } else if (
      packet.kind === "text" &&
      "text" in packet &&
      typeof packet.text === "string" &&
      packet.text.trim() &&
      packet.text.length <= 4000
    ) {
      if (!this.received.has(packet.id)) {
        if (this.snapshot.messages.length >= MAX_MESSAGES) {
          this.terminate(
            "failed",
            "Session message limit reached. Pair again to continue.",
          );
          return;
        }
        this.received.add(packet.id);
        this.update({
          messages: [
            ...this.snapshot.messages,
            {
              id: packet.id,
              text: packet.text,
              author: "peer",
              status: "received",
            },
          ],
        });
      }
      // Acknowledges receipt in the receiving app's memory, never reading or durable storage.
      if (this.channel?.readyState === "open") {
        try {
          this.channel.send(JSON.stringify({ kind: "ack", id: packet.id }));
        } catch {
          this.terminate(
            "failed",
            "Unable to acknowledge receipt. Pair again.",
          );
        }
      }
    }
  }
}
export const webRtcProvider = new WebRtcNetworkProvider();
