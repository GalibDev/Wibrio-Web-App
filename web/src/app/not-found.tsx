import Link from "next/link";
export default function NotFound() {
  return (
    <section className="empty-state">
      <h1>This path is a little off the map.</h1>
      <p>Let’s get you back to your space.</p>
      <Link className="button button-primary" href="/dashboard">
        Back to overview
      </Link>
    </section>
  );
}
