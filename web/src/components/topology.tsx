import { Radio } from "lucide-react";
import { Avatar } from "./shared";
const nodes = [
  { x: 20, y: 28, initials: "AM", color: "blue", name: "Alex" },
  { x: 72, y: 18, initials: "PS", color: "purple", name: "Priya" },
  { x: 86, y: 61, initials: "JL", color: "orange", name: "Jordan" },
  { x: 61, y: 84, initials: "MC", color: "pink", name: "Maya" },
  { x: 16, y: 75, initials: "RK", color: "mint", name: "Riley" },
];
export function Topology({ compact = false }: { compact?: boolean }) {
  return (
    <div
      className={`topology ${compact ? "compact" : ""}`}
      role="img"
      aria-label="Illustrative simulated topology: your device linked to five sample peers. No real routes."
    >
      <div className="orbit orbit-one" />
      <div className="orbit orbit-two" />
      <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
        {nodes.map((node) => (
          <line
            key={node.name}
            x1="50"
            y1="50"
            x2={node.x}
            y2={node.y}
            stroke="var(--line-blue)"
            strokeWidth="0.45"
            strokeDasharray="1.2 1.4"
          />
        ))}
      </svg>
      <div className="map-you">
        <Radio size={compact ? 21 : 29} />
        <strong>You</strong>
      </div>
      {nodes.map((node) => (
        <div
          className="map-node"
          key={node.name}
          style={{ left: `${node.x}%`, top: `${node.y}%` }}
        >
          <Avatar initials={node.initials} color={node.color} />
          <span>{node.name}</span>
        </div>
      ))}
      <span className="map-label">ILLUSTRATIVE DEMO TOPOLOGY</span>
    </div>
  );
}
