"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  Search,
  Phone,
  Video,
  Send,
  ShieldCheck,
  Info,
  MessageCircle,
  Users,
  Paperclip,
  X,
  RefreshCw,
} from "lucide-react";
import { people } from "@/lib/data";
import { readMessages, saveMessage, type Message } from "@/lib/db";
import { Avatar, Badge } from "./shared";
import { Button } from "./ui/button";
export function ChatScreen({ id }: { id: string }) {
  const person = people.find((p) => p.id === id)!;
  const [messages, setMessages] = useState<Message[]>([]);
  const [text, setText] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);
  const [info, setInfo] = useState(false);
  const [retry, setRetry] = useState(0);
  const [query, setQuery] = useState("");
  const bottom = useRef<HTMLDivElement>(null);
  useEffect(() => {
    let cancelled = false;
    void readMessages(id)
      .then((rows) => {
        if (!cancelled) {
          setMessages(rows);
          setError("");
        }
      })
      .catch(() => {
        if (!cancelled)
          setError(
            "Local chat storage could not be opened. Check browser storage permissions, then retry.",
          );
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id, retry]);
  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [messages]);
  async function send(e: React.FormEvent) {
    e.preventDefault();
    if (!text.trim() || sending || loading) return;
    setSending(true);
    setError("");
    try {
      const message = await saveMessage(id, text);
      setMessages((old) => [...old, message]);
      setText("");
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Message could not be saved locally.",
      );
    } finally {
      setSending(false);
    }
  }
  return (
    <div className="chat-layout">
      <aside className="chat-list">
        <h1>
          Conversations <Badge>6</Badge>
        </h1>
        <label className="search-field">
          <Search size={17} />
          <input
            aria-label="Filter chat list"
            placeholder="Find a conversation…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>
        {people
          .filter((p) => p.name.toLowerCase().includes(query.toLowerCase()))
          .map((p) => (
            <Link
              className={`chat-list-row ${id === p.id ? "selected" : ""}`}
              key={p.id}
              href={`/conversations/${p.id}`}
            >
              <Avatar initials={p.initials} color={p.color} />
              <div>
                <strong>{p.name}</strong>
                <small>{p.message}</small>
              </div>
            </Link>
          ))}
        {!people.some((p) =>
          p.name.toLowerCase().includes(query.toLowerCase()),
        ) && <p>No matching conversations.</p>}
        <div className="chat-local-note">
          <ShieldCheck size={18} />
          Local demo chats
          <br />
          Saved on this browser.
        </div>
      </aside>
      <section className="chat-panel">
        <header className="chat-header">
          <Link
            className="chat-back"
            href="/conversations"
            aria-label="Back to conversations"
          >
            <ArrowLeft size={21} />
          </Link>
          <Avatar initials={person.initials} color={person.color} />
          <div>
            <h2>{person.name}</h2>
            <small>
              {person.group
                ? "Demo group · Sample members"
                : "Sample peer · No real connection"}
            </small>
          </div>
          <div className="chat-header-actions">
            <Button variant="ghost" size="icon" asChild>
              <Link href="/voice" aria-label="Open voice call preview">
                <Phone size={19} />
              </Link>
            </Button>
            <Button variant="ghost" size="icon" asChild>
              <Link href="/video" aria-label="Open video call preview">
                <Video size={20} />
              </Link>
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Conversation information"
              aria-expanded={info}
              onClick={() => setInfo(!info)}
            >
              <Info size={20} />
            </Button>
          </div>
        </header>
        <div className="chat-demo-banner">
          <ShieldCheck size={14} />
          Demo conversation · Messages stay on this device and are never sent.
        </div>
        {info && (
          <div className="chat-info">
            <div>
              <strong>
                {person.group
                  ? "Sample group members"
                  : "About this conversation"}
              </strong>
              <Button
                variant="ghost"
                size="icon"
                aria-label="Close conversation information"
                onClick={() => setInfo(false)}
              >
                <X size={17} />
              </Button>
            </div>
            <p>
              {person.group
                ? "Alex, Priya, Maya, Jordan, and you are illustrative members. No group transport is implemented."
                : "This profile is fictional. Your messages are saved in IndexedDB without delivery or read receipts."}
            </p>
            <Badge>
              {person.group ? <Users size={13} /> : <MessageCircle size={13} />}
              Local demonstration
            </Badge>
          </div>
        )}
        <div className="message-scroll" aria-live="polite" aria-busy={loading}>
          <div className="date-divider">DEMO CONVERSATION</div>
          <div className="chat-welcome">
            <Avatar initials={person.initials} color={person.color} large />
            <h3>
              {person.group
                ? "A space to come together."
                : "Every good connection starts with hello."}
            </h3>
            <p>This is your local demo conversation with {person.name}.</p>
          </div>
          <div className="message received">
            <span className="message-author">
              {person.group
                ? "Maya · Sample message"
                : `${person.name} · Sample message`}
            </span>
            <p>{person.message}</p>
            <small>Illustrative content</small>
          </div>
          {loading && (
            <p className="loading-state" role="status">
              Loading local history…
            </p>
          )}
          {messages.map((m) => (
            <div key={m.id} className="message sent">
              <p>{m.text}</p>
              <small>
                {new Date(m.createdAt).toLocaleTimeString([], {
                  hour: "2-digit",
                  minute: "2-digit",
                })}{" "}
                · Saved locally (demo)
              </small>
            </div>
          ))}
          <div ref={bottom} />
        </div>
        {error && (
          <div className="chat-error" role="alert">
            {error}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setLoading(true);
                setRetry((r) => r + 1);
              }}
            >
              <RefreshCw size={14} />
              Retry storage
            </Button>
          </div>
        )}
        <form className="message-form" onSubmit={send}>
          <Button asChild variant="ghost" size="icon">
            <Link href="/files" aria-label="Open local file preview">
              <Paperclip size={21} />
            </Link>
          </Button>
          <label className="sr-only" htmlFor="message">
            Message
          </label>
          <textarea
            id="message"
            placeholder="Write a little hello…"
            rows={1}
            maxLength={4000}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (
                e.key === "Enter" &&
                !e.shiftKey &&
                !e.nativeEvent.isComposing
              ) {
                e.preventDefault();
                e.currentTarget.form?.requestSubmit();
              }
            }}
          />
          <Button
            type="submit"
            disabled={!text.trim() || sending || loading}
            aria-label="Save demo message"
          >
            <Send size={18} />
            <span>{sending ? "Saving…" : "Send locally"}</span>
          </Button>
        </form>
        <div className="composer-note">
          Enter to save · Shift + Enter for a new line · No real delivery
        </div>
      </section>
    </div>
  );
}
