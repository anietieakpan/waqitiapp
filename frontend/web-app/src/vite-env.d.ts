/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ANALYTICS_ENDPOINT?: string
  readonly VITE_API_BASE_URL?: string
  // Add other env variables here
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
