import { describe, expect, it } from "vitest";
import { providers } from "../src/lib/network";
describe("honest provider capabilities", () => {
  it("supports demo and rejects unavailable real transports in Node", async () => {
    await expect(providers.demo.start()).resolves.toBeUndefined();
    expect(providers.demo.simulated).toBe(true);
    await expect(providers.webrtc.start()).rejects.toThrow(
      "HTTPS or localhost",
    );
    await expect(providers.native.start()).rejects.toThrow(
      "No native companion",
    );
  });
  it("never advertises browser mesh controls as available", () => {
    for (const provider of Object.values(providers)) {
      for (const capability of provider.capabilities()) {
        if (/Wi-Fi|Bluetooth/.test(capability.name))
          expect(capability.availability).toBe("native-required");
      }
    }
  });
});
