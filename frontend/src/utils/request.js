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

export default request
