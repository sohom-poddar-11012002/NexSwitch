import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    const backend = process.env.QA_ORCHESTRATOR_URL ?? "http://localhost:8700";
    return [
      { source: "/api/qa/:path*", destination: `${backend}/api/qa/:path*` },
      { source: "/recorder/:path*", destination: `${backend}/recorder/:path*` },
    ];
  },
};

export default nextConfig;
