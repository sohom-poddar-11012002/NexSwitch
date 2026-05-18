import { fetchExecutions, type RunExecution } from "@/lib/api";

export default async function ReportsPage() {
  let executions: RunExecution[] = [];
  try { executions = await fetchExecutions(); } catch { /* service not up */ }

  const total  = executions.length;
  const passed = executions.filter((e) => e.status === "PASSED").length;
  const failed = executions.filter((e) => e.status === "FAILED").length;
  const passRate = total > 0 ? Math.round((passed / total) * 100) : null;

  // Slowest runs by elapsed time
  const withDuration = executions
    .filter((e) => e.completedAt)
    .map((e) => ({
      ...e,
      durationMs: new Date(e.completedAt!).getTime() - new Date(e.startedAt).getTime(),
    }))
    .sort((a, b) => b.durationMs - a.durationMs)
    .slice(0, 5);

  // Most-failed scenarios
  const failCounts: Record<string, number> = {};
  for (const exec of executions) {
    for (const se of exec.scenarioExecutions ?? []) {
      if (se.status === "FAILED") failCounts[se.scenarioId] = (failCounts[se.scenarioId] ?? 0) + 1;
    }
  }
  const flakyScenarios = Object.entries(failCounts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5);

  return (
    <div className="max-w-2xl">
      <h1 className="text-xl font-semibold mb-6">Reports</h1>

      {/* Pass Rate */}
      <section className="mb-8">
        <h2 className="text-sm font-medium text-[var(--muted)] uppercase tracking-wider mb-3">Overall Pass Rate</h2>
        <div className="flex items-center gap-6 p-4 rounded-lg border border-[var(--border)] bg-[var(--surface)]">
          <Stat label="Total Runs" value={String(total)} />
          <Stat label="Passed" value={String(passed)} color="text-pass" />
          <Stat label="Failed" value={String(failed)} color="text-fail" />
          {passRate !== null && (
            <div className="ml-auto">
              <PassRateBar pct={passRate} />
            </div>
          )}
          {passRate === null && <span className="text-sm text-[var(--muted)] ml-auto">No data yet</span>}
        </div>
      </section>

      {/* Slowest runs */}
      <section className="mb-8">
        <h2 className="text-sm font-medium text-[var(--muted)] uppercase tracking-wider mb-3">Slowest Runs</h2>
        {withDuration.length === 0
          ? <p className="text-sm text-[var(--muted)]">No completed runs yet.</p>
          : (
            <div className="flex flex-col gap-1">
              {withDuration.map((e) => (
                <div key={e.id} className="flex items-center justify-between p-2 rounded border border-[var(--border)] text-sm">
                  <span className="font-mono text-xs">{e.runId}</span>
                  <span className="text-[var(--muted)] text-xs">{(e.durationMs / 1000).toFixed(1)}s</span>
                </div>
              ))}
            </div>
          )}
      </section>

      {/* Flaky scenarios */}
      <section>
        <h2 className="text-sm font-medium text-[var(--muted)] uppercase tracking-wider mb-3">Most Failed Scenarios</h2>
        {flakyScenarios.length === 0
          ? <p className="text-sm text-[var(--muted)]">No failures recorded yet.</p>
          : (
            <div className="flex flex-col gap-1">
              {flakyScenarios.map(([id, count]) => (
                <div key={id} className="flex items-center justify-between p-2 rounded border border-[var(--border)] text-sm">
                  <span className="font-mono text-xs">{id}</span>
                  <span className="text-fail text-xs">{count} failure{count !== 1 ? "s" : ""}</span>
                </div>
              ))}
            </div>
          )}
      </section>
    </div>
  );
}

function Stat({ label, value, color = "text-[var(--text)]" }: { label: string; value: string; color?: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-[var(--muted)]">{label}</span>
      <span className={`text-2xl font-bold tabular-nums ${color}`}>{value}</span>
    </div>
  );
}

function PassRateBar({ pct }: { pct: number }) {
  const color = pct >= 80 ? "bg-pass" : pct >= 50 ? "bg-waiting" : "bg-fail";
  return (
    <div className="flex flex-col items-end gap-1">
      <span className="text-sm font-bold">{pct}%</span>
      <div className="w-24 h-2 rounded bg-[var(--bg)] overflow-hidden">
        <div className={`h-full rounded ${color}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
