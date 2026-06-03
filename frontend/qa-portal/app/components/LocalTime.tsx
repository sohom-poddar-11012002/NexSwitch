'use client';

export default function LocalTime({ iso }: { iso: string }) {
  return <>{new Date(iso).toLocaleTimeString()}</>;
}
