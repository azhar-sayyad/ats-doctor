/**
 * Next.js config. `standalone` output keeps the Docker image small and lets
 * the container run `node server.js` without a full node_modules copy.
 */
/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
};

export default nextConfig;
