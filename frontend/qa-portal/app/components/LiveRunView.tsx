"use client";

import { useRunSse, type SseEvent } from "@/hooks/useRunSse";
import WaitForHumanBanner from "./WaitForHumanBanner";
import StepTimeline from "./StepTimeline";

export default function LiveRunView({ executionId }: { executionId: string }) {
  const { events, waiting, done } = useRunSse(executionId);

  const runStatus = done
    ? events.find((e) => e.type === "RUN_PASSED") ? "PASSED" : "FAILED"
    : "RUNNING";

  return (
    <div className="max-w-2xl">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold">Live Run</h1>
          <p className="text-xs text-[var(--muted)] font-mono mt-0.5">{executionId}</p>
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
    </div>
  );
}

function RunStatusBadge({ status }: { status: string }) {
  const map: Record<string, string> = {
    PASSED: "bg-green-900/40 text-pass border-pass",
    FAILED: "bg-red-900/40 text-fail border-fail",
    RUNNING: "bg-indigo-900/40 text-indigo-300 border-indigo-500 animate-pulse",
  };
  return (
    <span className={`px-2 py-1 rounded border text-xs font-mono ${map[status] ?? ""}`}>
      {status}
    </span>
  );
}
