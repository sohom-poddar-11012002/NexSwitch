"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { triggerRun, type TestRun } from "@/lib/api";

export default function RunTriggerForm({ runs }: { runs: TestRun[] }) {
  const router = useRouter();
  const [selectedRunId, setSelectedRunId] = useState(runs[0]?.id ?? "");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleTrigger() {
    if (!selectedRunId) return;
    setLoading(true);
    setError(null);
    try {
      const { executionId } = await triggerRun(selectedRunId);
      router.push(`/live/${executionId}`);
    } catch (e) {
      setError(String(e));
      setLoading(false);
    }
  }

  if (runs.length === 0) {
    return <p className="text-sm text-[var(--muted)]">No runs configured — check qa-orchestrator YAML files.</p>;
  }

  return (
    <div className="flex flex-col gap-3 p-4 rounded-lg border border-[var(--border)] bg-[var(--surface)]">
      <div className="flex gap-3 items-end">
        <div className="flex flex-col gap-1 flex-1">
          <label className="text-xs text-[var(--muted)]">Select Run</label>
          <select
            data-testid="run-selector"
            value={selectedRunId}
            onChange={(e) => setSelectedRunId(e.target.value)}
            className="bg-[var(--bg)] border border-[var(--border)] rounded px-3 py-2 text-sm focus:outline-none focus:border-brand"
          >
            {runs.map((r) => (
              <option key={r.id} value={r.id}>{r.name}</option>
            ))}
          </select>
        </div>
        <button
          data-testid="trigger-run-button"
          onClick={handleTrigger}
          disabled={loading}
          className="px-4 py-2 bg-brand hover:bg-brand-dark disabled:opacity-50 rounded text-sm font-medium transition-colors"
        >
          {loading ? "Starting…" : "Trigger Run"}
        </button>
      </div>
      {selectedRunId && (
        <SessionModeBadge session={runs.find((r) => r.id === selectedRunId)?.session} />
      )}
      {error && <p className="text-xs text-fail">{error}</p>}
    </div>
  );
}

function SessionModeBadge({ session }: { session?: { mode: string; isolateOnFailure: boolean } }) {
  if (!session) return null;
  const isStateful = session.mode === "STATEFUL";
  return (
    <div className="flex items-center gap-2 text-xs text-[var(--muted)]">
      <span className={`px-1.5 py-0.5 rounded font-mono ${isStateful ? "bg-indigo-900/50 text-indigo-300" : "bg-slate-800 text-slate-300"}`}>
        {session.mode}
      </span>
      {isStateful && session.isolateOnFailure && (
        <span>— isolates context on failure</span>
      )}
    </div>
  );
}
