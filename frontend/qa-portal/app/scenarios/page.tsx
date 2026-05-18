import { fetchScenarios, type TestScenario } from "@/lib/api";

export default async function ScenariosPage() {
  let scenarios: TestScenario[] = [];
  let error: string | null = null;

  try {
    scenarios = await fetchScenarios();
  } catch (e) {
    error = String(e);
  }

  // Group by platform → project → feature
  const tree: Record<string, Record<string, Record<string, TestScenario[]>>> = {};
  for (const s of scenarios) {
    tree[s.platform] ??= {};
    tree[s.platform][s.project] ??= {};
    tree[s.platform][s.project][s.feature] ??= [];
    tree[s.platform][s.project][s.feature].push(s);
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold">Scenarios</h1>
        <span className="text-sm text-[var(--muted)]">{scenarios.length} loaded</span>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded bg-red-900/40 border border-fail text-sm text-red-200">
          Failed to load scenarios — is qa-orchestrator running on :8700? <br />{error}
        </div>
      )}

      {Object.entries(tree).map(([platform, projects]) => (
        <div key={platform} className="mb-8">
          <h2 className="text-base font-semibold text-brand mb-3">{platform}</h2>
          {Object.entries(projects).map(([project, features]) => (
            <div key={project} className="ml-4 mb-4">
              <h3 className="text-sm font-medium text-[var(--muted)] uppercase tracking-wider mb-2">{project}</h3>
              {Object.entries(features).map(([feature, list]) => (
                <div key={feature} className="ml-4 mb-3">
                  <h4 className="text-xs font-medium text-[var(--muted)] mb-2">/{feature}</h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-2">
                    {list.map((s) => (
                      <ScenarioCard key={s.id} scenario={s} />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

function ScenarioCard({ scenario }: { scenario: TestScenario }) {
  return (
    <div
      data-testid="scenario-card"
      className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-3 flex flex-col gap-1 hover:border-brand transition-colors"
    >
      <div className="flex items-start justify-between gap-2">
        <span className="text-sm font-medium">{scenario.name}</span>
        <ChannelBadge channel={scenario.primaryChannel} />
      </div>
      {scenario.description && (
        <p className="text-xs text-[var(--muted)] line-clamp-2">{scenario.description}</p>
      )}
      <span className="text-xs text-[var(--muted)] mt-1 font-mono">{scenario.id}</span>
    </div>
  );
}

function ChannelBadge({ channel }: { channel: string }) {
  const colors: Record<string, string> = {
    ISO8583: "bg-indigo-900/60 text-indigo-300",
    REST: "bg-sky-900/60 text-sky-300",
    KAFKA_ASSERT: "bg-orange-900/60 text-orange-300",
    CHAOS: "bg-red-900/60 text-red-300",
    PLAYWRIGHT: "bg-green-900/60 text-green-300",
  };
  return (
    <span className={`text-xs px-1.5 py-0.5 rounded font-mono shrink-0 ${colors[channel] ?? "bg-slate-800 text-slate-300"}`}>
      {channel}
    </span>
  );
}
