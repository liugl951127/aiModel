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
  css: {
    preprocessorOptions: {
      scss: {
        // SCSS 全局变量 (可省略, 这里只是防拼写错误)
        api: 'modern-compiler'
      },
      // 强制 scss 文件以 text/x-scss MIME 输出 (避免 vite 返 text/html fallback)
      sass: {
        api: 'modern-compiler'
      }
    }
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    // 缓存目录指定 -- dev server 重启后老缓存不命中
    cacheDir: 'node_modules/.vite',
    // 不存在的路径返 404 (避免 SPA fallback 干扰静态资源)
    fs: { strict: true },
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
