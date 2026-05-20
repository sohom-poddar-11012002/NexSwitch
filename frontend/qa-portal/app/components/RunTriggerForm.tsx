"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { triggerRun, type TestRun } from "@/lib/api";

export default function RunTriggerForm({ runs }: { runs: TestRun[] }) {
  const router = useRouter();
  const [selectedRunId, setSelectedRunId] = useState(runs[0]?.id ?? "");
  const [loading, setLoading]             = useState(false);
  const [error, setError]                 = useState<string | null>(null);

  async function handleTrigger() {
    if (!selectedRunId) return;
    setLoading(true); setError(null);
    try {
      const { executionId } = await triggerRun(selectedRunId);
      router.push(`/live/${executionId}`);
    } catch (e) {
      setError(String(e));
      setLoading(false);
    }
  }

  if (runs.length === 0) {
    return (
      <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-5 text-sm text-[var(--muted)]">
        No runs configured. Check qa-orchestrator YAML files.
      </div>
    );
  }

  const selectedRun = runs.find((r) => r.id === selectedRunId);

  return (
    <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-5 space-y-4">
      <div>
        <label className="block text-xs text-[var(--muted)] mb-1.5">Select Run</label>
        <select
          data-testid="run-selector"
          value={selectedRunId}
          onChange={(e) => setSelectedRunId(e.target.value)}
          className="w-full rounded-md border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm text-[var(--text)] focus:outline-none focus:border-brand transition-colors"
        >
          {runs.map((r) => (
            <option key={r.id} value={r.id}>{r.name}</option>
          ))}
        </select>
      </div>

      {selectedRun?.session && <SessionModeBadge session={selectedRun.session} />}

      {error && <p className="text-xs text-red-400">{error}</p>}

      <button
        data-testid="trigger-run-button"
        onClick={handleTrigger}
        disabled={loading}
        className="w-full rounded-md bg-brand hover:bg-brand-dark disabled:opacity-40 text-white text-sm font-medium py-2.5 transition-colors"
      >
        {loading ? "Starting…" : "Trigger Run"}
      </button>
    </div>
  );
}

function SessionModeBadge({ session }: { session: { mode: string; isolateOnFailure: boolean } }) {
  const isStateful = session.mode === "STATEFUL";
  return (
    <div className="flex items-center gap-2">
      <span className={`text-[11px] px-2 py-0.5 rounded border font-mono ${
        isStateful
          ? "border-brand/30 bg-brand/5 text-brand"
          : "border-[var(--border)] bg-[var(--surface-2)] text-[var(--muted)]"
      }`}>
        {session.mode}
      </span>
      {isStateful && session.isolateOnFailure && (
        <span className="text-xs text-[var(--muted)]">isolates on failure</span>
      )}
    </div>
  );
}
