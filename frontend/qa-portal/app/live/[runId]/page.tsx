import LiveRunView from "@/app/components/LiveRunView";

export default function LivePage({ params }: { params: { runId: string } }) {
  return <LiveRunView executionId={params.runId} />;
}
