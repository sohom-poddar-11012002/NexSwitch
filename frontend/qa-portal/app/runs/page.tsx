import { fetchRuns, fetchExecutions, type TestRun, type RunExecution } from "@/lib/api";
import RunTriggerForm from "@/app/components/RunTriggerForm";
import Link from "next/link";

export default async function RunsPage() {
  let runs: TestRun[] = [];
  let executions: RunExecution[] = [];

  try { runs = await fetchRuns(); } catch { /* qa-orchestrator not up */ }
  try { executions = await fetchExecutions(); } catch { /* no executions yet */ }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight text-[var(--text)]">Runs</h1>
        <p className="text-sm text-[var(--muted)] mt-1">Trigger a test run and monitor its live execution.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-8">
        <div className="lg:col-span-2">
          <p className="text-xs font-semibold text-[var(--muted)] uppercase tracking-widest mb-3">Trigger</p>
          <RunTriggerForm runs={runs} />
        </div>

        <div className="lg:col-span-3">
          <p className="text-xs font-semibold text-[var(--muted)] uppercase tracking-widest mb-3">
            Recent Executions
            {executions.length > 0 && (
              <span className="ml-2 normal-case font-normal">{executions.length} total</span>
            )}
          </p>
          {executions.length === 0 ? (
            <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-8 text-center">
              <p className="text-sm text-[var(--muted)]">No executions yet. Trigger a run to get started.</p>
            </div>
          ) : (
            <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] overflow-hidden">
              {executions.map((e, i) => (
                <Link
                  key={e.id}
                  href={`/live/${e.id}`}
                  data-testid="execution-row"
                  className={`flex items-center justify-between px-4 py-3 hover:bg-[var(--surface-2)] transition-colors ${
                    i < executions.length - 1 ? "border-b border-[var(--border)]" : ""
                  }`}
                >
                  <div className="flex flex-col gap-0.5">
                    <span className="text-sm font-medium text-[var(--text)]">{e.runId}</span>
                    <span className="text-[11px] text-[var(--muted)] font-mono">{e.id.slice(0, 8)}</span>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="text-xs text-[var(--muted)]">
                      {new Date(e.startedAt).toLocaleTimeString()}
                    </span>
                    <StatusBadge status={e.status} />
                  </div>
                </Link>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    PASSED:    "bg-emerald-500/10 text-emerald-400 border-emerald-500/20",
    FAILED:    "bg-red-500/10 text-red-400 border-red-500/20",
    RUNNING:   "bg-blue-500/10 text-blue-400 border-blue-500/20 animate-pulse",
    PENDING:   "bg-zinc-700/50 text-zinc-400 border-zinc-700",
    CANCELLED: "bg-zinc-700/50 text-zinc-400 border-zinc-700",
  };
  return (
    <span className={`text-[11px] px-2 py-0.5 rounded border font-mono ${styles[status] ?? "bg-zinc-700/50 text-zinc-400 border-zinc-700"}`}>
      {status}
    </span>
  );
}
