"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import {
  ArrowRight,
  MessageCircle,
  ShieldCheck,
  Search,
  SlidersHorizontal,
  Laptop,
  RefreshCw,
} from "lucide-react";
import { Button } from "../ui/button";
import { Avatar, Badge, Card, PageHeading, SimulationNote } from "../shared";
import { people } from "@/lib/data";
export function People() {
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("all");
  const [scanning, setScanning] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );
  const filtered = people.filter(
    (p) =>
      !p.group &&
      p.name.toLowerCase().includes(query.toLowerCase()) &&
      (filter === "all" || p.id === "alex" || p.id === "priya"),
  );
  return (
    <>
      <PageHeading
        title="Good people. A little closer."
        description="Discover the faces in your sample neighborhood."
        action={
          <Button
            variant="secondary"
            disabled={scanning}
            onClick={() => {
              setScanning(true);
              timer.current = setTimeout(() => setScanning(false), 700);
            }}
          >
            <RefreshCw size={17} className={scanning ? "spin" : ""} />
            {scanning ? "Refreshing samples…" : "Refresh demo people"}
          </Button>
        }
      />
      <SimulationNote>
        Sample profiles only · Refreshing does not scan for real devices.
      </SimulationNote>
      <div className="toolbar">
        <label className="search-field">
          <Search size={18} />
          <input
            aria-label="Search people"
            placeholder="Search your neighborhood…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>
        <div className="tabs">
          {["all", "favorites"].map((f) => (
            <button
              key={f}
              aria-pressed={filter === f}
              className={filter === f ? "active" : ""}
              onClick={() => setFilter(f)}
            >
              {f === "all" ? "All people" : "Favorites"}
            </button>
          ))}
        </div>
      </div>
      {scanning ? (
        <div className="loading-state" role="status">
          <span className="spinner" />
          Loading sample profiles…
        </div>
      ) : (
        <div className="people-grid">
          {filtered.map((p, index) => (
            <Card className="person-card" key={p.id}>
              <div className="person-card-top">
                <Avatar initials={p.initials} color={p.color} large />
                <Badge tone="mint">Sample peer</Badge>
              </div>
              <h2>{p.name}</h2>
              <p>{p.role}</p>
              <div className="person-meta">
                <Laptop size={15} />
                <span>
                  {index % 2 ? "Sample laptop" : "Sample mobile device"}
                </span>
                <ShieldCheck size={15} />
              </div>
              <Button variant="secondary" asChild>
                <Link href={`/conversations/${p.id}`}>
                  <MessageCircle size={16} />
                  Open demo chat
                  <ArrowRight size={16} />
                </Link>
              </Button>
            </Card>
          ))}
        </div>
      )}
      {!scanning && filtered.length === 0 && (
        <Empty
          title="No sample people found"
          description="Try another name or switch to All people."
        />
      )}
    </>
  );
}
function Empty({ title, description }: { title: string; description: string }) {
  return (
    <div className="empty-state">
      <span className="icon-tile blue">
        <SlidersHorizontal size={23} />
      </span>
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}
