"use client";

import { useEffect, useState } from "react";
import { useRunSse } from "@/hooks/useRunSse";
import { fetchExecution, type RunExecution } from "@/lib/api";
import WaitForHumanBanner from "./WaitForHumanBanner";
import StepTimeline from "./StepTimeline";

export default function LiveRunView({ executionId }: { executionId: string }) {
  const { events, waiting, done } = useRunSse(executionId);
  const [execution, setExecution] = useState<RunExecution | null>(null);

  // Poll REST API every 3s so the status badge is always current,
  // even if the user arrived after SSE events already fired.
  useEffect(() => {
    let cancelled = false;
    async function poll() {
      try {
        const ex = await fetchExecution(executionId);
        if (!cancelled) setExecution(ex);
        if (!cancelled && ex.status === "RUNNING") {
          setTimeout(poll, 3000);
        }
      } catch { /* orchestrator unreachable */ }
    }
    poll();
    return () => { cancelled = true; };
  }, [executionId]);

  const sseStatus = done
    ? events.find((e) => e.type === "RUN_PASSED") ? "PASSED" : "FAILED"
    : null;
  const runStatus = sseStatus ?? execution?.status ?? "RUNNING";

  return (
    <div className="max-w-2xl">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold">Live Run</h1>
          <p className="text-xs text-[var(--muted)] font-mono mt-0.5">{executionId}</p>
          {execution?.runId && (
            <p className="text-xs text-[var(--muted)] mt-0.5">{execution.runId}</p>
          )}
        </div>
        <RunStatusBadge status={runStatus} />
      </div>

      {waiting && (
        <WaitForHumanBanner
          executionId={executionId}
          stepId={waiting.stepId}
          instruction={waiting.instruction}
          expiresAt={waiting.expiresAt}
        />
      )}

      <StepTimeline events={events} />

      {runStatus !== "RUNNING" && events.length === 0 && (
        <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-6 text-center text-sm text-[var(--muted)]">
          Run {runStatus.toLowerCase()} — live events are no longer available.
          <br />
          <a href="/runs" className="text-brand hover:underline text-xs mt-1 inline-block">← Back to Runs</a>
        </div>
      )}
    </div>
  );
}

function RunStatusBadge({ status }: { status: string }) {
  const map: Record<string, string> = {
    PASSED:  "bg-green-900/40 text-pass border-pass",
    FAILED:  "bg-red-900/40 text-fail border-fail",
    RUNNING: "bg-indigo-900/40 text-indigo-300 border-indigo-500 animate-pulse",
  };
  return (
    <span className={`px-2 py-1 rounded border text-xs font-mono ${map[status] ?? ""}`}>
      {status}
    </span>
  );
}
