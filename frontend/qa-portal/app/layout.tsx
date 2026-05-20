import type { Metadata } from "next";
import "./globals.css";
import Sidebar from "@/app/components/Sidebar";

export const metadata: Metadata = {
  title: "QA Portal — NexSwitch",
  description: "Adversarial test platform — recorder, runs, live dashboard, reports",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-[var(--bg)]">
        <Sidebar />
        <main className="pl-[220px] min-h-screen">
          <div className="max-w-5xl mx-auto p-8">
            {children}
          </div>
        </main>
      </body>
    </html>
  );
}
