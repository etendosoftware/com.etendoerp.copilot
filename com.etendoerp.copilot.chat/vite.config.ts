import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { viteExternalsPlugin } from "vite-plugin-externals";

export default defineConfig(({ mode }) => {
  return {
    base: "./",
    plugins: [
      react(),
      viteExternalsPlugin({
        "react-native-document-picker": "DocumentPicker",
        "react-native-markdown-display": "ReactNativeMarkdownDisplay",
      }),
    ],
    resolve: {
      alias: {
        "react-native": "react-native-web",
        "react-native-svg": "react-native-svg/lib/commonjs/ReactNativeSVG.web.js",
        "react-native/Libraries/Utilities/codegenNativeComponent": "react-native-web/dist/exports/View",
        "react-native-svg/lib/module/fabric/CircleNativeComponent": "react-native-web/dist/exports/View",
        "react-native-svg/lib/module/fabric/RectNativeComponent": "react-native-web/dist/exports/View",
      },
    },
    resolveExtensions: [
      ".web.tsx",
      ".tsx",
      ".web.ts",
      ".ts",
      ".web.jsx",
      ".jsx",
      ".web.js",
      ".js",
      ".css",
      ".json",
    ],
    build: {
      emptyOutDir: true,
      outDir: "../web/com.etendoerp.copilot.dist",
      rollupOptions: {
        external: [
          "react-native-document-picker",
          "react-native-markdown-display",
        ],
      },
    },
    server:
      mode === "development"
        ? {
          proxy: {
            "/etendo": {
              target: "http://localhost:8080",
              changeOrigin: true,
            },
          },
        }
        : undefined,
  };
});