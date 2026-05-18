import { fetchRuns, fetchExecutions, type TestRun, type RunExecution } from "@/lib/api";
import RunTriggerForm from "@/app/components/RunTriggerForm";
import Link from "next/link";

export default async function RunsPage() {
  let runs: TestRun[] = [];
  let executions: RunExecution[] = [];

  try { runs = await fetchRuns(); } catch { /* qa-orchestrator not up */ }
  try { executions = await fetchExecutions(); } catch { /* no executions yet */ }

  return (
    <div className="max-w-3xl">
      <h1 className="text-xl font-semibold mb-6">Runs</h1>

      <section className="mb-8">
        <h2 className="text-sm font-medium text-[var(--muted)] uppercase tracking-wider mb-3">Trigger a Run</h2>
        <RunTriggerForm runs={runs} />
      </section>

      <section>
        <h2 className="text-sm font-medium text-[var(--muted)] uppercase tracking-wider mb-3">Recent Executions</h2>
        {executions.length === 0 ? (
          <p className="text-sm text-[var(--muted)]">No executions yet.</p>
        ) : (
          <div className="flex flex-col gap-2">
            {executions.map((e) => (
              <Link
                key={e.id}
                href={`/live/${e.id}`}
                data-testid="execution-row"
                className="flex items-center justify-between p-3 rounded-lg border border-[var(--border)] bg-[var(--surface)] hover:border-brand transition-colors"
              >
                <div>
                  <span className="text-sm font-mono">{e.runId}</span>
                  <span className="ml-3 text-xs text-[var(--muted)]">{e.id.slice(0, 8)}</span>
                </div>
                <StatusBadge status={e.status} />
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, string> = {
    PASSED: "text-pass",
    FAILED: "text-fail",
    RUNNING: "text-waiting animate-pulse",
    PENDING: "text-[var(--muted)]",
    CANCELLED: "text-[var(--muted)]",
  };
  return <span className={`text-xs font-mono ${map[status] ?? "text-[var(--muted)]"}`}>{status}</span>;
}
