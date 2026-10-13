"use client";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import {
  Copy,
  Link2,
  ShieldCheck,
  Send,
  Unplug,
  ArrowRight,
} from "lucide-react";
import { webRtcProvider } from "@/lib/webrtc";
import { Badge, Card, PageHeading, SectionTitle } from "../shared";
import { Button } from "../ui/button";

const receiptLabels = {
  pending: "Awaiting peer acknowledgement",
  acknowledged: "Acknowledged by peer app · Not a read receipt",
  unconfirmed: "Receipt unconfirmed · Not automatically resent",
  received: "Received in this browser",
};
export function PeerChat() {
  const session = useSyncExternalStore(
    webRtcProvider.subscribe,
    webRtcProvider.getSnapshot,
    webRtcProvider.getServerSnapshot,
  );
  const [mode, setMode] = useState<"offer" | "answer">("offer");
  const [incoming, setIncoming] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [text, setText] = useState("");
  const [supported, setSupported] = useState<boolean | null>(null);
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => {
    void webRtcProvider
      .start()
      .then(() => setSupported(true))
      .catch(() => setSupported(false));
  }, []);
  useEffect(() => {
    const list = end.current?.parentElement;
    if (list) list.scrollTo({ top: list.scrollHeight });
  }, [session.messages]);
  const canSend =
    session.state === "connected" && session.verified && session.peerVerified;
  const running = ["pairing", "connecting", "connected"].includes(
    session.state,
  );
  async function act(operation: () => void | Promise<void>) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await operation();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Unable to complete this operation.",
      );
    } finally {
      setBusy(false);
    }
  }
  async function copy() {
    try {
      await navigator.clipboard.writeText(session.localCode);
      setNotice("Pairing code copied. Share privately with the intended peer.");
    } catch {
      setNotice(
        "Clipboard unavailable. Select and copy the complete code manually.",
      );
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="REAL PEER CONNECTION"
        title="Two browsers. One conversation."
        description="Manually paired WebRTC text chat. No mesh discovery, signaling server, STUN, or TURN."
        action={
          <Badge tone="mint">
            <Link2 size={14} />
            Real transport · Not simulated
          </Badge>
        }
      />
      <div className="info-callout peer-intro">
        <ShieldCheck size={22} />
        <div>
          <strong>Pair privately, then compare.</strong>
          <p>
            Open this page on both devices using HTTPS or localhost. Exchange
            the offer and answer through a trusted channel, then compare the
            verification code by voice or in person. Best on the same LAN; other
            networks may fail. Pairing codes include network metadata. Messages
            remain in memory and disappear on reload or a new pairing;
            navigating within the app keeps the session. Demo chats stay
            separate.
          </p>
        </div>
      </div>
      {supported === false && (
        <p role="alert" className="error-message">
          WebRTC is unavailable. Use a supported browser on HTTPS or localhost.
          Native mesh is not available in the browser.
        </p>
      )}
      <div className="peer-layout">
        <Card className="peer-pairing">
          <SectionTitle title="1. Pair your browsers" />
          <div className="tabs">
            <button
              disabled={running || busy}
              className={mode === "offer" ? "active" : ""}
              aria-pressed={mode === "offer"}
              onClick={() => {
                setMode("offer");
                setIncoming("");
              }}
            >
              Create invitation
            </button>
            <button
              disabled={running || busy}
              className={mode === "answer" ? "active" : ""}
              aria-pressed={mode === "answer"}
              onClick={() => {
                setMode("answer");
                setIncoming("");
              }}
            >
              Join invitation
            </button>
          </div>
          {mode === "offer" && (
            <>
              <p className="peer-step">
                Create an offer and send its complete code to your peer.
              </p>
              <Button
                disabled={busy || running || supported !== true}
                onClick={() => {
                  setIncoming("");
                  void act(() => webRtcProvider.createOffer());
                }}
              >
                {busy ? "Preparing…" : "Create offer"}
                <ArrowRight size={16} />
              </Button>
            </>
          )}
          {mode === "answer" && (
            <p className="peer-step">
              Paste your peer’s offer, generate an answer, then send the answer
              code back.
            </p>
          )}
          <label className="form-field">
            {mode === "offer" ? "Peer answer code" : "Peer offer code"}
            <textarea
              aria-label={
                mode === "offer" ? "Peer answer code" : "Peer offer code"
              }
              rows={4}
              maxLength={65536}
              value={incoming}
              disabled={
                busy ||
                session.state === "connected" ||
                session.state === "connecting"
              }
              onChange={(e) => setIncoming(e.target.value)}
              placeholder="Paste the complete Wibrio pairing JSON…"
              spellCheck={false}
            />
          </label>
          <Button
            variant="secondary"
            disabled={
              busy ||
              supported !== true ||
              !incoming.trim() ||
              session.state === "connected" ||
              session.state === "connecting" ||
              (mode === "offer" && !session.localCode)
            }
            onClick={() =>
              void act(() =>
                mode === "offer"
                  ? webRtcProvider.acceptAnswer(incoming)
                  : webRtcProvider.acceptOffer(incoming),
              )
            }
          >
            {mode === "offer" ? "Apply answer" : "Accept offer & create answer"}
          </Button>
          {session.localCode && (
            <div className="local-pairing-code">
              <label className="form-field">
                Your {session.localRole} code
                <textarea
                  aria-label="Your pairing code"
                  rows={4}
                  readOnly
                  value={session.localCode}
                  spellCheck={false}
                />
              </label>
              <Button variant="secondary" onClick={copy}>
                <Copy size={15} />
                Copy pairing code
              </Button>
            </div>
          )}
          <div className="peer-state" role="status">
            <strong>
              Connection:{" "}
              <span data-testid="connection-state">{session.state}</span>
            </strong>
            <p>
              {session.state === "connected"
                ? "Data channel open. Both peers must confirm the matching code before chat is enabled."
                : session.state === "connecting"
                  ? "Waiting for the real data channel… Connection times out after 45 seconds."
                  : session.state === "pairing"
                    ? "Exchange codes within five minutes."
                    : "No live data channel."}
            </p>
          </div>
          {running && (
            <Button
              variant="destructive"
              onClick={() =>
                void (async () => {
                  await webRtcProvider.stop();
                  setIncoming("");
                  setText("");
                })()
              }
            >
              <Unplug size={15} />
              Disconnect / cancel
            </Button>
          )}
          {(error || session.error) && (
            <p role="alert" className="error-message">
              {error || session.error}
            </p>
          )}
          {notice && (
            <p role="status" className="success-message">
              {notice}
            </p>
          )}
        </Card>
        <div className="peer-right">
          <Card>
            <SectionTitle title="2. Verify the intended peer" />
            <p className="section-subtitle">
              Both screens must show exactly the same code. This binds the two
              session certificate fingerprints; it is not a verified account
              identity.
            </p>
            <code className="verification-code" data-testid="verification-code">
              {session.verificationCode || "Waiting for pairing…"}
            </code>
            <Button
              variant="secondary"
              disabled={
                busy ||
                session.state !== "connected" ||
                !session.verificationCode ||
                session.verified
              }
              onClick={() => void act(() => webRtcProvider.confirmPeer())}
            >
              <ShieldCheck size={16} />
              {session.verified
                ? "You confirmed the code"
                : "Codes match — confirm peer"}
            </Button>
            <p className="peer-verification-status">
              {session.peerVerified
                ? "Peer confirmed the code."
                : "Waiting for the peer to confirm."}
            </p>
          </Card>
          <Card className="peer-chat">
            <SectionTitle title="3. Real text conversation" />
            <div
              className="peer-messages"
              role="log"
              aria-label="Live peer messages"
              aria-live="polite"
            >
              {session.messages.length === 0 && (
                <div className="empty-state">
                  <Link2 size={26} />
                  <h3>Your real conversation starts here.</h3>
                  <p>
                    No sample messages or fabricated peers. Pair and verify both
                    browsers to send.
                  </p>
                </div>
              )}
              {session.messages.map((message) => (
                <div
                  key={message.id}
                  className={`message ${message.author === "you" ? "sent" : "received"}`}
                >
                  <span className="message-author">
                    {message.author === "you" ? "You" : "Paired peer"}
                  </span>
                  <p>{message.text}</p>
                  <small>{receiptLabels[message.status]}</small>
                </div>
              ))}
              <div ref={end} />
            </div>
            <form
              className="message-form"
              onSubmit={(e) => {
                e.preventDefault();
                void act(() => {
                  webRtcProvider.sendMessage(text);
                  setText("");
                });
              }}
            >
              <label htmlFor="peer-message" className="sr-only">
                Live message
              </label>
              <textarea
                id="peer-message"
                rows={2}
                maxLength={4000}
                disabled={!canSend || busy}
                value={text}
                onChange={(e) => setText(e.target.value)}
                placeholder={
                  canSend
                    ? "Write to your paired peer…"
                    : "Pair and verify both browsers first"
                }
              />
              <Button
                type="submit"
                disabled={!canSend || busy || !text.trim()}
                aria-label="Send live message"
              >
                <Send size={17} />
                Send
              </Button>
            </form>
            <p className="composer-note">
              Acknowledgements confirm receipt in peer app memory, not reading
              or durable storage. Unconfirmed after 10 seconds; no automatic
              resend. Up to 500 messages per session.
            </p>
          </Card>
        </div>
      </div>
    </>
  );
}
