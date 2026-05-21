"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const links = [
  { href: "/scenarios", label: "Scenarios" },
  { href: "/runs",      label: "Runs" },
  { href: "/reports",   label: "Reports" },
  { href: "/suites",    label: "Suites" },
  { href: "/recorder",  label: "Recorder" },
];

export default function Sidebar() {
  const pathname = usePathname();
  return (
    <aside className="fixed left-0 top-0 h-screen w-[220px] flex flex-col border-r border-[var(--border)] bg-[var(--surface)]">
      <div className="px-4 py-5 border-b border-[var(--border)]">
        <Link href="/" className="flex items-center gap-2.5">
          <span className="w-6 h-6 rounded-md bg-brand flex items-center justify-center text-white text-xs font-bold shrink-0">
            Q
          </span>
          <span className="font-semibold text-[var(--text)] tracking-tight text-[13px]">NexSwitch QA</span>
        </Link>
      </div>

      <nav className="flex-1 p-2 space-y-0.5 overflow-y-auto">
        {links.map(({ href, label }) => {
          const active = pathname.startsWith(href);
          return (
            <Link
              key={href}
              href={href}
              data-testid={`nav-${label.toLowerCase()}`}
              className={`flex items-center gap-2.5 px-3 py-2 rounded-md text-sm transition-colors ${
                active
                  ? "bg-[var(--surface-2)] text-[var(--text)] font-medium"
                  : "text-[var(--muted)] hover:text-[var(--text)] hover:bg-[var(--surface-2)]/60"
              }`}
            >
              {label}
              {active && (
                <span className="ml-auto w-1.5 h-1.5 rounded-full bg-brand shrink-0" />
              )}
            </Link>
          );
        })}
      </nav>

      <div className="px-4 py-3 border-t border-[var(--border)]">
        <p className="text-[11px] text-[var(--muted)]">v1.0.0-SNAPSHOT</p>
      </div>
    </aside>
  );
}
