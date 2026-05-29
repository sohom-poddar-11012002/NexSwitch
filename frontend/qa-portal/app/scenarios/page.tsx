import { fetchScenarios, type TestScenario } from "@/lib/api";
import Link from "next/link";

export default async function ScenariosPage() {
  let scenarios: TestScenario[] = [];
  let error: string | null = null;

  try {
    scenarios = await fetchScenarios();
  } catch (e) {
    error = String(e);
  }

  const tree: Record<string, Record<string, Record<string, TestScenario[]>>> = {};
  for (const s of scenarios) {
    tree[s.platform] ??= {};
    tree[s.platform][s.project] ??= {};
    tree[s.platform][s.project][s.feature] ??= [];
    tree[s.platform][s.project][s.feature].push(s);
  }

  return (
    <div>
      <PageHeader
        title="Scenarios"
        description="All adversarial test scenarios loaded from YAML definitions."
        badge={scenarios.length > 0 ? `${scenarios.length} loaded` : undefined}
      />

      {error && <ErrorBanner message={error} />}

      {scenarios.length === 0 && !error && (
        <EmptyState message="No scenarios found. Add YAML files under resources/scenarios/." />
      )}

      <div className="space-y-8">
        {Object.entries(tree).map(([platform, projects]) => (
          <div key={platform}>
            <h2 className="text-xs font-semibold text-brand uppercase tracking-widest mb-4">{platform}</h2>
            <div className="space-y-5">
              {Object.entries(projects).map(([project, features]) => (
                <div key={project}>
                  <h3 className="text-sm font-medium text-[var(--text)] mb-3 flex items-center gap-2">
                    {project}
                    <span className="h-px flex-1 bg-[var(--border)]" />
                  </h3>
                  <div className="space-y-3">
                    {Object.entries(features).map(([feature, list]) => (
                      <div key={feature}>
                        <p className="text-xs text-[var(--muted)] mb-2 font-mono">/{feature}</p>
                        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-2">
                          {list.map((s) => (
                            <ScenarioCard key={s.id} scenario={s} />
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function ScenarioCard({ scenario }: { scenario: TestScenario }) {
  return (
    <Link
      href={`/scenarios/${scenario.id}`}
      data-testid="scenario-card"
      className="group rounded-lg border border-[var(--border)] bg-[var(--surface)] p-4 flex flex-col gap-2 hover:border-brand/50 hover:bg-[var(--surface-2)] transition-all duration-150"
    >
      <div className="flex items-start justify-between gap-2">
        <span className="text-sm font-medium text-[var(--text)] leading-snug">{scenario.name}</span>
        <ChannelBadge channel={scenario.primaryChannel} />
      </div>
      {scenario.description && (
        <p className="text-xs text-[var(--muted)] line-clamp-2 leading-relaxed">{scenario.description}</p>
      )}
      <p className="text-[11px] text-[var(--muted)] font-mono mt-auto pt-1 border-t border-[var(--border)]">
        {scenario.id}
      </p>
    </Link>
  );
}

function ChannelBadge({ channel }: { channel: string }) {
  const styles: Record<string, string> = {
    ISO8583:      "bg-indigo-500/10 text-indigo-400 border-indigo-500/20",
    REST:         "bg-sky-500/10 text-sky-400 border-sky-500/20",
    KAFKA_ASSERT: "bg-orange-500/10 text-orange-400 border-orange-500/20",
    CHAOS:        "bg-red-500/10 text-red-400 border-red-500/20",
    PLAYWRIGHT:   "bg-emerald-500/10 text-emerald-400 border-emerald-500/20",
  };
  return (
    <span className={`text-[10px] px-1.5 py-0.5 rounded border font-mono shrink-0 ${styles[channel] ?? "bg-zinc-800 text-zinc-400 border-zinc-700"}`}>
      {channel}
    </span>
  );
}

function PageHeader({ title, description, badge }: { title: string; description?: string; badge?: string }) {
  return (
    <div className="mb-8 flex items-start justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-[var(--text)]">{title}</h1>
        {description && <p className="text-sm text-[var(--muted)] mt-1">{description}</p>}
      </div>
      {badge && (
        <span className="shrink-0 text-xs px-2.5 py-1 rounded-full bg-[var(--surface-2)] text-[var(--muted)] border border-[var(--border)]">
          {badge}
        </span>
      )}
    </div>
  );
}

function ErrorBanner({ message }: { message: string }) {
  return (
    <div className="mb-6 flex items-center gap-3 p-3 rounded-lg bg-red-500/5 border border-red-500/20 text-sm text-red-400">
      <span className="shrink-0">⚠</span>
      {message}
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <p className="text-[var(--muted)] text-sm">{message}</p>
    </div>
  );
}
