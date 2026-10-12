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
export function Screen({ screen }: { screen: string }) {
  switch (screen) {
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
