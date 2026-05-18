import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/qa/:path*",
        destination: `${process.env.QA_ORCHESTRATOR_URL ?? "http://localhost:8700"}/api/qa/:path*`,
      },
    ];
  },
};

export default nextConfig;
