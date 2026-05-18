"use client";

import type { SseEvent } from "@/hooks/useRunSse";

const STEP_EVENTS = new Set(["STEP_STARTED", "STEP_PASSED", "STEP_FAILED", "WAITING_FOR_HUMAN"]);

function stepColor(type: string) {
  if (type === "STEP_PASSED") return "text-pass border-pass bg-green-950/40";
  if (type === "STEP_FAILED") return "text-fail border-fail bg-red-950/40";
  if (type === "WAITING_FOR_HUMAN") return "text-waiting border-waiting bg-yellow-950/40";
  return "text-[var(--muted)] border-[var(--border)] bg-[var(--surface)]";
}

function stepIcon(type: string) {
  if (type === "STEP_PASSED") return "✓";
  if (type === "STEP_FAILED") return "✗";
  if (type === "WAITING_FOR_HUMAN") return "⏸";
  return "•";
}

export default function StepTimeline({ events }: { events: SseEvent[] }) {
  // Only render one entry per stepId — last seen event wins
  const byStep = new Map<string, SseEvent>();
  for (const e of events) {
    if (STEP_EVENTS.has(e.type) && e.stepId) {
      byStep.set(e.stepId, e);
    }
  }

  const steps = [...byStep.values()];
  if (steps.length === 0) {
    return <p className="text-sm text-[var(--muted)]">Waiting for steps…</p>;
  }

  return (
    <div className="flex flex-col gap-2" data-testid="step-timeline">
      {steps.map((e) => (
        <div
          key={e.stepId}
          data-testid={`step-${e.stepId}`}
          className={`flex items-start gap-3 p-3 rounded-lg border text-sm transition-all ${stepColor(e.type)}`}
        >
          <span className="font-mono text-base w-5 shrink-0 mt-0.5">{stepIcon(e.type)}</span>
          <div className="flex flex-col gap-0.5 min-w-0">
            <span className="font-mono text-xs">{e.stepId}</span>
            {e.description && <span className="text-xs opacity-70 truncate">{e.description}</span>}
            {e.message && <span className="text-xs opacity-70">{e.message}</span>}
            {e.actual && e.type === "STEP_FAILED" && (
              <span className="text-xs font-mono mt-1 opacity-80">actual: {e.actual}</span>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
