import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({ resolvers: [ElementPlusResolver()] }),
    Components({ resolvers: [ElementPlusResolver()] })
  ],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      // 保留 /api 前缀 — 直接转发到 gateway
      // (gateway 路由本身就是 /api/auth/** /api/user/** 等)
      // 注意: 之前用 path.replace(/^\/api/, '') 是 bug, 会拼成 auth/login
      //       致 gateway 路由 /api/auth/** 不匹配返回 404
      '/api': {
        target: 'http://127.0.0.1:9000',
        changeOrigin: true,
        ws: false,
        rewrite: (path) => path,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            console.log(`[proxy] ${req.method} ${req.url} → ${proxyReq.getHeader('host')}${proxyReq.path}`)
          })
          proxy.on('proxyRes', (proxyRes, req) => {
            console.log(`[proxy]   ← ${proxyRes.statusCode} ${req.url}`)
          })
          proxy.on('error', (err, req) => {
            console.error(`[proxy] ERR ${req.url}: ${err.message}`)
          })
        }
      },
      // 单独的 actuator 代理
      '/actuator': {
        target: 'http://127.0.0.1:9000',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500
  }
})
