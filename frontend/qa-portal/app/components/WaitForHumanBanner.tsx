"use client";

import { useState } from "react";
import { resumeStep } from "@/lib/api";

interface Props {
  executionId: string;
  stepId: string;
  instruction: string;
  expiresAt: string;
}

export default function WaitForHumanBanner({ executionId, stepId, instruction, expiresAt }: Props) {
  const [submitting, setSubmitting] = useState<"PASS" | "FAIL" | null>(null);
  const [done, setDone] = useState(false);

  async function handle(outcome: "PASS" | "FAIL") {
    setSubmitting(outcome);
    await resumeStep(executionId, stepId, outcome);
    setDone(true);
  }

  if (done) return null;

  const expiresDate = new Date(expiresAt);
  const expiresStr = expiresDate.toLocaleTimeString();

  return (
    <div
      data-testid="wait-for-human-banner"
      className="mb-4 p-4 rounded-lg border border-waiting bg-yellow-950/40 flex flex-col gap-3"
    >
      <div className="flex items-start gap-2">
        <span className="text-waiting text-lg shrink-0">⏸</span>
        <div>
          <p className="text-sm font-medium text-waiting">Human action required</p>
          <p className="text-sm mt-1">{instruction}</p>
          <p className="text-xs text-[var(--muted)] mt-1">Expires at {expiresStr}</p>
        </div>
      </div>
      <div className="flex gap-2">
        <button
          data-testid="resume-pass-button"
          onClick={() => handle("PASS")}
          disabled={!!submitting}
          className="px-4 py-1.5 bg-pass/20 hover:bg-pass/30 border border-pass text-pass rounded text-sm font-medium disabled:opacity-50 transition-colors"
        >
          {submitting === "PASS" ? "Sending…" : "Continue"}
        </button>
        <button
          data-testid="resume-fail-button"
          onClick={() => handle("FAIL")}
          disabled={!!submitting}
          className="px-4 py-1.5 bg-fail/20 hover:bg-fail/30 border border-fail text-fail rounded text-sm font-medium disabled:opacity-50 transition-colors"
        >
          {submitting === "FAIL" ? "Sending…" : "Mark as Failed"}
        </button>
      </div>
    </div>
  );
}
