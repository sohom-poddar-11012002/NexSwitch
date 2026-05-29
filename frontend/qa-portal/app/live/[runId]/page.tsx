import LiveRunView from "@/app/components/LiveRunView";

export default async function LivePage({ params }: { params: Promise<{ runId: string }> }) {
  const { runId } = await params;
  return <LiveRunView executionId={runId} />;
}
