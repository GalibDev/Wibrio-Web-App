import { describe, expect, it } from "vitest";
import { providers } from "../src/lib/network";
describe("honest provider capabilities", () => {
  it("allows only a simulated provider to start", async () => {
    await expect(providers.demo.start()).resolves.toBeUndefined();
    expect(providers.demo.simulated).toBe(true);
    await expect(providers.webrtc.start()).rejects.toThrow("not implemented");
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
