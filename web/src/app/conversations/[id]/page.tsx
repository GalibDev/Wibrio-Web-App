import { notFound } from "next/navigation";
import { ChatScreen } from "@/components/chat";
import { people } from "@/lib/data";
export function generateStaticParams() {
  return people.map(({ id }) => ({ id }));
}
export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  if (!people.some((person) => person.id === id)) notFound();
  return <ChatScreen id={id} />;
}
