import RecorderPanel from "@/app/components/RecorderPanel";
import { getProxyStatus, fetchRecordings } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function RecorderPage() {
  const [status, recordings] = await Promise.all([getProxyStatus(), fetchRecordings()]);

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight text-[var(--text)]">Recorder</h1>
        <p className="text-sm text-[var(--muted)] mt-1">
          Capture ISO 8583 sessions as replayable scenarios, or import from a browser HAR export.
        </p>
      </div>
      <RecorderPanel initialStatus={status} initialRecordings={recordings} />
    </div>
  );
}
