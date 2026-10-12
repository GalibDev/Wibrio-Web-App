import { expect, test } from "@playwright/test";

test("profile and contact search work across navigation", async ({ page }) => {
  await page.goto("/profile");
  await page.getByLabel("Display name").fill("Taylor Local");
  await page.getByRole("button", { name: "Save changes" }).click();
  await page.reload();
  await expect(page.getByLabel("Display name")).toHaveValue("Taylor Local");
  await page.goto("/nearby");
  await page.getByLabel("Search people").fill("No matching person");
  await expect(page.getByText("No sample people found")).toBeVisible();
  await page.getByLabel("Search people").fill("Alex");
  await page.getByRole("link", { name: "Open demo chat" }).click();
  await expect(page.locator(".chat-header h2")).toHaveText("Alex Morgan");
});

test("file previews remain local and chat deletion requires confirmation", async ({
  page,
}) => {
  await page.goto("/files");
  const chooserPromise = page.waitForEvent("filechooser");
  await page.locator('button').filter({ hasText: 'Choose local files' }).click();
  const chooser = await chooserPromise;
  await chooser.setFiles({
    name: "hello.txt",
    mimeType: "text/plain",
    buffer: Buffer.from("A local preview"),
  });
  await expect(page.getByText("hello.txt", { exact: true })).toBeVisible();
  await expect(page.getByText(/Nothing was transmitted/)).toBeVisible();
  await page.getByRole("button", { name: "Remove hello.txt" }).click();
  await expect(page.getByText("A fresh space for your files")).toBeVisible();
  await page.goto("/conversations/alex");
  await page
    .getByLabel("Message", { exact: true })
    .fill("Delete this local message");
  await page.getByRole("button", { name: "Save demo message" }).click();
  await expect(page.getByText("Delete this local message")).toBeVisible();
  await page.goto("/settings");
  await page.getByRole("button", { name: "Clear demo chat history" }).click();
  await expect(page.getByText("Delete all local demo messages?")).toBeVisible();
  await page
    .getByRole("button", { name: "Delete messages", exact: true })
    .click();
  await expect(
    page.getByText("Local demo chat history cleared."),
  ).toBeVisible();
  await page.goto("/conversations/alex");
  await expect(page.getByText("Delete this local message")).toHaveCount(0);
});
test("screens render without horizontal overflow or client errors", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  for (const route of [
    "dashboard",
    "start",
    "nearby",
    "conversations",
    "conversations/alex",
    "conversations/community",
    "groups",
    "voice",
    "video",
    "files",
    "network",
    "settings",
    "profile",
  ]) {
    await page.goto(`/${route}`);
    await expect(
      page
        .locator(
          route.startsWith("conversations/")
            ? "main .chat-header h2"
            : "main h1",
        )
        .first(),
    ).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
  }
  expect(errors).toEqual([]);
});
test("saves messages across reload and keeps conversations separate", async ({
  page,
}) => {
  await page.goto("/conversations/alex");
  await page
    .getByLabel("Message", { exact: true })
    .fill("Hello from this local browser");
  await page.getByRole("button", { name: "Save demo message" }).click();
  await expect(page.getByText("Hello from this local browser")).toBeVisible();
  await page.reload();
  await expect(page.getByText("Hello from this local browser")).toBeVisible();
  await page.goto("/conversations/priya");
  await expect(page.getByText("Hello from this local browser")).toHaveCount(0);
});
test("theme persists and unsupported providers never start", async ({
  page,
}) => {
  await page.goto("/settings");
  await page.getByLabel("Dark theme", { exact: true }).check();
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.goto("/start");
  await page.getByRole("radio", { name: /WebRTC/ }).check();
  await page.getByRole("button", { name: "Start selected provider" }).click();
  await expect(page.locator('main [role="alert"]')).toContainText(
    "not implemented",
  );
  await page.getByRole("radio", { name: /Demo network/ }).check();
  await page.getByRole("button", { name: "Start selected provider" }).click();
  await expect(
    page.getByText("Demo session active. No real peers are connected."),
  ).toBeVisible();
});
test("chat works offline after the production shell is cached", async ({
  page,
  context,
}) => {
  await page.goto("/conversations/alex");
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  await page.reload();
  await page.getByLabel("Message", { exact: true }).fill("Cached chat");
  await page.getByRole("button", { name: "Save demo message" }).click();
  await expect(page.getByText("Cached chat", { exact: true })).toBeVisible();
  await context.setOffline(true);
  await page.reload();
  await expect(page.getByText("Cached chat", { exact: true })).toBeVisible();
  await page.getByLabel("Message", { exact: true }).fill("Saved while offline");
  await page.getByRole("button", { name: "Save demo message" }).click();
  await expect(
    page.getByText("Saved while offline", { exact: true }),
  ).toBeVisible();
});
