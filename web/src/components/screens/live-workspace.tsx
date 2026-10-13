"use client";
import Link from "next/link";
import { useSyncExternalStore } from "react";
import {
  Link2,
  Users,
  MessageCircle,
  ShieldCheck,
  ArrowRight,
  Network,
  Unplug,
} from "lucide-react";
import { webRtcProvider } from "@/lib/webrtc";
import { usePreferences } from "@/lib/store";
import { Card, Badge, PageHeading, SectionTitle } from "../shared";
import { Button } from "../ui/button";

export function LiveWorkspace({ screen }: { screen: string }) {
  const session = useSyncExternalStore(
    webRtcProvider.subscribe,
    webRtcProvider.getSnapshot,
    webRtcProvider.getServerSnapshot,
  );
  const name = usePreferences((s) => s.name);
  const connected = session.state === "connected";
  const verified = connected && session.verified && session.peerVerified;
  const received = session.messages.filter((m) => m.author === "peer").length;
  const acknowledged = session.messages.filter(
    (m) => m.author === "you" && m.status === "acknowledged",
  ).length;
  const titles: Record<string, [string, string]> = {
    dashboard: [
      `Your live workspace, ${name.split(" ")[0]}.`,
      "Actual browser session activity. Pair another browser to get started.",
    ],
    nearby: [
      "Your connected people.",
      "Browsers appear here only when a real WebRTC data channel is open. Wireless discovery is not supported.",
    ],
    conversations: [
      "Your real conversations.",
      "Text from your current peer session. No sample contacts or seeded messages.",
    ],
    network: [
      "Your live connection.",
      "Actual WebRTC session state. This is a direct browser link, not a mesh route.",
    ],
    groups: [
      "Group conversations",
      "Real group transport is not implemented yet.",
    ],
    files: [
      "File sharing",
      "Real peer file transfers are not implemented yet.",
    ],
    voice: ["Voice calls", "Real voice transport is not implemented yet."],
    video: ["Video calls", "Real video transport is not implemented yet."],
  };
  const [title, description] = titles[screen];
  const pairLink = (
    <Button asChild>
      <Link href="/connect">
        <Link2 size={17} />
        {connected ? "Open live chat" : "Pair a browser"}
        <ArrowRight size={16} />
      </Link>
    </Button>
  );
  return (
    <>
      <PageHeading
        eyebrow="LIVE WORKSPACE"
        title={title}
        description={description}
        action={pairLink}
      />
      {screen === "dashboard" && (
        <>
          <section className="hero-card live-hero">
            <Badge tone="hero-badge">
              LIVE WEBRTC · {connected ? "CHANNEL OPEN" : "NO PEER CONNECTED"}
            </Badge>
            <h2>
              {connected
                ? "A real connection.\nA conversation that’s yours."
                : "Your next connection\nstarts with a real person."}
            </h2>
            <p>
              {connected
                ? "Your paired browser is connected through a real data channel."
                : "Exchange pairing codes with another browser. Compare the verification code together, then start chatting."}
            </p>
            <div className="live-hero-action">{pairLink}</div>
          </section>
          <div className="stats-grid">
            {[
              {
                icon: Users,
                label: "Connected peers",
                value: connected ? "1" : "0",
              },
              {
                icon: MessageCircle,
                label: "Received messages",
                value: String(received),
              },
              {
                icon: ShieldCheck,
                label: "Peer acknowledgements",
                value: String(acknowledged),
              },
              {
                icon: Network,
                label: "Connection state",
                value: session.state,
              },
            ].map((stat) => (
              <Card className="stat-card" key={stat.label}>
                <span className="icon-tile blue">
                  <stat.icon size={20} />
                </span>
                <p>{stat.label}</p>
                <strong
                  className="stat-value"
                  data-testid={`live-${stat.label.toLowerCase().replaceAll(" ", "-")}`}
                >
                  {stat.value}
                </strong>
              </Card>
            ))}
          </div>
        </>
      )}
      {["dashboard", "nearby", "conversations", "network"].includes(screen) && (
        <Card className="live-session-card">
          <SectionTitle
            title={
              screen === "network"
                ? "Direct connection details"
                : screen === "conversations"
                  ? "Peer session conversation"
                  : "Connected browser"
            }
          />
          {connected ? (
            <div className="live-peer-row">
              <span className="icon-tile mint">
                <Link2 size={24} />
              </span>
              <div>
                <h2>Paired browser</h2>
                <p>
                  {verified
                    ? "Both peers confirmed the session verification code."
                    : "Channel open · Waiting for both peers to confirm the verification code."}
                </p>
                <Badge tone={verified ? "mint" : ""}>
                  {verified ? "Session verified" : "Verification pending"}
                </Badge>
              </div>
              <Button asChild variant="secondary">
                <Link href="/connect">
                  {verified ? "Open conversation" : "Verify peer"}
                  <ArrowRight size={15} />
                </Link>
              </Button>
            </div>
          ) : (
            <div className="empty-state">
              <Unplug size={30} />
              <h3>
                {session.messages.length
                  ? "Peer disconnected. Session transcript remains in memory."
                  : "No real peer connected yet."}
              </h3>
              <p>
                Use manual pairing to connect a browser. No nearby devices,
                connection quality, or mesh routes are invented.
              </p>
              {pairLink}
            </div>
          )}
          {screen === "network" && (
            <div className="live-network-details">
              {connected && (
                <div
                  className="live-direct-link"
                  role="img"
                  aria-label="One actual WebRTC data channel between this browser and the paired browser"
                >
                  <span>
                    <Network size={24} />
                    This browser
                  </span>
                  <i />
                  <Link2 size={20} />
                  <i />
                  <span>
                    <Users size={24} />
                    Paired browser
                  </span>
                </div>
              )}
              {[
                ["Transport", "WebRTC data channel"],
                ["State", session.state],
                ["Connected peers", connected ? "1" : "0"],
                [
                  "Verification",
                  verified ? "Confirmed on both browsers" : "Not confirmed",
                ],
                ["Mesh routing", "Not supported"],
                ["ICE servers", "None configured"],
              ].map(([label, value]) => (
                <div className="session-line" key={label}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                </div>
              ))}
              <p className="section-subtitle">
                No distance, signal strength, hop count, or route quality is
                measured.
              </p>
            </div>
          )}
          {screen === "conversations" && session.messages.length > 0 && (
            <div
              className="live-transcript"
              role="log"
              aria-label="Session transcript"
            >
              {session.messages.map((message) => (
                <div
                  className={`message ${message.author === "you" ? "sent" : "received"}`}
                  key={message.id}
                >
                  <p>{message.text}</p>
                  <small>
                    {message.author === "you" ? "You" : "Paired peer"} ·{" "}
                    {message.status === "acknowledged"
                      ? "Acknowledged by peer app · Not a read receipt"
                      : message.status === "pending"
                        ? "Awaiting acknowledgement"
                        : message.status === "unconfirmed"
                          ? "Receipt unconfirmed"
                          : "Received in this browser"}
                  </small>
                </div>
              ))}
            </div>
          )}
        </Card>
      )}
      {["groups", "files", "voice", "video"].includes(screen) && (
        <Card>
          <div className="empty-state">
            <ShieldCheck size={30} />
            <Badge>Unsupported operation</Badge>
            <h2>{title} is not available on the live provider yet.</h2>
            <p>
              Real one-to-one text chat is available. Interface previews can be
              explored by explicitly switching to Demo mode.
            </p>
            {pairLink}
          </div>
        </Card>
      )}
      {session.error &&
        ["dashboard", "nearby", "conversations", "network"].includes(
          screen,
        ) && (
          <p role="status" className="error-message">
            {session.error}
          </p>
        )}
      <p className="simulation-note">
        Live text stays in session memory. Reloading ends the connection and
        clears its transcript. New pairing starts a new session.
      </p>
    </>
  );
}
