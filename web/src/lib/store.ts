import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import type { ProviderId } from "./network";
interface Preferences {
  theme: "light" | "dark";
  name: string;
  provider: ProviderId;
  notifications: boolean;
  setTheme: (theme: "light" | "dark") => void;
  setName: (name: string) => void;
  setProvider: (provider: ProviderId) => void;
  setNotifications: (enabled: boolean) => void;
}
export const usePreferences = create<Preferences>()(
  persist(
    (set) => ({
      theme: "light",
      name: "Jamie Parker",
      provider: "demo",
      notifications: true,
      setTheme: (theme) => set({ theme }),
      setName: (name) => set({ name }),
      setProvider: (provider) => set({ provider }),
      setNotifications: (notifications) => set({ notifications }),
    }),
    {
      name: "wibrio-preferences",
      storage: createJSONStorage(() => localStorage),
      skipHydration: true,
    },
  ),
);
export const useSession = create<{
  active: boolean;
  setActive: (active: boolean) => void;
}>((set) => ({ active: false, setActive: (active) => set({ active }) }));
