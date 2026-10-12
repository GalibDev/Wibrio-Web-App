import "fake-indexeddb/auto";
import { afterEach, describe, expect, it } from "vitest";
import { db, readMessages, saveMessage } from "../src/lib/db";
afterEach(async () => {
  await db.messages.clear();
});
describe("local demo persistence", () => {
  it("persists chats separately without claiming delivery", async () => {
    await saveMessage("alex", " Hello Alex ");
    await saveMessage("priya", "Hello Priya");
    const messages = await readMessages("alex");
    expect(messages).toHaveLength(1);
    expect(messages[0]).toMatchObject({
      text: "Hello Alex",
      status: "local-demo",
      author: "you",
    });
  });
  it("rejects empty and oversized messages", async () => {
    await expect(saveMessage("alex", "   ")).rejects.toThrow("Enter a message");
    await expect(saveMessage("alex", "a".repeat(4001))).rejects.toThrow(
      "4,000",
    );
    expect(await readMessages("alex")).toEqual([]);
  });
});
