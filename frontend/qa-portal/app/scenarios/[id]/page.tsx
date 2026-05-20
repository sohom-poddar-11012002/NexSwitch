import { fetchScenarioYaml } from "@/lib/api";
import Link from "next/link";
import { notFound } from "next/navigation";
import { load as parseYaml } from "js-yaml";

// ─── types ────────────────────────────────────────────────────────────────────

interface RawStep {
  type: string;
  channel?: string;
  operation?: string;
  timeout_ms?: number;
  capture_response_as?: string;
  expression?: string;
  description?: string;
  fail_fast?: boolean;
  instruction?: string;
  step_id?: string;
  name?: string;
  value?: string;
  count?: number;
  delay_between_ms?: number;
  parallel?: boolean;
  steps?: RawStep[];
  payload?: Record<string, unknown>;
}

interface RawScenario {
  id: string;
  name?: string;
  description?: string;
  platform?: string;
  project?: string;
  feature?: string;
  channel?: string;
  variables?: Record<string, unknown>;
  steps?: RawStep[];
}

// ─── page ─────────────────────────────────────────────────────────────────────

export default async function ScenarioDetailPage({ params }: { params: { id: string } }) {
  let yaml: string;
  try {
    yaml = await fetchScenarioYaml(params.id);
  } catch {
    notFound();
  }

  const root = parseYaml(yaml) as { scenario?: RawScenario };
  const s = root?.scenario;

  const channelColors: Record<string, string> = {
    ISO8583:      "bg-indigo-500/10 text-indigo-400 border-indigo-500/20",
    REST:         "bg-sky-500/10 text-sky-400 border-sky-500/20",
    KAFKA_ASSERT: "bg-orange-500/10 text-orange-400 border-orange-500/20",
    CHAOS:        "bg-red-500/10 text-red-400 border-red-500/20",
    PLAYWRIGHT:   "bg-emerald-500/10 text-emerald-400 border-emerald-500/20",
  };

  return (
    <div className="max-w-3xl">
      {/* Back */}
      <Link href="/scenarios" className="inline-flex items-center gap-1.5 text-xs text-[var(--muted)] hover:text-[var(--text)] transition-colors mb-6">
        ← Scenarios
      </Link>

      {/* Header */}
      <div className="mb-8">
        <div className="flex items-start gap-3 mb-2">
          <h1 className="text-2xl font-semibold tracking-tight text-[var(--text)]">
            {s?.name ?? params.id}
          </h1>
          {s?.channel && (
            <span className={`mt-1 shrink-0 text-[11px] px-2 py-0.5 rounded border font-mono ${channelColors[s.channel.toUpperCase()] ?? ""}`}>
              {s.channel}
            </span>
          )}
        </div>

        {/* Taxonomy */}
        <div className="flex items-center gap-1.5 text-xs text-[var(--muted)] font-mono mb-4">
          <span>{s?.platform}</span>
          <span className="opacity-40">/</span>
          <span>{s?.project}</span>
          <span className="opacity-40">/</span>
          <span>{s?.feature}</span>
        </div>

        {/* Natural language description */}
        {s?.description && (
          <p className="text-sm text-[var(--muted)] leading-relaxed border-l-2 border-brand/40 pl-3">
            {s.description}
          </p>
        )}
      </div>

      {/* Steps — natural language */}
      {s?.steps && s.steps.length > 0 && (
        <div className="mb-8">
          <h2 className="text-xs font-semibold text-[var(--muted)] uppercase tracking-widest mb-4">
            Steps · {s.steps.length}
          </h2>
          <ol className="space-y-2">
            {s.steps.map((step, i) => (
              <StepRow key={i} index={i + 1} step={step} />
            ))}
          </ol>
        </div>
      )}

      {/* Variables */}
      {s?.variables && Object.keys(s.variables).length > 0 && (
        <div className="mb-8">
          <h2 className="text-xs font-semibold text-[var(--muted)] uppercase tracking-widest mb-3">Variables</h2>
          <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] divide-y divide-[var(--border)]">
            {Object.entries(s.variables).map(([k, v]) => (
              <div key={k} className="flex items-center gap-4 px-4 py-2.5 text-sm">
                <span className="font-mono text-brand text-xs w-36 shrink-0">{k}</span>
                <span className="text-[var(--muted)] font-mono text-xs truncate">{String(v)}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Raw YAML */}
      <div>
        <h2 className="text-xs font-semibold text-[var(--muted)] uppercase tracking-widest mb-3">Raw YAML</h2>
        <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] overflow-hidden">
          <div className="flex items-center justify-between px-4 py-2 border-b border-[var(--border)] bg-[var(--surface-2)]">
            <span className="text-[11px] text-[var(--muted)] font-mono">{params.id}.yml</span>
            <span className="text-[11px] text-[var(--muted)]">{yaml.split("\n").length} lines</span>
          </div>
          <pre className="p-5 text-xs font-mono text-[var(--text)] overflow-x-auto leading-relaxed whitespace-pre">
            {yaml}
          </pre>
        </div>
      </div>
    </div>
  );
}

// ─── step row ─────────────────────────────────────────────────────────────────

function StepRow({ index, step }: { index: number; step: RawStep }) {
  const { label, detail, intent } = describeStep(step);
  const intentColors = {
    send:   "bg-indigo-500/10 border-indigo-500/20 text-indigo-400",
    assert: "bg-emerald-500/10 border-emerald-500/20 text-emerald-400",
    wait:   "bg-amber-500/10 border-amber-500/20 text-amber-400",
    loop:   "bg-sky-500/10 border-sky-500/20 text-sky-400",
    set:    "bg-purple-500/10 border-purple-500/20 text-purple-400",
    other:  "bg-[var(--surface-2)] border-[var(--border)] text-[var(--muted)]",
  } as const;

  return (
    <li className="flex items-start gap-3">
      <span className="shrink-0 w-5 h-5 rounded-full bg-[var(--surface-2)] border border-[var(--border)] text-[10px] text-[var(--muted)] flex items-center justify-center mt-0.5 tabular-nums">
        {index}
      </span>
      <div className="flex-1 rounded-lg border border-[var(--border)] bg-[var(--surface)] px-4 py-3">
        <div className="flex items-center gap-2 mb-1">
          <span className={`text-[10px] px-1.5 py-0.5 rounded border font-mono ${intentColors[intent]}`}>
            {step.type}
          </span>
          <span className="text-sm text-[var(--text)]">{label}</span>
        </div>
        {detail && <p className="text-xs text-[var(--muted)] mt-1 leading-relaxed">{detail}</p>}
        {step.fail_fast && (
          <span className="inline-block mt-1.5 text-[10px] text-red-400 font-mono">⚡ fail fast</span>
        )}
      </div>
    </li>
  );
}

// ─── natural language renderer ────────────────────────────────────────────────

type StepIntent = "send" | "assert" | "wait" | "loop" | "set" | "other";

function describeStep(step: RawStep): { label: string; detail: string | null; intent: StepIntent } {
  switch (step.type) {
    case "send": {
      const ch  = step.channel ?? "ISO8583";
      const op  = step.operation ?? "";
      const ms  = step.timeout_ms;
      const cap = step.capture_response_as;
      const label = `Send ${op} via ${ch}`;
      const parts = [];
      if (ms)  parts.push(`timeout ${ms}ms`);
      if (cap) parts.push(`captured as "${cap}"`);
      if (step.payload && Object.keys(step.payload).length > 0)
        parts.push(`payload: ${Object.keys(step.payload).join(", ")}`);
      return { label, detail: parts.length ? parts.join(" · ") : null, intent: "send" };
    }

    case "assert": {
      const label  = step.description ?? step.expression ?? "Assertion";
      const detail = step.description ? step.expression ?? null : null;
      return { label, detail, intent: "assert" };
    }

    case "wait_for_human": {
      const label = step.instruction ?? "Wait for human confirmation";
      const ms    = step.timeout_ms;
      return { label, detail: ms ? `Timeout: ${ms}ms` : null, intent: "wait" };
    }

    case "loop": {
      const n       = step.count ?? 1;
      const inner   = step.steps?.length ?? 0;
      const delayMs = step.delay_between_ms;
      const par     = step.parallel;
      const label   = `Loop ${n}× — ${inner} inner step${inner !== 1 ? "s" : ""}`;
      const parts   = [];
      if (delayMs) parts.push(`${delayMs}ms between iterations`);
      if (par)     parts.push("parallel");
      return { label, detail: parts.length ? parts.join(" · ") : null, intent: "loop" };
    }

    case "inject_variable": {
      const label = `Set "${step.name}" = ${step.value ?? ""}`;
      return { label, detail: null, intent: "set" };
    }

    default:
      return { label: step.type, detail: null, intent: "other" };
  }
}
