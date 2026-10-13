import { expect, test, type Page } from "@playwright/test";

async function pair(a: Page, b: Page) {
  await Promise.all([a.goto("/connect"), b.goto("/connect")]);
  await a.getByRole("button", { name: "Create offer", exact: true }).click();
  await expect(a.getByLabel("Your pairing code")).not.toHaveValue("", {
    timeout: 20000,
  });
  const offer = await a.getByLabel("Your pairing code").inputValue();
  await b.getByRole("button", { name: "Join invitation" }).click();
  await b.getByLabel("Peer offer code").fill(offer);
  await b.getByRole("button", { name: "Accept offer & create answer" }).click();
  await expect(b.getByLabel("Your pairing code")).not.toHaveValue("", {
    timeout: 20000,
  });
  const answer = await b.getByLabel("Your pairing code").inputValue();
  await a.getByLabel("Peer answer code").fill(answer);
  await a.getByRole("button", { name: "Apply answer" }).click();
  await expect(a.getByTestId("connection-state")).toHaveText("connected", {
    timeout: 20000,
  });
  await expect(b.getByTestId("connection-state")).toHaveText("connected", {
    timeout: 20000,
  });
  await expect(a.getByTestId("verification-code")).not.toHaveText(
    "Waiting for pairing…",
  );
  await expect(b.getByTestId("verification-code")).toHaveText(
    (await a.getByTestId("verification-code").textContent()) as string,
  );
  await expect(
    a.getByRole("button", { name: "Send live message" }),
  ).toBeDisabled();
  await a.getByRole("button", { name: "Codes match — confirm peer" }).click();
  await expect(a.getByLabel("Live message", { exact: true })).toBeDisabled();
  await b.getByRole("button", { name: "Codes match — confirm peer" }).click();
  await expect(a.getByLabel("Live message", { exact: true })).toBeEnabled();
  await expect(b.getByLabel("Live message", { exact: true })).toBeEnabled();
}

test("real data channels exchange acknowledged text, survive client navigation, and disconnect", async ({
  page,
  context,
}) => {
  test.setTimeout(60000);
  const peer = await context.newPage();
  await pair(page, peer);
  await page
    .getByLabel("Live message", { exact: true })
    .fill("A real message from browser A");
  await page.getByRole("button", { name: "Send live message" }).click();
  await expect(
    peer.getByText("A real message from browser A", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Acknowledged by peer app · Not a read receipt", {
      exact: true,
    }),
  ).toBeVisible();
  await peer
    .getByLabel("Live message", { exact: true })
    .fill('<script>alert("plain text only")</script>');
  await peer.getByRole("button", { name: "Send live message" }).click();
  await expect(
    page.getByText('<script>alert("plain text only")</script>', {
      exact: true,
    }),
  ).toBeVisible();
  // Use client-side navigation rather than reload; the in-memory provider stays alive.
  const menu = page.getByRole("button", { name: "Open navigation" });
  if (await menu.isVisible()) await menu.click();
  await page
    .getByRole("navigation", { name: "Main navigation", exact: true })
    .getByRole("link", { name: "Conversations" })
    .click();
  await page.getByRole("link", { name: /Pair a real browser/ }).click();
  await expect(page.getByTestId("connection-state")).toHaveText("connected");
  await expect(
    page.getByText("A real message from browser A", { exact: true }),
  ).toBeVisible();
  await peer.getByRole("button", { name: "Disconnect / cancel" }).click();
  await expect(page.getByTestId("connection-state")).toHaveText(
    /disconnected|failed/,
    { timeout: 15000 },
  );
  await expect(page.getByLabel("Live message", { exact: true })).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Create offer", exact: true }),
  ).toBeEnabled();
  await pair(page, peer);
  await expect(
    page.getByText("A real message from browser A", { exact: true }),
  ).toHaveCount(0);
  await page
    .getByLabel("Live message", { exact: true })
    .fill("Fresh verified session after reconnect");
  await page.getByRole("button", { name: "Send live message" }).click();
  await expect(
    peer.getByText("Fresh verified session after reconnect", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Disconnect / cancel" }).click();
  await peer.close();
});

test("missing acknowledgements remain unconfirmed and late receipts update truthfully", async ({
  page,
  context,
}) => {
  test.setTimeout(60000);
  const peer = await context.newPage();
  await peer.addInitScript(() => {
    const send = RTCDataChannel.prototype.send;
    RTCDataChannel.prototype.send = function (
      data: string | Blob | ArrayBuffer | ArrayBufferView,
    ) {
      if (typeof data === "string" && JSON.parse(data).kind === "ack") {
        setTimeout(() => {
          if (this.readyState === "open") Reflect.apply(send, this, [data]);
        }, 12000);
      } else {
        Reflect.apply(send, this, [data]);
      }
    };
  });
  await pair(page, peer);
  await page
    .getByLabel("Live message", { exact: true })
    .fill("Receipt must come from the real peer");
  await page.getByRole("button", { name: "Send live message" }).click();
  await expect(
    peer.getByText("Receipt must come from the real peer", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Awaiting peer acknowledgement", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Receipt unconfirmed · Not automatically resent", {
      exact: true,
    }),
  ).toBeVisible({ timeout: 11500 });
  await expect(
    page.getByText("Acknowledged by peer app · Not a read receipt", {
      exact: true,
    }),
  ).toBeVisible({ timeout: 7000 });
  await page.getByRole("button", { name: "Disconnect / cancel" }).click();
  await peer.close();
});

test("invalid pairing codes fail safely without creating a peer", async ({
  page,
}) => {
  await page.goto("/connect");
  await expect(
    page.getByRole("button", { name: "Create offer", exact: true }),
  ).toBeEnabled();
  expect(await page.evaluate(() => window.scrollY)).toBe(0);
  await page.getByRole("button", { name: "Join invitation" }).click();
  await page
    .getByLabel("Peer offer code")
    .fill('{"version":1,"type":"answer","sdp":"untrusted"}');
  await page
    .getByRole("button", { name: "Accept offer & create answer" })
    .click();
  await expect(page.locator('main [role="alert"]')).toContainText(
    "Expected a Wibrio offer code",
  );
  await expect(page.getByTestId("connection-state")).toHaveText("idle");
  await expect(
    page.getByRole("button", { name: "Send live message" }),
  ).toBeDisabled();
});
