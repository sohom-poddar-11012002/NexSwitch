import { fetchExecutions, type RunExecution } from "@/lib/api";
import PassTrendChart, { type TrendPoint } from "@/app/components/PassTrendChart";

export default async function ReportsPage() {
  let executions: RunExecution[] = [];
  try { executions = await fetchExecutions(); } catch { /* service not up */ }

  const total    = executions.length;
  const passed   = executions.filter((e) => e.status === "PASSED").length;
  const failed   = executions.filter((e) => e.status === "FAILED").length;
  const passRate = total > 0 ? Math.round((passed / total) * 100) : null;

  const trendData: TrendPoint[] = executions
    .filter((e) => e.completedAt)
    .slice(-10)
    .map((e, i, arr) => {
      const windowPassed = arr.slice(0, i + 1).filter((x) => x.status === "PASSED").length;
      return { label: `#${total - arr.length + i + 1}`, passRate: Math.round((windowPassed / (i + 1)) * 100) };
    });

  const withDuration = executions
    .filter((e) => e.completedAt)
    .map((e) => ({
      ...e,
      durationMs: new Date(e.completedAt!).getTime() - new Date(e.startedAt).getTime(),
    }))
    .sort((a, b) => b.durationMs - a.durationMs)
    .slice(0, 5);

  interface StepStat { stepId: string; p50: number; p95: number; count: number }
  const stepElapsed: Record<string, number[]> = {};
  for (const exec of executions) {
    for (const se of exec.scenarioExecutions ?? []) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      for (const step of (se as any).stepExecutions ?? []) {
        if (step.elapsedMs != null) (stepElapsed[step.stepId] ??= []).push(step.elapsedMs);
      }
    }
  }
  const stepStats: StepStat[] = Object.entries(stepElapsed)
    .map(([stepId, times]) => {
      const sorted = [...times].sort((a, b) => a - b);
      return { stepId, p50: sorted[Math.floor(sorted.length * 0.5)] ?? 0, p95: sorted[Math.floor(sorted.length * 0.95)] ?? 0, count: times.length };
    })
    .sort((a, b) => b.p95 - a.p95)
    .slice(0, 5);

  const failCounts: Record<string, number> = {};
  for (const exec of executions) {
    for (const se of exec.scenarioExecutions ?? []) {
      if (se.status === "FAILED") failCounts[se.scenarioId] = (failCounts[se.scenarioId] ?? 0) + 1;
    }
  }
  const flakyScenarios = Object.entries(failCounts).sort((a, b) => b[1] - a[1]).slice(0, 5);

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight text-[var(--text)]">Reports</h1>
        <p className="text-sm text-[var(--muted)] mt-1">Execution history, pass rates, and performance metrics.</p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-8">
        <StatCard label="Total Runs" value={String(total)} />
        <StatCard label="Passed" value={String(passed)} color="text-emerald-400" />
        <StatCard label="Failed" value={String(failed)} color="text-red-400" />
        <StatCard
          label="Pass Rate"
          value={passRate !== null ? `${passRate}%` : "—"}
          color={passRate !== null ? (passRate >= 80 ? "text-emerald-400" : passRate >= 50 ? "text-amber-400" : "text-red-400") : undefined}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        {/* Trend chart */}
        <Section title="Pass Rate Trend" subtitle="Last 10 completed runs">
          {trendData.length === 0
            ? <Empty />
            : <div className="h-48"><PassTrendChart data={trendData} /></div>
          }
        </Section>

        {/* Slowest runs */}
        <Section title="Slowest Runs" subtitle="By total duration">
          {withDuration.length === 0 ? <Empty /> : (
            <table className="w-full text-sm">
              <tbody className="divide-y divide-[var(--border)]">
                {withDuration.map((e) => (
                  <tr key={e.id} className="flex items-center justify-between py-2">
                    <td className="text-[var(--muted)] font-mono text-[11px]">{e.runId}</td>
                    <td className="text-[var(--text)] tabular-nums text-xs">{(e.durationMs / 1000).toFixed(1)}s</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Section>

        {/* Step latency */}
        <Section title="Slowest Steps" subtitle="p50 / p95 latency">
          {stepStats.length === 0 ? <Empty /> : (
            <table className="w-full text-sm">
              <tbody className="divide-y divide-[var(--border)]">
                {stepStats.map((s) => (
                  <tr key={s.stepId} className="flex items-center justify-between py-2 gap-4">
                    <td className="text-[var(--text)] font-mono text-[11px] truncate">{s.stepId}</td>
                    <td className="text-[var(--muted)] text-xs shrink-0 tabular-nums">
                      {s.p50}ms · {s.p95}ms
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Section>

        {/* Flaky scenarios */}
        <Section title="Most Failed" subtitle="Scenarios by failure count">
          {flakyScenarios.length === 0 ? <Empty /> : (
            <table className="w-full text-sm">
              <tbody className="divide-y divide-[var(--border)]">
                {flakyScenarios.map(([id, count]) => (
                  <tr key={id} className="flex items-center justify-between py-2">
                    <td className="text-[var(--text)] font-mono text-[11px] truncate">{id}</td>
                    <td className="text-red-400 text-xs shrink-0">{count}×</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Section>
      </div>
    </div>
  );
}

function StatCard({ label, value, color = "text-[var(--text)]" }: { label: string; value: string; color?: string }) {
  const testId = `stat-${label.toLowerCase().replace(/\s+/g, "-")}`;
  return (
    <div data-testid={testId} className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-4">
      <p className="text-xs text-[var(--muted)] mb-1">{label}</p>
      <p className={`text-3xl font-semibold tabular-nums tracking-tight ${color}`}>{value}</p>
    </div>
  );
}

function Section({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-5">
      <div className="mb-4">
        <p className="text-sm font-medium text-[var(--text)]">{title}</p>
        {subtitle && <p className="text-xs text-[var(--muted)] mt-0.5">{subtitle}</p>}
      </div>
      {children}
    </div>
  );
}

function Empty() {
  return <p className="text-sm text-[var(--muted)] py-4 text-center">No data yet.</p>;
}
