"use client";
import { Dashboard } from "./screens/dashboard";
import { Setup } from "./screens/setup";
import { People } from "./screens/people";
import { Conversations } from "./screens/conversations";
import { NetworkScreen } from "./screens/network-screen";
import { Files } from "./screens/files";
import { SettingsScreen } from "./screens/settings-screen";
import { Profile } from "./screens/profile";
import { Call } from "./screens/call";
import { PeerChat } from "./screens/peer-chat";
import { useAppMode } from "@/lib/store";
import { LiveWorkspace } from "./screens/live-workspace";
export function Screen({ screen }: { screen: string }) {
  const mode = useAppMode((s) => s.mode);
  if (mode === "live" && screen === "start") return <PeerChat />;
  if (
    mode === "live" &&
    [
      "dashboard",
      "nearby",
      "conversations",
      "groups",
      "network",
      "files",
      "voice",
      "video",
    ].includes(screen)
  )
    return <LiveWorkspace screen={screen} />;
  switch (screen) {
    case "connect":
      return <PeerChat />;
    case "dashboard":
      return <Dashboard />;
    case "start":
      return <Setup />;
    case "nearby":
      return <People />;
    case "conversations":
      return <Conversations />;
    case "groups":
      return <Conversations groups />;
    case "network":
      return <NetworkScreen />;
    case "files":
      return <Files />;
    case "settings":
      return <SettingsScreen />;
    case "profile":
      return <Profile />;
    case "voice":
      return <Call />;
    case "video":
      return <Call video />;
    default:
      return null;
  }
}
