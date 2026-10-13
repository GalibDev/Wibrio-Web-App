import { describe, expect, it } from "vitest";
import { parsePairingCode, WebRtcNetworkProvider } from "../src/lib/webrtc";
const sdp = `v=0\r\nm=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\na=fingerprint:sha-256 ${Array(32).fill("AB").join(":")}\r\n`;
describe("manual pairing boundary", () => {
  it("accepts versioned, fingerprinted data channel descriptions", () => {
    expect(
      parsePairingCode(
        JSON.stringify({ version: 1, type: "offer", sdp }),
        "offer",
      ),
    ).toEqual({ type: "offer", sdp });
  });
  it("rejects malformed, mismatched, oversized, and media descriptions", () => {
    for (const code of [
      "bad json",
      "{}",
      JSON.stringify({ version: 1, type: "answer", sdp }),
      JSON.stringify({
        version: 1,
        type: "offer",
        sdp: sdp + "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n",
      }),
      "x".repeat(65537),
      JSON.stringify({ version: 1, type: "offer", sdp: "m=application 9" }),
      JSON.stringify({
        version: 1,
        type: "offer",
        sdp:
          sdp + `a=fingerprint:sha-256 ${Array(32).fill("CD").join(":")}\r\n`,
      }),
      JSON.stringify({
        version: 1,
        type: "offer",
        sdp: sdp + "m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n",
      }),
    ])
      expect(() => parsePairingCode(code, "offer")).toThrow();
  });
  it("blocks sends and verification outside an open verified session", () => {
    const provider = new WebRtcNetworkProvider();
    expect(provider.getSnapshot().state).toBe("idle");
    expect(() => provider.sendMessage("hello")).toThrow("Both peers");
    expect(() => provider.confirmPeer()).toThrow("Wait for");
  });
});
