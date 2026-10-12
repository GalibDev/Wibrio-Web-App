"use client";
import Link from "next/link";
import {
  ArrowRight,
  ArrowUpRight,
  Radio,
  Users,
  MessageCircle,
  FolderUp,
  ShieldCheck,
  Wifi,
  Network,
} from "lucide-react";
import { Button } from "../ui/button";
import {
  Avatar,
  Badge,
  Card,
  PageHeading,
  SectionTitle,
  SimulationNote,
} from "../shared";
import { Topology } from "../topology";
import { people } from "@/lib/data";
import { usePreferences, useSession } from "@/lib/store";
export function Dashboard() {
  const name = usePreferences((s) => s.name);
  const active = useSession((s) => s.active);
  return (
    <>
      <PageHeading
        title={`A little closer, ${name.split(" ")[0]}.`}
        description="Your people. Your conversations. Your own little network."
        action={
          <Button asChild>
            <Link href="/start">
              <Radio size={17} />
              {active ? "Manage demo mesh" : "Start demo mesh"}
              <ArrowUpRight size={16} />
            </Link>
          </Button>
        }
      />
      <div className="dashboard-top">
        <section className="hero-card">
          <div className="hero-copy">
            <Badge tone="hero-badge">
              <span className="status-dot" />A WORLD WITHOUT BARRIERS
            </Badge>
            <h2>
              Good connections.
              <br />
              Beyond the internet.
            </h2>
            <p>
              Find your people and keep the conversation going.
              <br className="desktop-break" /> Explore what a more connected
              world could feel like.
            </p>
            <Button asChild className="hero-button">
              <Link href="/nearby">
                Explore nearby people
                <ArrowRight size={17} />
              </Link>
            </Button>
            <span className="hero-footnote">
              <ShieldCheck size={14} />
              Your space. Your device. Local demo.
            </span>
          </div>
          <div className="hero-art" aria-hidden="true">
            <div className="hero-ring ring-one" />
            <div className="hero-ring ring-two" />
            <div className="hero-ring ring-three" />
            <div className="hero-center">
              <Radio size={40} />
            </div>
            <span className="hero-orb orb-a">
              <Users size={23} />
            </span>
            <span className="hero-orb orb-b">
              <MessageCircle size={23} />
            </span>
            <span className="hero-orb orb-c">
              <Wifi size={22} />
            </span>
            <span className="art-dot dot-a" />
            <span className="art-dot dot-b" />
          </div>
        </section>
        <Card className="session-card">
          <div className="section-title">
            <span className="icon-tile mint">
              <Radio size={22} />
            </span>
            <Badge tone="mint">
              {active ? "Demo active" : "Ready to explore"}
            </Badge>
          </div>
          <h2>Your mesh, at a glance</h2>
          <p>A private space to explore local connection.</p>
          <div className="session-line">
            <span>Environment</span>
            <strong>Local simulation</strong>
          </div>
          <div className="session-line">
            <span>Internet required</span>
            <strong>No, after caching</strong>
          </div>
          <div className="session-line">
            <span>Real peers connected</span>
            <strong>0 devices</strong>
          </div>
          <Link href="/network" className="text-link">
            Explore the demo network
            <ArrowRight size={16} />
          </Link>
        </Card>
      </div>
      <div className="stats-grid">
        {[
          {
            icon: Users,
            label: "Sample people",
            value: String(
              people.filter((person) => !person.group).length,
            ).padStart(2, "0"),
            detail: "Around your demo neighborhood",
            color: "blue",
          },
          {
            icon: MessageCircle,
            label: "Demo conversations",
            value: "06",
            detail: "A space for every conversation",
            color: "purple",
          },
          {
            icon: Network,
            label: "Illustrative links",
            value: "05",
            detail: "Explore the topology",
            color: "mint",
          },
          {
            icon: ShieldCheck,
            label: "Storage location",
            value: "On device",
            detail: "Your demo chats stay here",
            color: "orange",
          },
        ].map((stat) => (
          <Card key={stat.label} className="stat-card">
            <div className="stat-top">
              <span className={`icon-tile ${stat.color}`}>
                <stat.icon size={20} />
              </span>
              <span className="stat-tag">DEMO</span>
            </div>
            <p>{stat.label}</p>
            <strong className="stat-value">{stat.value}</strong>
            <small>{stat.detail}</small>
          </Card>
        ))}
      </div>
      <div className="dashboard-bottom">
        <Card>
          <SectionTitle title="People in your neighborhood" href="/nearby" />
          <p className="section-subtitle">
            A few familiar faces in the demo space.
          </p>
          <div className="people-list">
            {people
              .filter((p) => !p.group)
              .slice(0, 3)
              .map((p) => (
                <Link
                  href={`/conversations/${p.id}`}
                  className="person-row"
                  key={p.id}
                >
                  <Avatar initials={p.initials} color={p.color} />
                  <div>
                    <strong>{p.name}</strong>
                    <small>{p.role}</small>
                  </div>
                  <Badge>Sample peer</Badge>
                  <MessageCircle size={18} className="row-arrow" />
                </Link>
              ))}
          </div>
        </Card>
        <Card className="network-preview">
          <SectionTitle
            title="A network built around you"
            href="/network"
            link="Explore"
          />
          <Topology compact />
        </Card>
      </div>
      <div className="quick-actions">
        <span>MAKE YOURSELF AT HOME</span>
        <Link href="/conversations">
          <MessageCircle size={18} />
          Start a conversation
          <ArrowUpRight size={15} />
        </Link>
        <Link href="/files">
          <FolderUp size={18} />
          Explore file sharing
          <ArrowUpRight size={15} />
        </Link>
        <Link href="/profile">
          <ShieldCheck size={18} />
          Your device identity
          <ArrowUpRight size={15} />
        </Link>
      </div>
      <SimulationNote />
    </>
  );
}
