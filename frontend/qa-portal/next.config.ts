import type { NextConfig } from "next";

// LEARN: standalone output copies only used node_modules into .next/standalone,
//        producing a self-contained bundle runnable with `node server.js` — no
//        npm install needed in the Docker runtime stage.
// LEARN: reactRemoveProperties strips data-testid attributes at build time in production.
//        This reduces HTML payload size and prevents test selectors from leaking into prod,
//        where they could be used for scraping or selector-based attacks.
const nextConfig: NextConfig = {
  output: "standalone",
  compiler: {
    reactRemoveProperties: {
      properties: ['^data-testid$']
    }
  },
};

export default nextConfig;
