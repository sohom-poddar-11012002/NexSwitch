"use client";

import { useState, useRef } from "react";
import { getProxyStatus, startProxy, stopProxy, importHar, fetchRecordings } from "@/lib/api";
import type { ProxyStatus, Recording } from "@/lib/api";

interface Props {
  initialStatus: ProxyStatus;
  initialRecordings: Recording[];
}

export default function RecorderPanel({ initialStatus, initialRecordings }: Props) {
  const [status, setStatus]               = useState<ProxyStatus>(initialStatus);
  const [recordings, setRecordings]       = useState<Recording[]>(initialRecordings);
  const [harYaml, setHarYaml]             = useState<string | null>(null);
  const [harScenarioId, setHarScenarioId] = useState("");
  const [loading, setLoading]             = useState(false);
  const [error, setError]                 = useState<string | null>(null);
  const fileRef                           = useRef<HTMLInputElement>(null);

  async function handleStartProxy() {
    setLoading(true); setError(null);
    try { setStatus(await startProxy()); } catch (e) { setError(String(e)); } finally { setLoading(false); }
  }

  async function handleStopProxy() {
    setLoading(true);
    try { await stopProxy(); setStatus(await getProxyStatus()); } finally { setLoading(false); }
  }

  async function handleRefreshRecordings() {
    setRecordings(await fetchRecordings());
  }

  async function handleImportHar() {
    const file = fileRef.current?.files?.[0];
    if (!file) return;
    setLoading(true); setError(null);
    try { setHarYaml((await importHar(file, harScenarioId || undefined)).yaml); } catch (e) { setError(String(e)); } finally { setLoading(false); }
  }

  return (
    <div className="space-y-4 max-w-2xl">
      {/* Proxy control */}
      <Card title="ISO 8583 Recorder Proxy">
        <div className="flex items-center gap-2.5 mb-4">
          <span className={`w-2 h-2 rounded-full shrink-0 ${status.running ? "bg-emerald-500" : "bg-[var(--border)]"}`} />
          <span className="text-sm text-[var(--muted)]">
            {status.running ? `Running · ${status.recordingCount ?? 0} captured` : "Stopped"}
          </span>
          {!status.enabled && (
            <span className="text-[11px] px-2 py-0.5 rounded border border-amber-500/20 bg-amber-500/5 text-amber-400">
              qa.recorder.proxy.enabled=true required
            </span>
          )}
        </div>
        <div className="flex gap-2">
          <Btn onClick={handleStartProxy} disabled={loading || status.running} variant="success" data-testid="proxy-start-btn">Start</Btn>
          <Btn onClick={handleStopProxy} disabled={loading || !status.running} variant="danger" data-testid="proxy-stop-btn">Stop</Btn>
          <Btn onClick={handleRefreshRecordings} variant="outline" data-testid="proxy-refresh-btn">Refresh</Btn>
        </div>
        {error && <p className="mt-3 text-sm text-red-400">{error}</p>}
      </Card>

      {/* Recordings */}
      {recordings.length > 0 && (
        <Card title="Recorded Sessions">
          <div className="divide-y divide-[var(--border)]">
            {recordings.map((r) => (
              <div key={r.filename} className="flex items-center justify-between py-2.5">
                <span className="text-sm font-mono text-[var(--text)]">{r.filename}</span>
                <span className="text-xs text-[var(--muted)] tabular-nums">{(r.sizeBytes / 1024).toFixed(1)} KB</span>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* HAR import */}
      <Card title="Import HAR File">
        <div className="space-y-4">
          <div>
            <label className="block text-xs text-[var(--muted)] mb-1.5">Scenario ID (optional)</label>
            <input
              data-testid="har-scenario-id-input"
              type="text"
              value={harScenarioId}
              onChange={(e) => setHarScenarioId(e.target.value)}
              placeholder="my-payment-flow"
              className="w-full max-w-xs rounded-md border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm text-[var(--text)] placeholder:text-[var(--muted)] focus:outline-none focus:border-brand transition-colors"
            />
          </div>
          <div>
            <label className="block text-xs text-[var(--muted)] mb-1.5">HAR File</label>
            <input
              data-testid="har-file-input"
              type="file"
              accept=".har"
              ref={fileRef}
              className="text-sm text-[var(--muted)] file:mr-3 file:text-xs file:px-3 file:py-1.5 file:rounded-md file:border file:border-[var(--border)] file:bg-[var(--surface-2)] file:text-[var(--text)] file:cursor-pointer"
            />
          </div>
          <Btn onClick={handleImportHar} disabled={loading} data-testid="har-import-btn">Import</Btn>
        </div>

        {harYaml && (
          <div className="mt-5 pt-5 border-t border-[var(--border)]">
            <p className="text-xs font-medium text-[var(--muted)] mb-2 uppercase tracking-wider">Generated YAML</p>
            <pre className="rounded-md border border-[var(--border)] bg-[var(--surface-2)] p-4 text-xs text-[var(--text)] overflow-x-auto font-mono leading-relaxed">
              {harYaml}
            </pre>
          </div>
        )}
      </Card>
    </div>
  );
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-5">
      <h2 className="text-sm font-semibold text-[var(--text)] mb-4">{title}</h2>
      {children}
    </div>
  );
}

type BtnVariant = "default" | "success" | "danger" | "outline";

function Btn({
  children,
  onClick,
  disabled,
  variant = "default",
  ...rest
}: {
  children: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  variant?: BtnVariant;
  [key: string]: unknown;
}) {
  const styles: Record<BtnVariant, string> = {
    default: "bg-brand hover:bg-brand-dark text-white",
    success: "bg-emerald-600 hover:bg-emerald-700 text-white",
    danger:  "bg-red-600 hover:bg-red-700 text-white",
    outline: "border border-[var(--border)] bg-[var(--surface-2)] text-[var(--text)] hover:bg-[var(--border)]",
  };
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`px-3.5 py-1.5 rounded-md text-sm font-medium transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${styles[variant]}`}
      {...rest}
    >
      {children}
    </button>
  );
}
