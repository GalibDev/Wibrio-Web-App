import Dexie, { type Table } from "dexie";
export interface Message {
  id: string;
  conversationId: string;
  text: string;
  createdAt: number;
  author: "you" | "sample";
  status: "local-demo";
}
class WibrioDatabase extends Dexie {
  messages!: Table<Message, string>;
  constructor() {
    super("wibrio-demo-v1");
    this.version(1).stores({ messages: "id, conversationId, createdAt" });
  }
}
export const db = new WibrioDatabase();
export async function readMessages(conversationId: string) {
  return db.messages
    .where("conversationId")
    .equals(conversationId)
    .sortBy("createdAt");
}
export async function saveMessage(
  conversationId: string,
  text: string,
): Promise<Message> {
  const message: Message = {
    id: crypto.randomUUID(),
    conversationId,
    text: text.trim(),
    createdAt: Date.now(),
    author: "you",
    status: "local-demo",
  };
  if (!message.text) throw new Error("Enter a message first.");
  if (message.text.length > 4000)
    throw new Error("Messages must be 4,000 characters or fewer.");
  await db.messages.add(message);
  return message;
}
