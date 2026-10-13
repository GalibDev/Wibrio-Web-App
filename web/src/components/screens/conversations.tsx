"use client";
import Link from "next/link";
import { useState } from "react";
import { ArrowUpRight, Plus, Search, SlidersHorizontal } from "lucide-react";
import { Button } from "../ui/button";
import { Avatar, Badge, Card, PageHeading, SimulationNote } from "../shared";
import { people } from "@/lib/data";
export function Conversations({ groups = false }: { groups?: boolean }) {
  const [query, setQuery] = useState("");
  const list = people.filter(
    (p) =>
      (!groups || p.group) &&
      p.name.toLowerCase().includes(query.toLowerCase()),
  );
  return (
    <>
      <PageHeading
        title={groups ? "Better, together." : "Keep the conversation going."}
        description={
          groups
            ? "Shared spaces for your sample communities."
            : "A familiar place for every connection."
        }
        action={
          <Button asChild>
            <Link href="/nearby">
              <Plus size={17} />
              New conversation
            </Link>
          </Button>
        }
      />
      {!groups && (
        <div className="info-callout live-chat-link">
          <Link href="/connect">
            Pair a real browser for live text chat <ArrowUpRight size={16} />
          </Link>
          <p>The sample conversations below remain local demonstrations.</p>
        </div>
      )}
      <Card className="inbox">
        <div className="inbox-top">
          <div>
            <h2>
              {groups ? "Demo groups" : "Your conversations"}{" "}
              <Badge>{groups ? "02" : "06"}</Badge>
            </h2>
            <p>Sample previews · Your own messages are saved locally.</p>
          </div>
          <label className="search-field">
            <Search size={18} />
            <input
              aria-label="Search conversations"
              placeholder="Search conversations…"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </label>
        </div>
        {list.map((p) => (
          <Link
            className="conversation-row"
            href={`/conversations/${p.id}`}
            key={p.id}
          >
            <Avatar initials={p.initials} color={p.color} />
            <div>
              <strong>
                {p.name}
                {p.group && <Badge>Group</Badge>}
              </strong>
              <p>{p.message}</p>
            </div>
            <div className="conversation-time">
              <small>Sample · {p.time}</small>
              <ArrowUpRight size={17} />
            </div>
          </Link>
        ))}
        {list.length === 0 && (
          <Empty
            title="No conversations found"
            description="Try searching for Alex, Priya, or Community."
          />
        )}
      </Card>
      <SimulationNote />
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
