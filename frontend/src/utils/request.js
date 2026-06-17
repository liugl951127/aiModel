import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/',
  // 跟后端 Feign read-timeout (30s) 保持一致. 超过说明服务不可用, 早点让用户重试
  timeout: 30000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  const tenantId = localStorage.getItem('tenant_id')
  if (tenantId) config.headers['X-Tenant-Id'] = tenantId
  return config
})

request.interceptors.response.use(
  (resp) => {
    const data = resp.data
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 200) {
        return data
      }
      if (data.code === 401) {
        ElMessage.error('登录已过期，请重新登录')
        localStorage.clear()
        location.href = '/#/login'
        return Promise.reject(data)
      }
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(data)
    }
    // raw page result (page/PageResult)
    return data
  },
  (err) => {
    ElMessage.error(err.message || '网络错误')
    return Promise.reject(err)
  }
)

/**
 * 响应解包辅助: 后端返 {code,message,data}, 拦截器已抓 → r = 整个 Result
 * 但代码里常看到 r.data.X (当拦截器没拦时的写法) → 修起来麻烦
 * 用法: const d = unwrap(r) 拿到最终 data
 */
export function unwrap(resp) {
  if (!resp) return resp
  if (resp.data && typeof resp.data === 'object' && 'code' in resp.data && 'data' in resp.data) {
    return resp.data.data
  }
  if (resp.data !== undefined) return resp.data
  return resp
}

export default request
