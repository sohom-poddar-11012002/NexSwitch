"use client";

import { useState, useRef } from "react";
import { getProxyStatus, startProxy, stopProxy, importHar, fetchRecordings } from "@/lib/api";
import type { ProxyStatus, Recording } from "@/lib/api";

interface Props {
  initialStatus: ProxyStatus;
  initialRecordings: Recording[];
}

export default function RecorderPanel({ initialStatus, initialRecordings }: Props) {
  const [status, setStatus]           = useState<ProxyStatus>(initialStatus);
  const [recordings, setRecordings]   = useState<Recording[]>(initialRecordings);
  const [harYaml, setHarYaml]         = useState<string | null>(null);
  const [harScenarioId, setHarScenarioId] = useState("");
  const [loading, setLoading]         = useState(false);
  const [error, setError]             = useState<string | null>(null);
  const fileRef                       = useRef<HTMLInputElement>(null);

  async function handleStartProxy() {
    setLoading(true);
    setError(null);
    try {
      const s = await startProxy();
      setStatus(s);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }

  async function handleStopProxy() {
    setLoading(true);
    try {
      await stopProxy();
      const s = await getProxyStatus();
      setStatus(s);
    } finally {
      setLoading(false);
    }
  }

  async function handleRefreshRecordings() {
    const recs = await fetchRecordings();
    setRecordings(recs);
  }

  async function handleImportHar() {
    const file = fileRef.current?.files?.[0];
    if (!file) return;
    setLoading(true);
    setError(null);
    try {
      const result = await importHar(file, harScenarioId || undefined);
      setHarYaml(result.yaml);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* Proxy status */}
      <section className="border rounded-lg p-5 bg-white shadow-sm">
        <h2 className="text-lg font-semibold mb-3">ISO 8583 Recorder Proxy</h2>
        <div className="flex items-center gap-3 mb-4">
          <span
            className={`w-2.5 h-2.5 rounded-full ${status.running ? "bg-green-500" : "bg-gray-300"}`}
          />
          <span className="text-sm text-gray-700">
            {status.running ? `Running · ${status.recordingCount} captured` : "Stopped"}
          </span>
          {!status.enabled && (
            <span className="text-xs bg-yellow-50 text-yellow-700 px-2 py-0.5 rounded">
              Set qa.recorder.proxy.enabled=true to enable
            </span>
          )}
        </div>
        <div className="flex gap-2">
          <button
            data-testid="proxy-start-btn"
            onClick={handleStartProxy}
            disabled={loading || status.running}
            className="bg-green-600 text-white text-sm px-4 py-1.5 rounded hover:bg-green-700 disabled:opacity-50"
          >
            Start
          </button>
          <button
            data-testid="proxy-stop-btn"
            onClick={handleStopProxy}
            disabled={loading || !status.running}
            className="bg-red-600 text-white text-sm px-4 py-1.5 rounded hover:bg-red-700 disabled:opacity-50"
          >
            Stop
          </button>
          <button
            data-testid="proxy-refresh-btn"
            onClick={handleRefreshRecordings}
            className="text-sm px-4 py-1.5 border rounded hover:bg-gray-50"
          >
            Refresh
          </button>
        </div>
        {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      </section>

      {/* Recordings list */}
      {recordings.length > 0 && (
        <section className="border rounded-lg p-5 bg-white shadow-sm">
          <h2 className="text-lg font-semibold mb-3">Recorded Sessions</h2>
          <ul className="divide-y text-sm">
            {recordings.map((r) => (
              <li key={r.filename} className="py-2 flex justify-between items-center">
                <span className="font-mono text-gray-700">{r.filename}</span>
                <span className="text-gray-400 text-xs">{(r.sizeBytes / 1024).toFixed(1)} KB</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* HAR import */}
      <section className="border rounded-lg p-5 bg-white shadow-sm">
        <h2 className="text-lg font-semibold mb-3">Import HAR File</h2>
        <div className="space-y-3">
          <div>
            <label className="block text-sm text-gray-600 mb-1">Scenario ID (optional)</label>
            <input
              data-testid="har-scenario-id-input"
              type="text"
              value={harScenarioId}
              onChange={(e) => setHarScenarioId(e.target.value)}
              placeholder="my-payment-flow"
              className="border rounded px-3 py-1.5 text-sm w-full max-w-sm"
            />
          </div>
          <div>
            <label className="block text-sm text-gray-600 mb-1">HAR File</label>
            <input
              data-testid="har-file-input"
              type="file"
              accept=".har"
              ref={fileRef}
              className="text-sm"
            />
          </div>
          <button
            data-testid="har-import-btn"
            onClick={handleImportHar}
            disabled={loading}
            className="bg-indigo-600 text-white text-sm px-4 py-1.5 rounded hover:bg-indigo-700 disabled:opacity-50"
          >
            Import
          </button>
        </div>

        {harYaml && (
          <div className="mt-4">
            <h3 className="text-sm font-medium text-gray-700 mb-1">Generated Scenario YAML</h3>
            <pre className="bg-gray-50 border rounded p-3 text-xs overflow-x-auto">{harYaml}</pre>
          </div>
        )}
      </section>
    </div>
  );
}
