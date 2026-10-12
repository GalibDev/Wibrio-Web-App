"use client";
import { Button } from "@/components/ui/button";
export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <section className="empty-state">
      <h1>Something interrupted your space</h1>
      <p>
        Your local chats are still on this device. Try loading this view again.
      </p>
      <Button onClick={reset}>Try again</Button>
    </section>
  );
}
