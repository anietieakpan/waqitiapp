import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import viteCompression from 'vite-plugin-compression'
import { visualizer } from 'rollup-plugin-visualizer'

export default defineConfig({
  plugins: [
    react(),
    // Gzip compression
    viteCompression({
      verbose: true,
      disable: false,
      threshold: 10240, // 10kb
      algorithm: 'gzip',
      ext: '.gz',
    }),
    // Brotli compression
    viteCompression({
      verbose: true,
      disable: false,
      threshold: 10240,
      algorithm: 'brotliCompress',
      ext: '.br',
    }),
    // Bundle analyzer
    visualizer({
      filename: 'dist/stats.html',
      open: false,
      gzipSize: true,
      brotliSize: true,
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    // Production optimizations
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
        pure_funcs: ['console.log', 'console.info', 'console.debug'],
      },
    },
    // Chunk size warnings
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        // Advanced code splitting
        manualChunks: (id) => {
          // Vendor chunks
          if (id.includes('node_modules')) {
            // React core
            if (id.includes('react') || id.includes('react-dom') || id.includes('react-router')) {
              return 'vendor-react';
            }
            // MUI components
            if (id.includes('@mui/material')) {
              return 'vendor-mui';
            }
            // MUI icons (separate for lazy loading)
            if (id.includes('@mui/icons-material')) {
              return 'vendor-mui-icons';
            }
            // Redux/state management
            if (id.includes('redux') || id.includes('@reduxjs')) {
              return 'vendor-redux';
            }
            // Form libraries
            if (id.includes('formik') || id.includes('yup')) {
              return 'vendor-forms';
            }
            // Utilities
            if (id.includes('axios') || id.includes('date-fns') || id.includes('dompurify')) {
              return 'vendor-utils';
            }
            // Everything else
            return 'vendor-misc';
          }
        },
        // Optimize chunk names for caching
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/[name]-[hash].js',
        assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
      },
    },
    // Asset optimization
    assetsInlineLimit: 4096, // 4kb - inline small assets
  },
  define: {
    'process.env': {},
  },
})