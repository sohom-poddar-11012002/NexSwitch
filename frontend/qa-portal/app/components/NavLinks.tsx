"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const links = [
  { href: "/scenarios", label: "Scenarios" },
  { href: "/runs", label: "Runs" },
  { href: "/reports", label: "Reports" },
  { href: "/suites", label: "Suites" },
  { href: "/recorder", label: "Recorder" },
];

export default function NavLinks() {
  const pathname = usePathname();
  return (
    <>
      {links.map(({ href, label }) => (
        <Link
          key={href}
          href={href}
          className={`text-sm transition-colors px-1 pb-0.5 border-b-2 ${
            pathname.startsWith(href)
              ? "border-brand text-[var(--text)] font-medium"
              : "border-transparent text-[var(--muted)] hover:text-[var(--text)]"
          }`}
        >
          {label}
        </Link>
      ))}
    </>
  );
}
