import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 企业级 axios 封装
 *
 * 关键能力:
 *  - 自动加 JWT token + tenant_id header
 *  - 后端 Result 包装自动解包 (code===200 → return data, 否则 ElMessage + reject)
 *  - 401 自动跳登录 (token 过期)
 *  - 5xx / 网络错误自动重试 2 次 (指数退避 500ms → 1500ms)
 *  - 30s 超时 (跟后端 Feign read-timeout 一致)
 *  - unwrap() 兼容老代码 r.data.X 的写法 (3 种可能解构)
 */
const request = axios.create({
  baseURL: '/',
  timeout: 30000
})

// === 请求拦截器: 注入认证信息 ===
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  const tenantId = localStorage.getItem('tenant_id')
  if (tenantId) config.headers['X-Tenant-Id'] = tenantId
  // 标记不重试 (上传 / 导出用)
  if (config.noRetry) config.headers['X-No-Retry'] = '1'
  return config
})

// === 响应拦截器: Result 解包 + 401 + 错误分类 ===
request.interceptors.response.use(
  (resp) => {
    const data = resp.data
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 200) return data
      if (data.code === 401) {
        ElMessage.error('登录已过期，请重新登录')
        localStorage.clear()
        location.href = '/#/login'
        return Promise.reject(data)
      }
      // 业务错误: 不重试, 弹 toast
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(data)
    }
    // 原生 page/PageResult 等不带 code 字段的, 直接返回
    return data
  },
  // === 网络 / 5xx 错误: 智能重试 ===
  async (err) => {
    const config = err?.config
    // 已重试过 / 标记不重试 / 4xx 业务错误 → 不重试
    if (!config || config.__retried || config.noRetry || (err.response?.status >= 400 && err.response?.status < 500)) {
      return showError(err)
    }
    config.__retried = (config.__retried || 0) + 1
    if (config.__retried > 2) return showError(err)
    // 指数退避: 500ms / 1500ms / 4500ms
    const delay = 500 * Math.pow(3, config.__retried - 1)
    await new Promise(r => setTimeout(r, delay))
    return request.request(config)
  }
)

function showError(err) {
  // 401 已经处理, 不会到这里
  if (err?.response?.status === 401) return Promise.reject(err)
  if (err?.code === 'ERR_NETWORK' || err?.message?.includes('Network')) {
    ElMessage.error('网络异常，请检查连接')
  } else if (err?.response?.status >= 500) {
    ElMessage.error('服务暂不可用，请稍后再试')
  } else {
    ElMessage.error(err?.response?.data?.message || err?.message || '请求失败')
  }
  return Promise.reject(err)
}

/**
 * 响应解包辅助: 兼容历史代码 r.data.X 写法.
 *
 * @example
 *   const r = await api.x()         // 拦截器已返 Result
 *   const d = unwrap(r)             // 拿 data
 *   const id = d.id                 // 直接用
 */
export function unwrap(resp) {
  if (!resp) return resp
  // 拦截器已处理过 code===200, 此时 r = {code, message, data}
  if (resp.data !== undefined && typeof resp === 'object' && 'code' in resp) {
    return resp.data
  }
  // 兜底: 老代码 r = axios 原始响应
  if (resp.data && typeof resp.data === 'object' && 'code' in resp.data && 'data' in resp.data) {
    return resp.data.data
  }
  if (resp.data !== undefined) return resp.data
  return resp
}

export default request