"use client";
import { useEffect, useState } from "react";
import { ShieldCheck, Check, Monitor, Copy } from "lucide-react";
import { Button } from "../ui/button";
import { Avatar, Badge, Card, PageHeading, SectionTitle } from "../shared";
import { usePreferences } from "@/lib/store";
export function Profile() {
  const { name, setName } = usePreferences();
  const [draft, setDraft] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const [deviceId, setDeviceId] = useState("");
  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => {
      try {
        let id = localStorage.getItem("wibrio-device-id");
        if (!id) {
          id = crypto.randomUUID();
          localStorage.setItem("wibrio-device-id", id);
        }
        if (!cancelled) setDeviceId(id);
      } catch {
        if (!cancelled)
          setNotice("Device identity storage is unavailable in this browser.");
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);
  return (
    <>
      <PageHeading
        title="There’s only one you."
        description="Your local identity, wherever the conversation takes you."
      />
      <div className="profile-layout">
        <Card className="profile-summary">
          <Avatar
            initials={name.slice(0, 2).toUpperCase()}
            color="mint"
            large
          />
          <h2>{name}</h2>
          <Badge>Local demo identity</Badge>
          <p>No account. Just a space that’s yours.</p>
          <span className="profile-device">
            <Monitor size={18} />
            This browser
          </span>
        </Card>
        <Card>
          <SectionTitle title="Profile details" />
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const value = (draft ?? name).trim();
              if (!value) {
                setNotice("Please enter a display name.");
                return;
              }
              setName(value);
              setNotice("Your profile has been saved on this device.");
            }}
          >
            <label className="form-field">
              Display name
              <input
                required
                maxLength={40}
                autoComplete="nickname"
                value={draft ?? name}
                onChange={(e) => setDraft(e.target.value)}
              />
              <small>Visible only in this local demonstration.</small>
            </label>
            <Button type="submit">
              Save changes
              <Check size={17} />
            </Button>
          </form>
          <div className="identity-block">
            <span className="icon-tile blue">
              <ShieldCheck size={22} />
            </span>
            <h3>Device identity</h3>
            <p>
              A locally generated demo identifier. It is not a cryptographic
              identity or verified fingerprint.
            </p>
            <code>{deviceId || "Loading local identifier…"}</code>
            <Button
              variant="secondary"
              disabled={!deviceId}
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(deviceId);
                  setNotice("Demo device identifier copied.");
                } catch {
                  setNotice(
                    "Clipboard unavailable. Select the identifier to copy it.",
                  );
                }
              }}
            >
              <Copy size={15} />
              Copy identifier
            </Button>
          </div>
          {notice && (
            <p role="status" className="success-message">
              {notice}
            </p>
          )}
        </Card>
      </div>
    </>
  );
}
