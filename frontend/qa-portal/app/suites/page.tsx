import { fetchSuites, triggerSuite } from "@/lib/api";
import type { TestSuite } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function SuitesPage() {
  let suites: TestSuite[] = [];
  try { suites = await fetchSuites(); } catch { /* service not up */ }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight text-[var(--text)]">Suites</h1>
        <p className="text-sm text-[var(--muted)] mt-1">Collections of runs executed together with shared scheduling.</p>
      </div>

      {suites.length === 0 ? (
        <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-12 text-center">
          <p className="text-sm text-[var(--muted)]">No suites defined. Add a YAML file under resources/suites/.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {suites.map((suite) => <SuiteCard key={suite.id} suite={suite} />)}
        </div>
      )}
    </div>
  );
}

function SuiteCard({ suite }: { suite: TestSuite }) {
  return (
    <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-5">
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1.5 flex-wrap">
            <h2 className="text-base font-semibold text-[var(--text)] truncate">{suite.name}</h2>
            <span className="text-[11px] px-2 py-0.5 rounded-full border border-[var(--border)] bg-[var(--surface-2)] text-[var(--muted)] font-mono">
              {suite.mode}
            </span>
            {suite.schedule && (
              <span className="text-[11px] px-2 py-0.5 rounded-full border border-brand/30 bg-brand/5 text-brand font-mono">
                {suite.schedule}
              </span>
            )}
          </div>

          <p className="text-sm text-[var(--muted)] mb-3">
            {suite.runIds.length} run{suite.runIds.length !== 1 ? "s" : ""}
            <span className="mx-1.5 opacity-40">·</span>
            on failure: <span className="text-[var(--text)]">{suite.onFailure}</span>
            {suite.parallelism > 1 && (
              <><span className="mx-1.5 opacity-40">·</span>parallelism: <span className="text-[var(--text)]">{suite.parallelism}</span></>
            )}
          </p>

          <div className="flex flex-wrap gap-1.5">
            {suite.runIds.map((runId) => (
              <span key={runId} className="text-[11px] font-mono px-2 py-0.5 rounded border border-[var(--border)] bg-[var(--surface-2)] text-[var(--muted)]">
                {runId}
              </span>
            ))}
          </div>
        </div>

        <TriggerSuiteButton suiteId={suite.id} />
      </div>
    </div>
  );
}

function TriggerSuiteButton({ suiteId }: { suiteId: string }) {
  return (
    <form
      action={async () => {
        "use server";
        await triggerSuite(suiteId);
      }}
    >
      <button
        type="submit"
        data-testid="suite-trigger-btn"
        className="shrink-0 bg-brand hover:bg-brand-dark text-white text-sm font-medium px-4 py-2 rounded-md transition-colors"
      >
        Run Suite
      </button>
    </form>
  );
}
