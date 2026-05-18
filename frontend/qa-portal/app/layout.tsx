import type { Metadata } from "next";
import "./globals.css";
import Link from "next/link";

export const metadata: Metadata = {
  title: "QA Portal — NexSwitch",
  description: "Adversarial test platform — recorder, runs, live dashboard, reports",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen flex flex-col">
        <nav className="flex items-center gap-6 px-6 py-3 border-b border-[var(--border)] bg-[var(--surface)]">
          <span className="font-bold text-brand tracking-tight">NexSwitch QA</span>
          <Link href="/scenarios" className="text-sm text-[var(--muted)] hover:text-[var(--text)] transition-colors">Scenarios</Link>
          <Link href="/runs" className="text-sm text-[var(--muted)] hover:text-[var(--text)] transition-colors">Runs</Link>
          <Link href="/reports" className="text-sm text-[var(--muted)] hover:text-[var(--text)] transition-colors">Reports</Link>
        </nav>
        <main className="flex-1 p-6">{children}</main>
      </body>
    </html>
  );
}
