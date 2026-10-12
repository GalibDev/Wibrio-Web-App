"use client";
import Link from "next/link";
import { useState } from "react";
import { ArrowRight, Radio, Network } from "lucide-react";
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
export function NetworkScreen() {
  const [view, setView] = useState("map");
  return (
    <>
      <PageHeading
        title="Every connection tells a story."
        description="Explore how a mesh could bring your local world together."
        action={
          <Badge tone="mint">
            <Radio size={14} />
            Illustrative network
          </Badge>
        }
      />
      <div className="network-layout">
        <Card className="network-full">
          <div className="section-title">
            <h2>Your demo neighborhood</h2>
            <div className="tabs">
              <button
                className={view === "map" ? "active" : ""}
                onClick={() => setView("map")}
              >
                Map
              </button>
              <button
                className={view === "list" ? "active" : ""}
                onClick={() => setView("list")}
              >
                Peers
              </button>
            </div>
          </div>
          {view === "map" ? (
            <Topology />
          ) : (
            <div className="people-list">
              {people
                .filter((p) => !p.group)
                .map((p) => (
                  <div className="person-row" key={p.id}>
                    <Avatar initials={p.initials} color={p.color} />
                    <div>
                      <strong>{p.name}</strong>
                      <small>Illustrative peer · No real route</small>
                    </div>
                    <Badge>Simulated</Badge>
                  </div>
                ))}
            </div>
          )}
          <div className="map-legend">
            <span>
              <i className="legend-dot" />
              Your device
            </span>
            <span>
              <i className="legend-dot pale" />
              Sample peer
            </span>
            <span>┄ Illustrative link</span>
          </div>
        </Card>
        <div className="network-details">
          <Card>
            <SectionTitle title="Network details" />
            {[
              ["Environment", "Demo simulation"],
              ["Actual connected peers", "0"],
              ["Real routes", "None"],
              ["Routing engine", "Not integrated"],
              ["Transport", "Local only"],
            ].map(([label, value]) => (
              <div className="session-line" key={label}>
                <span>{label}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </Card>
          <div className="info-callout">
            <Network size={23} />
            <div>
              <strong>Made for the possibilities.</strong>
              <p>
                These links illustrate a potential mesh topology. Link quality,
                distance, and route delivery are not measured.
              </p>
              <Link className="text-link" href="/start">
                View provider capabilities
                <ArrowRight size={15} />
              </Link>
            </div>
          </div>
        </div>
      </div>
      <SimulationNote />
    </>
  );
}
