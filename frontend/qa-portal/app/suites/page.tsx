import { fetchSuites, triggerSuite } from "@/lib/api";
import type { TestSuite } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function SuitesPage() {
  const suites = await fetchSuites();

  return (
    <main className="p-6 max-w-5xl mx-auto">
      <h1 className="text-2xl font-bold mb-6">Test Suites</h1>

      {suites.length === 0 && (
        <p className="text-gray-500">No suites defined. Add a suite YAML under resources/suites/.</p>
      )}

      <div className="space-y-4">
        {suites.map((suite) => (
          <SuiteCard key={suite.id} suite={suite} />
        ))}
      </div>
    </main>
  );
}

function SuiteCard({ suite }: { suite: TestSuite }) {
  return (
    <div className="border rounded-lg p-5 bg-white shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <h2 className="text-lg font-semibold truncate">{suite.name}</h2>
            <span className="text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-600 shrink-0">
              {suite.mode}
            </span>
            {suite.schedule && (
              <span
                className="text-xs px-2 py-0.5 rounded-full bg-blue-50 text-blue-700 font-mono shrink-0"
                title={`Cron: ${suite.schedule}`}
              >
                ⏱ {suite.schedule}
              </span>
            )}
          </div>

          <p className="text-sm text-gray-500 mb-2">
            {suite.runIds.length} run{suite.runIds.length !== 1 ? "s" : ""} · on failure:{" "}
            <span className="font-medium">{suite.onFailure}</span>
            {suite.parallelism > 1 && ` · parallelism: ${suite.parallelism}`}
          </p>

          <div className="flex flex-wrap gap-1">
            {suite.runIds.map((runId) => (
              <span
                key={runId}
                className="text-xs bg-gray-50 border rounded px-2 py-0.5 font-mono text-gray-700"
              >
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
        className="shrink-0 bg-indigo-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-indigo-700 transition-colors"
      >
        Run Suite
      </button>
    </form>
  );
}
