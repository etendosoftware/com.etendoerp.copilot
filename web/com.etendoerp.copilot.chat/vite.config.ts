import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { viteExternalsPlugin } from 'vite-plugin-externals';

export default defineConfig(({ mode }) => {
  return {
    base: "./",
    plugins: [
      react(),
      viteExternalsPlugin({
        'react-native-document-picker': 'DocumentPicker',
      })
    ],
    build: {
      outDir: "../com.etendoerp.copilot.dist",
      rollupOptions: {
        external: "react-native-document-picker",
        output: {
          entryFileNames: `copilot.js`,
          chunkFileNames: `copilot.js`,
          assetFileNames: `copilot.[ext]`
        }
      },
    },
    server: mode === 'development' ? {
      proxy: {
        '/etendo': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        }
      }
    } : undefined
  };
});