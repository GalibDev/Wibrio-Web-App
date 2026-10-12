"use client";
import { useState } from "react";
import { Trash2, CheckCircle2, Globe, Lock } from "lucide-react";
import { Button } from "../ui/button";
import { Card, PageHeading, SectionTitle } from "../shared";
import { usePreferences } from "@/lib/store";
import { db } from "@/lib/db";
export function SettingsScreen() {
  const { theme, setTheme, notifications, setNotifications } = usePreferences();
  const [confirm, setConfirm] = useState(false);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  async function clear() {
    setBusy(true);
    try {
      await db.messages.clear();
      setNotice("Local demo chat history cleared.");
      setConfirm(false);
    } catch {
      setNotice("Unable to clear local storage. Try again.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeading
        title="Make this space your own."
        description="A few thoughtful preferences for the way you connect."
      />
      <div className="settings-layout">
        <Card>
          <SectionTitle title="Look & feel" />
          <p className="section-subtitle">A little light, or a little less.</p>
          <fieldset className="theme-options">
            <legend className="sr-only">Color theme</legend>
            {(["light", "dark"] as const).map((t) => (
              <label
                key={t}
                className={`theme-option ${theme === t ? "chosen" : ""}`}
              >
                <input
                  type="radio"
                  name="theme"
                  checked={theme === t}
                  onChange={() => setTheme(t)}
                />
                <span className={`theme-swatch ${t}`}>
                  <i />
                  <i />
                  <i />
                </span>
                <strong>{t === "light" ? "Light" : "Dark"} theme</strong>
                {theme === t && <CheckCircle2 size={17} />}
              </label>
            ))}
          </fieldset>
          <div className="setting-row">
            <div>
              <strong>In-app demo notifications</strong>
              <p>Save your preference for future integrations.</p>
            </div>
            <button
              className={`switch ${notifications ? "on" : ""}`}
              role="switch"
              aria-checked={notifications}
              aria-label="In-app demo notifications"
              onClick={() => setNotifications(!notifications)}
            >
              <span />
            </button>
          </div>
        </Card>
        <Card>
          <SectionTitle title="Privacy & local storage" />
          <div className="setting-row">
            <span className="icon-tile mint">
              <Lock size={20} />
            </span>
            <div>
              <strong>Stored on your device</strong>
              <p>
                Demo messages use IndexedDB. No account or server is involved.
                Local storage is not encrypted.
              </p>
            </div>
          </div>
          <div className="setting-row">
            <span className="icon-tile blue">
              <Globe size={20} />
            </span>
            <div>
              <strong>Offline availability</strong>
              <p>
                In production, previously visited pages and their assets are
                cached. Your first visit requires a connection.
              </p>
            </div>
          </div>
          <Button variant="destructive" onClick={() => setConfirm(true)}>
            <Trash2 size={16} />
            Clear demo chat history
          </Button>
          {confirm && (
            <div className="confirm-box">
              <strong>Delete all local demo messages?</strong>
              <p>This cannot be undone. Your profile preferences will stay.</p>
              <div>
                <Button variant="destructive" disabled={busy} onClick={clear}>
                  {busy ? "Clearing…" : "Delete messages"}
                </Button>
                <Button variant="secondary" onClick={() => setConfirm(false)}>
                  Cancel
                </Button>
              </div>
            </div>
          )}
          {notice && <p role="status">{notice}</p>}
        </Card>
        <Card className="settings-provider">
          <SectionTitle
            title="Connection capabilities"
            href="/start"
            link="Manage"
          />
          <p>
            Demo mode is the available provider in Phase 1. Wi-Fi Direct and
            Bluetooth mesh require a native companion. WebRTC transport is not
            integrated.
          </p>
        </Card>
      </div>
    </>
  );
}
