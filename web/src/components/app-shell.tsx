"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import {
  LayoutDashboard,
  Users,
  MessageCircle,
  UsersRound,
  Folder,
  Network,
  Settings,
  Radio,
  ArrowUpRight,
  Moon,
  Sun,
  ChevronDown,
  WifiOff,
  X,
  Bell,
  Menu,
  FlaskConical,
} from "lucide-react";
import { navigation } from "@/lib/data";
import { usePreferences, useSession } from "@/lib/store";
import { Button } from "./ui/button";
import { Avatar } from "./shared";
const icons = {
  dashboard: LayoutDashboard,
  people: Users,
  chat: MessageCircle,
  groups: UsersRound,
  files: Folder,
  network: Network,
  settings: Settings,
};
export function AppShell({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  const { theme, setTheme, name } = usePreferences();
  const active = useSession((s) => s.active);
  const [offline, setOffline] = useState(false);
  const [menu, setMenu] = useState(false);
  const [notifications, setNotifications] = useState(false);
  const [workerError, setWorkerError] = useState(false);
  useEffect(() => {
    void usePreferences.persist.rehydrate();
    const update = () => setOffline(!navigator.onLine);
    update();
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    if (process.env.NODE_ENV === "production" && "serviceWorker" in navigator) {
      void navigator.serviceWorker
        .register("/sw.js")
        .catch(() => setWorkerError(true));
    }
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);
  return (
    <>
      <a href="#main" className="skip-link">
        Skip to content
      </a>
      <aside className={`sidebar ${menu ? "sidebar-open" : ""}`}>
        <Link href="/dashboard" className="brand">
          <span className="brand-symbol">w</span>wibrio
          <span className="brand-dot">.</span>
        </Link>
        <Button
          variant="ghost"
          size="icon"
          className="close-menu"
          aria-label="Close navigation"
          onClick={() => setMenu(false)}
        >
          <X />
        </Button>
        <div className="workspace">
          <span className="workspace-icon">
            <Radio size={19} />
          </span>
          <div>
            <strong>My workspace</strong>
            <small>Personal demo space</small>
          </div>
          <ChevronDown size={15} />
        </div>
        <span className="nav-label">WORKSPACE</span>
        <nav aria-label="Main navigation">
          {navigation.map((item) => {
            const Icon = icons[item.icon];
            const selected =
              path === item.path ||
              (item.path === "/conversations" &&
                path.startsWith("/conversations/"));
            return (
              <Link
                key={item.path}
                href={item.path}
                onClick={() => setMenu(false)}
                className={`nav-link ${selected ? "selected" : ""}`}
                aria-current={selected ? "page" : undefined}
              >
                <Icon size={20} />
                {item.label}
                {item.icon === "chat" && <span className="nav-count">6</span>}
              </Link>
            );
          })}
        </nav>
        <div className="sidebar-bottom">
          <div className="connection-card">
            <span className="status-dot" />
            <strong>
              {active ? "Demo session active" : "Your world, connected."}
            </strong>
            <p>A little closer, even when you’re offline.</p>
            <Link href="/start">
              {active ? "Manage demo session" : "Start a demo mesh"}
              <ArrowUpRight size={16} />
            </Link>
          </div>
          <Link href="/profile" className="account">
            <Avatar initials={name.slice(0, 2).toUpperCase()} color="mint" />
            <div>
              <strong>{name}</strong>
              <small>Local device profile</small>
            </div>
            <Settings size={16} />
          </Link>
        </div>
      </aside>
      {menu && (
        <button
          className="nav-backdrop"
          aria-label="Close navigation"
          onClick={() => setMenu(false)}
        />
      )}
      <div className="app-main">
        <header className="topbar">
          <div className="breadcrumb">
            <Button
              variant="ghost"
              size="icon"
              className="menu-button"
              aria-label="Open navigation"
              onClick={() => setMenu(true)}
            >
              <Menu />
            </Button>
            <span>Workspace</span>
            <span className="slash">/</span>
            <strong>
              {path.startsWith("/conversations/")
                ? "Conversation"
                : path === "/start"
                  ? "Connection setup"
                  : path === "/profile"
                    ? "My profile"
                    : path === "/voice"
                      ? "Voice call"
                      : path === "/video"
                        ? "Video call"
                        : navigation.find((n) => n.path === path)?.label ||
                          "Wibrio"}
            </strong>
          </div>
          <div className="topbar-actions">
            <span className="demo-chip">
              <FlaskConical size={14} />
              Demo mode
            </span>
            <span className="topbar-divider" />
            <Button
              variant="ghost"
              size="icon"
              aria-label={
                theme === "light"
                  ? "Switch to dark theme"
                  : "Switch to light theme"
              }
              onClick={() => setTheme(theme === "light" ? "dark" : "light")}
            >
              {theme === "light" ? <Moon size={19} /> : <Sun size={19} />}
            </Button>
            <div className="notification-wrap">
              <Button
                variant="ghost"
                size="icon"
                aria-label="Notifications"
                aria-expanded={notifications}
                onClick={() => setNotifications(!notifications)}
              >
                <Bell size={19} />
              </Button>
              {notifications && (
                <div className="notification-popover">
                  <strong>You’re all caught up</strong>
                  <p>
                    Demo activity stays local. No network notifications are
                    being received.
                  </p>
                </div>
              )}
            </div>
            <Link href="/profile" aria-label="Open profile">
              <Avatar initials={name.slice(0, 2).toUpperCase()} />
            </Link>
          </div>
        </header>
        {offline && (
          <div className="offline-banner" role="status">
            <WifiOff size={16} />
            Browser offline · Local demo chats remain available. Previously
            visited pages are cached.
          </div>
        )}
        {workerError && (
          <div className="offline-banner" role="status">
            Offline page caching is unavailable. Local chat storage can still be
            used.
          </div>
        )}
        <main id="main" className="content">
          {children}
        </main>
        <footer className="app-footer">
          <span>
            <span className="status-dot" />
            Designed for connection. Built for possibility.
          </span>
          <span>Phase 1 · Local demonstration</span>
        </footer>
      </div>
      <nav className="bottom-nav" aria-label="Mobile navigation">
        {navigation
          .slice(0, 3)
          .concat(navigation[5], navigation[6])
          .map((item) => {
            const Icon = icons[item.icon];
            return (
              <Link
                key={item.path}
                href={item.path}
                aria-current={path.startsWith(item.path) ? "page" : undefined}
              >
                <Icon size={21} />
                <span>
                  {item.icon === "dashboard"
                    ? "Home"
                    : item.icon === "people"
                      ? "People"
                      : item.icon === "chat"
                        ? "Chats"
                        : item.icon === "network"
                          ? "Network"
                          : "Settings"}
                </span>
              </Link>
            );
          })}
      </nav>
    </>
  );
}
