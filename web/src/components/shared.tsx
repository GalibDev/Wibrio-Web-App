import type { ReactNode } from "react";
import { ArrowUpRight, Radio } from "lucide-react";
import Link from "next/link";
import { cn } from "@/lib/utils";
export function Avatar({
  initials,
  color = "blue",
  large = false,
}: {
  initials: string;
  color?: string;
  large?: boolean;
}) {
  return (
    <span className={cn("avatar", color, large && "avatar-large")}>
      {initials}
    </span>
  );
}
export function Badge({
  children,
  tone = "",
}: {
  children: ReactNode;
  tone?: string;
}) {
  return <span className={cn("badge", tone)}>{children}</span>;
}
export function PageHeading({
  eyebrow = "YOUR LOCAL WORLD",
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action}
    </div>
  );
}
export function Card({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return <section className={cn("card", className)}>{children}</section>;
}
export function SectionTitle({
  title,
  href,
  link = "View all",
}: {
  title: string;
  href?: string;
  link?: string;
}) {
  return (
    <div className="section-title">
      <h2>{title}</h2>
      {href && (
        <Link href={href}>
          {link}
          <ArrowUpRight size={15} />
        </Link>
      )}
    </div>
  );
}
export function SimulationNote({ children }: { children?: ReactNode }) {
  return (
    <div className="simulation-note">
      <Radio size={15} />
      <span>
        {children ||
          "Demo environment · People, links, and activity shown here are simulated."}
      </span>
    </div>
  );
}
