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
  // ★ 防老依赖缓存: 强制 dev 启动时重新扫描依赖, 避免残留无效文件引用
  optimizeDeps: {
    // 不用 force, 排除可能多版本或不存在的依赖
    include: [
      'vue',
      'vue-router',
      'pinia',
      'axios',
      'echarts',
      'vue-echarts',
      'element-plus',
      '@element-plus/icons-vue'
    ],
    // 历史 cache 里出现过但当前 package.json 没装的包: 排除防报错
    exclude: [
      'pinia-plugin-persistedstate',
      'vue-i18n',
      'element-plus/dist/locale/zh-cn'
    ]
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
    chunkSizeWarningLimit: 800,
    // ★ v3.x 拆包策略:
    // 1) 拆主包 vendor (vue, vue-router, pinia, axios) → 首页不加载 workflow/knowledge
    // 2) 拆 element-plus → 仅使用组件时才加载
    // 3) 拆 echarts → 大库, 仅 Charts 页加载
    // 4) 各业务 view 按路由自动拆 (vite 默认 dynamic import)
    rollupOptions: {
      output: {
        // 手动分包 (防止 vite 默认把什么都打进 index.js)
        manualChunks: {
          // Vue 核心 (首屏 pre-load)
          'vendor-vue': ['vue', 'vue-router', 'pinia'],
          // 网络 + 工具 (首屏 pre-load)
          'vendor-utils': ['axios']
          // ★ Element Plus 不手动分包: 让 unplugin-auto-import + unplugin-vue-components 按需 import
          //   各组件代码与使用它的 view 同 chunk, 首屏不加载 944KB
          // ★ ECharts: 同上, 大库仅在 Dashboard / Distributed / Monitor 加载
        },
        // 拆分后 chunk 名格式
        chunkFileNames: 'assets/[name]-[hash].js',
        entryFileNames: 'assets/[name]-[hash].js'
      }
    },
    // Rollup 压缩选项 (提高 Tree-shaking)
    minify: 'esbuild',
    esbuild: {
      // 删除 console.log / debugger (生产环境)
      drop: process.env.NODE_ENV === 'production' ? ['console', 'debugger'] : [],
      // 优化条件分支
      pure: ['console.log']
    },
    // gzip 前大小报告
    reportCompressedSize: true
  }
})
