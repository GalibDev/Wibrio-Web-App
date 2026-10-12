import { notFound } from "next/navigation";
import { Screen } from "@/components/screens";
const screens = [
  "start",
  "dashboard",
  "nearby",
  "conversations",
  "groups",
  "voice",
  "video",
  "files",
  "network",
  "settings",
  "profile",
];
export function generateStaticParams() {
  return screens.map((screen) => ({ screen }));
}
export default async function Page({
  params,
}: {
  params: Promise<{ screen: string }>;
}) {
  const { screen } = await params;
  if (!screens.includes(screen)) notFound();
  return <Screen screen={screen} />;
}
