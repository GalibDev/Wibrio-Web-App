"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, Radio, ShieldCheck, Wifi, Bluetooth } from "lucide-react";
import { Button } from "../ui/button";
import {
  Badge,
  Card,
  PageHeading,
  SectionTitle,
  SimulationNote,
} from "../shared";
import { providers, type ProviderId } from "@/lib/network";
import { usePreferences, useSession } from "@/lib/store";
export function Setup() {
  const router = useRouter();
  const { provider, setProvider } = usePreferences();
  const { active, setActive } = useSession();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function start() {
    setError("");
    setBusy(true);
    try {
      await providers[provider].start();
      if (provider === "webrtc") {
        router.push("/connect");
        return;
      }
      setActive(true);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Unable to start this provider.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeading
        title="Your next connection starts here."
        description="Choose how you want to explore your Wibrio space."
      />
      <div className="setup-grid">
        <Card className="setup-card">
          <span className="big-icon">
            <Radio size={35} />
          </span>
          <h2>Let’s bring your world closer.</h2>
          <p>
            Choose local demo mode or pair two browsers for real WebRTC text
            chat. No wireless discovery or mesh routing is performed.
          </p>
          <fieldset>
            <legend>Connection provider</legend>
            {(Object.keys(providers) as ProviderId[]).map((id) => (
              <label
                key={id}
                className={`provider-option ${provider === id ? "chosen" : ""}`}
              >
                <input
                  type="radio"
                  name="provider"
                  value={id}
                  checked={provider === id}
                  disabled={active}
                  onChange={() => {
                    setProvider(id);
                    setError("");
                  }}
                />
                <div>
                  <strong>{providers[id].label}</strong>
                  <small>
                    {id === "demo"
                      ? "Available · Local simulation"
                      : id === "native"
                        ? "Requires native companion"
                        : "Manual pairing · Real text channel"}
                  </small>
                </div>
                {id === "demo" && <Badge>Phase 1</Badge>}
              </label>
            ))}
          </fieldset>
          {error && (
            <p className="error-message" role="alert">
              {error}
            </p>
          )}
          <Button
            disabled={busy}
            onClick={
              active
                ? async () => {
                    await providers[provider].stop();
                    setActive(false);
                  }
                : start
            }
          >
            {busy
              ? "Starting…"
              : active
                ? "Stop demo session"
                : "Start selected provider"}
            <ArrowRight size={17} />
          </Button>
          {active && (
            <p className="success-message" role="status">
              Demo session active. No real peers are connected.
            </p>
          )}
        </Card>
        <div>
          <Card>
            <SectionTitle title="What’s available on this device" />
            <div className="capability-list">
              {providers[provider].capabilities().map((c) => (
                <div key={c.name}>
                  <span className="icon-tile blue">
                    {c.name.includes("Bluetooth") ? (
                      <Bluetooth size={20} />
                    ) : (
                      <Wifi size={20} />
                    )}
                  </span>
                  <div>
                    <strong>{c.name}</strong>
                    <Badge tone={c.availability === "available" ? "mint" : ""}>
                      {c.availability === "available"
                        ? "Available"
                        : c.availability === "native-required"
                          ? "Native companion required"
                          : "Unsupported"}
                    </Badge>
                    <p>{c.description}</p>
                  </div>
                </div>
              ))}
            </div>
          </Card>
          <div className="info-callout">
            <ShieldCheck size={23} />
            <div>
              <strong>Built with honest boundaries.</strong>
              <p>
                Browser APIs cannot manage Wi-Fi Direct or Bluetooth mesh
                routing. Future providers will report their own capabilities.
              </p>
            </div>
          </div>
        </div>
      </div>
      {provider === "demo" && <SimulationNote />}
    </>
  );
}
