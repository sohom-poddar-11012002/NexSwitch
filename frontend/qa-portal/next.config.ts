import type { NextConfig } from "next";

// LEARN: standalone output copies only used node_modules into .next/standalone,
//        producing a self-contained bundle runnable with `node server.js` — no
//        npm install needed in the Docker runtime stage.
const nextConfig: NextConfig = {
  output: "standalone",
};

export default nextConfig;
