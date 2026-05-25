import { NextRequest, NextResponse } from 'next/server';

const ORCHESTRATOR_URL = process.env.QA_ORCHESTRATOR_URL ?? 'http://localhost:8700';
const API_KEY = process.env.QA_INTERNAL_API_KEY ?? '';

type Params = { path: string[] };

async function proxy(req: NextRequest, { params }: { params: Promise<Params> }): Promise<NextResponse> {
  const { path } = await params;
  const url = `${ORCHESTRATOR_URL}/recorder/${path.join('/')}${req.nextUrl.search}`;

  const headers = new Headers(req.headers);
  headers.set('X-Internal-Api-Key', API_KEY);
  headers.delete('host');

  const upstream = await fetch(url, {
    method: req.method,
    headers,
    body: req.method !== 'GET' && req.method !== 'HEAD' ? req.body : undefined,
    // @ts-expect-error — Node 18 fetch requires duplex for streaming bodies
    duplex: 'half',
  });

  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: upstream.headers,
  });
}

export { proxy as GET, proxy as POST, proxy as PUT, proxy as DELETE, proxy as PATCH };
