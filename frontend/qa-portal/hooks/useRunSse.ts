"use client";

import { useEffect, useRef, useState } from "react";

export type SseEvent = {
  type: string;
  executionId?: string;
  scenarioId?: string;
  stepId?: string;
  description?: string;
  status?: string;
  instruction?: string;
  expiresAt?: string;
  actual?: string;
  message?: string;
};

export type WaitState = {
  stepId: string;
  instruction: string;
  expiresAt: string;
};

// LEARN: EventSource — browser-native SSE client. Unlike WebSockets it is HTTP/1.1 compatible,
//        auto-reconnects on disconnect, and is unidirectional (server → client only), which is
//        exactly right for streaming live run progress to multiple portal tabs.
export function useRunSse(executionId: string | null) {
  const [events, setEvents] = useState<SseEvent[]>([]);
  const [waiting, setWaiting] = useState<WaitState | null>(null);
  const [done, setDone] = useState(false);
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!executionId) return;

    const es = new EventSource(`/api/qa/runs/${executionId}/stream`);
    esRef.current = es;

    es.onmessage = (e) => {
      try {
        const payload: SseEvent = JSON.parse(e.data);
        setEvents((prev) => [...prev, payload]);

        if (payload.type === "WAITING_FOR_HUMAN") {
          setWaiting({ stepId: payload.stepId!, instruction: payload.instruction!, expiresAt: payload.expiresAt! });
        }
        if (payload.type === "RUN_PASSED" || payload.type === "RUN_FAILED") {
          setDone(true);
          setWaiting(null);
          es.close();
        }
        if (payload.type === "STEP_PASSED" || payload.type === "STEP_FAILED") {
          setWaiting((w) => (w?.stepId === payload.stepId ? null : w));
        }
      } catch {
        // ignore malformed events
      }
    };

    es.onerror = () => {
      if (done) es.close();
    };

    return () => es.close();
  }, [executionId, done]);

  return { events, waiting, done };
}
