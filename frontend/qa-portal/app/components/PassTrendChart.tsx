"use client";

import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, ReferenceLine,
} from "recharts";

export interface TrendPoint {
  label: string;
  passRate: number;
}

export default function PassTrendChart({ data }: { data: TrendPoint[] }) {
  if (data.length === 0) {
    return <p className="text-sm text-[var(--muted)]">No completed runs yet.</p>;
  }
  return (
    <ResponsiveContainer width="100%" height={160}>
      <LineChart data={data} margin={{ top: 4, right: 8, left: -20, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
        <XAxis dataKey="label" tick={{ fontSize: 10 }} stroke="var(--muted)" />
        <YAxis domain={[0, 100]} tick={{ fontSize: 10 }} stroke="var(--muted)" unit="%" />
        <Tooltip formatter={(v: number) => `${v}%`} />
        <ReferenceLine y={80} stroke="#22c55e" strokeDasharray="4 2" label={{ value: "80%", fontSize: 10 }} />
        <Line type="monotone" dataKey="passRate" stroke="#3b82f6" strokeWidth={2} dot={{ r: 3 }} />
      </LineChart>
    </ResponsiveContainer>
  );
}
