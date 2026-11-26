import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// In development /api is proxied to the backend (BACKEND_URL, default http://localhost:8080).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: { '/api': { target: env.BACKEND_URL || 'http://localhost:8080', changeOrigin: false } },
    },
  };
});
