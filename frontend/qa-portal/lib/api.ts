const BASE = process.env.NEXT_PUBLIC_QA_API ?? "/api/qa";

export type ChannelType = "ISO8583" | "REST" | "KAFKA_ASSERT" | "CHAOS" | "PLAYWRIGHT";
export type ExecutionStatus = "PENDING" | "RUNNING" | "PASSED" | "FAILED" | "CANCELLED";
export type SessionMode = "STATEFUL" | "STATELESS";

export interface TestScenario {
  id: string;
  name: string;
  description: string;
  platform: string;
  project: string;
  feature: string;
  primaryChannel: ChannelType;
  yamlPath: string;
}

export interface SessionConfig {
  mode: SessionMode;
  carryVariables: string[];
  isolateOnFailure: boolean;
}

export interface RunScenarioRef {
  scenarioId: string;
  variableOverrides: Record<string, unknown>;
}

export interface TestRun {
  id: string;
  name: string;
  scenarios: RunScenarioRef[];
  runVariables: Record<string, unknown>;
  session: SessionConfig;
}

export interface TestSuite {
  id: string;
  name: string;
  runIds: string[];
  mode: "SEQUENTIAL" | "PARALLEL";
  parallelism: number;
  onFailure: "CONTINUE" | "FAIL_FAST" | "RETRY_ONCE";
  schedule: string | null;
}

export interface StepExecution {
  stepId: string;
  stepIndex: number;
  result: { type: string; message?: string; actual?: string; elapsedMs?: number };
  completedAt: string;
}

export interface ScenarioExecution {
  scenarioId: string;
  stepExecutions: StepExecution[];
  status: ExecutionStatus;
}

export interface RunExecution {
  id: string;
  runId: string;
  status: ExecutionStatus;
  scenarioExecutions: ScenarioExecution[];
  startedAt: string;
  completedAt: string | null;
}

export async function fetchScenarios(params?: { platform?: string; project?: string; feature?: string }): Promise<TestScenario[]> {
  const qs = params ? "?" + new URLSearchParams(Object.entries(params).filter(([, v]) => v) as [string, string][]).toString() : "";
  const res = await fetch(`${BASE}/scenarios${qs}`, { cache: "no-store" });
  if (!res.ok) throw new Error(`GET /scenarios failed: ${res.status}`);
  return res.json();
}

export async function fetchRuns(): Promise<TestRun[]> {
  const res = await fetch(`${BASE}/runs`, { cache: "no-store" });
  if (!res.ok) throw new Error(`GET /runs failed: ${res.status}`);
  return res.json();
}

export async function fetchExecutions(): Promise<RunExecution[]> {
  const res = await fetch(`${BASE}/runs`, { cache: "no-store" });
  if (!res.ok) throw new Error(`GET /runs failed: ${res.status}`);
  return res.json();
}

export async function fetchExecution(id: string): Promise<RunExecution> {
  const res = await fetch(`${BASE}/runs/${id}`, { cache: "no-store" });
  if (!res.ok) throw new Error(`GET /runs/${id} failed: ${res.status}`);
  return res.json();
}

export async function triggerRun(runId: string, variableOverrides: Record<string, string> = {}): Promise<{ executionId: string }> {
  const res = await fetch(`${BASE}/runs/trigger`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ runId, variableOverrides }),
  });
  if (!res.ok) throw new Error(`POST /runs/trigger failed: ${res.status}`);
  return res.json();
}

export async function resumeStep(executionId: string, stepId: string, outcome: "PASS" | "FAIL"): Promise<void> {
  await fetch(`${BASE}/runs/${executionId}/steps/${stepId}/resume`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ outcome }),
  });
}
