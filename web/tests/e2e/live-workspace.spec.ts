import { expect, test } from "@playwright/test";

test("live workspace is the default and sample data requires explicit opt-in", async ({
  page,
}) => {
  for (const route of [
    "dashboard",
    "nearby",
    "conversations",
    "network",
    "conversations/alex",
  ]) {
    await page.goto(`/${route}`);
    await expect(
      page.getByRole("button", { name: "Switch to demo mode" }),
    ).toBeVisible();
    await expect(
      page.getByText("No real peer connected yet.", { exact: true }),
    ).toBeVisible();
    await expect(page.getByText("Alex Morgan", { exact: true })).toHaveCount(0);
    await expect(page.getByText("Priya Shah", { exact: true })).toHaveCount(0);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  }
  for (const route of ["groups", "files", "voice", "video"]) {
    await page.goto(`/${route}`);
    await expect(
      page.getByText("Unsupported operation", { exact: true }),
    ).toBeVisible();
  }
  await page.goto("/dashboard");
  await expect(page.getByTestId("live-connected-peers")).toHaveText("0");
  await page.getByRole("button", { name: "Switch to demo mode" }).click();
  await expect(
    page.getByText("A little closer, Jamie.", { exact: true }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Switch to live mode" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Switch to live mode" }).click();
  await expect(page.getByTestId("live-connected-peers")).toHaveText("0");
});
