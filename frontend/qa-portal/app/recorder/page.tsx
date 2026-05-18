import RecorderPanel from "@/app/components/RecorderPanel";
import { getProxyStatus, fetchRecordings } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function RecorderPage() {
  const [status, recordings] = await Promise.all([getProxyStatus(), fetchRecordings()]);

  return (
    <main className="p-6 max-w-4xl mx-auto">
      <h1 className="text-2xl font-bold mb-2">Recorder</h1>
      <p className="text-sm text-gray-500 mb-6">
        Capture real ISO 8583 sessions as replayable scenarios, or import from a browser HAR export.
      </p>
      <RecorderPanel initialStatus={status} initialRecordings={recordings} />
    </main>
  );
}
