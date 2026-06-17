import request from '@/utils/request'

export const authApi = {
  login: (data, config) => request.post('/api/auth/login', data, config),
  register: (data, config) => request.post('/api/auth/register', data, config),
  logout: () => request.post('/api/auth/logout'),
  refresh: (refreshToken) => request.post('/api/auth/refresh', null, { params: { refreshToken } }),
  /** 登录前置：拉用户信息 + 可选公司。 */
  preview: (username) => request.get('/api/auth/preview', { params: { username } }),
  /** 公开端点：拉所有公司 (登录页初始化用) */
  tenants: () => request.get('/api/auth/tenants')
}

export const userApi = {
  list: () => request.get('/api/user/list'),
  page: (params) => request.get('/api/user/page', { params }),
  create: (data) => request.post('/api/user', data),
  update: (data) => request.put('/api/user', data),
  remove: (id) => request.delete(`/api/user/${id}`),
  resetPassword: (id) => request.post(`/api/user/${id}/reset-password`),
  changePassword: (id, body) => request.post(`/api/user/${id}/change-password`, body),
  changeStatus: (id, status) => request.post(`/api/user/${id}/status/${status}`),
  stats: () => request.get('/api/user/stats')
}

export const tenantApi = {
  list: () => request.get('/api/tenant/list'),
  page: (params) => request.get('/api/tenant/page', { params }),
  get: (code) => request.get(`/api/tenant/${code}`),
  create: (data) => request.post('/api/tenant', data),
  update: (data) => request.put('/api/tenant', data),
  remove: (id) => request.delete(`/api/tenant/${id}`),
  changeStatus: (id, status) => request.post(`/api/tenant/${id}/status/${status}`),
  stats: () => request.get('/api/tenant/stats'),
  listUsers: (id) => request.get(`/api/tenant/${id}/users`)
}

export const modelApi = {
  list: () => request.get('/api/model/list'),
  page: (params) => request.get('/api/model/page', { params }),
  get: (id) => request.get(`/api/model/${id}`),
  create: (data) => request.post('/api/model', data),
  update: (data) => request.put('/api/model', data),
  remove: (id) => request.delete(`/api/model/${id}`),
  export: (id) => request.post(`/api/model/export/${id}`),
  versions: (modelCode) => request.get(`/api/model/versions/${modelCode}`),
  activate: (id) => request.post(`/api/model/${id}/activate`),
  compare: (a, b) => request.get('/api/model/compare', { params: { a, b } }),
  stats: () => request.get('/api/model/stats'),
  newVersion: (modelCode, data) => request.post(`/api/model/${modelCode}/new-version`, data)
}

export const datasetApi = {
  page: (params) => request.get('/api/dataset/page', { params }),
  create: (data) => request.post('/api/dataset', data),
  remove: (id) => request.delete(`/api/dataset/${id}`)
}

export const trainApi = {
  submit: (data) => request.post('/api/train/submit', data),
  page: (params) => request.get('/api/train/page', { params }),
  get: (id) => request.get(`/api/train/${id}`)
}

export const agentApi = {
  list: () => request.get('/api/agent/list'),
  page: (params) => request.get('/api/agent/page', { params }),
  get: (id) => request.get(`/api/agent/${id}`),
  create: (data) => request.post('/api/agent', data),
  update: (data) => request.put('/api/agent', data),
  remove: (id) => request.delete(`/api/agent/${id}`),
  chat: (data) => request.post('/api/conversation/chat', data),
  history: (sessionId) => request.get('/api/conversation/history', { params: { sessionId } })
}

export const toolApi = {
  list: () => request.get('/api/tool/list'),
  page: (params) => request.get('/api/tool/page', { params }),
  create: (data) => request.post('/api/tool', data),
  remove: (id) => request.delete(`/api/tool/${id}`)
}

export const knowledgeApi = {
  bases: () => request.get('/api/knowledge/base/list'),
  createBase: (data) => request.post('/api/knowledge/base', data),
  documents: (kbId, params) => request.get('/api/knowledge/document/page', { params: { kbId, ...params } }),
  upload: (kbId, file) => {
    const fd = new FormData()
    fd.append('file', file)
    return request.post(`/api/knowledge/document/upload?kbId=${kbId}`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  search: (kbId, query, topK = 3) => request.get('/api/knowledge/search', { params: { kbId, query, topK } })
}

export const inferenceApi = {
  models: () => request.get('/api/inference/models'),
  generate: (data) => request.post('/api/inference/generate', data),
  chat: (data) => request.post('/api/chat/completions', data)
}

// ---------- 训练侧 v2 (多模型 + 实时预览 + 防幻觉) ----------
export const trainerApi = {
  // 列出已注册的 trainer (UI model picker)
  models: () => request.get('/api/trainer/models'),
  // 提交训练 (新版本:trainerId + params)
  submit: (data) => request.post('/api/trainer/submit', data),
  // 查询任务
  job: (id) => request.get(`/api/trainer/job/${id}`),
  jobs: () => request.get('/api/trainer/jobs'),
  // 热更新超参数
  updateParams: (id, delta) => request.put(`/api/trainer/job/${id}/params`, delta),
  // 请求实时生成样本
  sample: (id, prompt, maxTokens = 60) =>
    request.post(`/api/trainer/job/${id}/sample?prompt=${encodeURIComponent(prompt)}&maxTokens=${maxTokens}`),
  // SSE 事件流
  streamUrl: (id) => `/api/trainer/job/${id}/stream`,
  health: () => request.get('/api/trainer/health')
}

// ---------- 角色 / 菜单 / 审计 ----------
export const roleApi = {
  list: () => request.get('/api/role/list'),
  page: (params) => request.get('/api/role/page', { params }),
  create: (data) => request.post('/api/role', data),
  update: (data) => request.put('/api/role', data),
  remove: (id) => request.delete(`/api/role/${id}`),
  changeStatus: (id, status) => request.post(`/api/role/${id}/status/${status}`),
  stats: () => request.get('/api/role/stats'),
  assign: (data) => request.post('/api/role/assign', data),
  byUser: (userId, tenantId) => request.get(`/api/role/by-user/${userId}`, { params: { tenantId } }),
  userIds: (roleId) => request.get(`/api/role/${roleId}/users`)
}

export const menuApi = {
  list: () => request.get('/api/menu/list'),
  tree: () => request.get('/api/menu/tree'),
  create: (data) => request.post('/api/menu', data),
  update: (data) => request.put('/api/menu', data),
  remove: (id) => request.delete(`/api/menu/${id}`),
  byRole: (roleId) => request.get(`/api/menu/by-role/${roleId}`),
  assign: (data) => request.post('/api/menu/assign', data)
}

export const auditApi = {
  page: (params) => request.get('/api/audit/login/page', { params }),
  stats: () => request.get('/api/audit/login/stats'),
  trend: (days = 7) => request.get('/api/audit/login/trend', { params: { days } })
}

export const workflowApi = {
  listSpecs: () => request.get('/api/workflow/spec/list'),
  getSpec: (id) => request.get(`/api/workflow/spec/${id}`),
  saveSpec: (data) => request.post('/api/workflow/spec', data),
  removeSpec: (id) => request.delete(`/api/workflow/spec/${id}`),
  duplicate: (id) => request.post(`/api/workflow/spec/${id}/duplicate`),
  listRuns: () => request.get('/api/workflow/runs'),
  getRun: (id) => request.get(`/api/workflow/run/${id}`),
  run: (data) => request.post('/api/workflow/run', data),
  // 实时调用: 同步执行某个节点, 返回结果
  exec: (data) => request.post('/api/workflow/exec', data),
  // 批量执行
  execBatch: (data) => request.post('/api/workflow/exec/batch', data),
  template: () => request.get('/api/workflow/templates/train-eval-deploy'),
  // 组件参数 schema (后台可配, 不是前端 hardcode)
  listComponentSchemas: () => request.get('/api/workflow/component-schemas'),
  getComponentSchema: (nodeId) => request.get(`/api/workflow/component-schemas/${nodeId}`),
  // AI 智能建议: 用户输入部分参数, 后端按 schema 给推荐
  suggestComponentParams: (nodeId, body) => request.post(`/api/workflow/component-schemas/${nodeId}/suggest`, body)
}

// ---------- 知识库流程编排 ----------
export const pipelineApi = {
  nodes: () => request.get('/api/knowledge/pipeline/nodes'),
  save: (p) => request.post('/api/knowledge/pipeline', p),
  list: () => request.get('/api/knowledge/pipeline'),
  get: (id) => request.get(`/api/knowledge/pipeline/${id}`),
  remove: (id) => request.delete(`/api/knowledge/pipeline/${id}`),
  run: (id, query, config = {}) => request.post(`/api/knowledge/pipeline/${id}/run?query=${encodeURIComponent(query)}`, config),
  getRun: (rid) => request.get(`/api/knowledge/pipeline/run/${rid}`)
}

export const distributedApi = {
  health: () => request.get('/api/distributed/health'),
  // 1. 锁
  lockDemo: (body) => request.post('/api/distributed/lock/demo', body),
  releaseLock: (key) => request.post('/api/distributed/lock/release', null, { params: { key } }),
  // 2. 雪花 ID
  snowflake: (n) => request.get('/api/distributed/snowflake/next', { params: { n } }),
  // 3. 限流
  rateLimitCheck: (body) => request.post('/api/distributed/ratelimiter/check', body),
  rateLimitReset: (key) => request.post('/api/distributed/ratelimiter/reset', null, { params: { key } }),
  // 4. 幂等
  idempotencySubmit: (token, body) => request.post('/api/distributed/idempotency/submit', body, { headers: { 'X-Idempotency-Key': token } }),
  // 5. 缓存
  cacheGet: (key) => request.get('/api/distributed/cache/get', { params: { key } }),
  cacheEvict: (key) => request.post('/api/distributed/cache/evict', null, { params: { key } }),
  // 6. 事件
  eventPublish: (body) => request.post('/api/distributed/event/publish', body),
  eventSubscribe: (topic) => request.post('/api/distributed/event/subscribe', null, { params: { topic } }),
  eventLog: () => request.get('/api/distributed/event/log'),
  // 7. 调度
  schedulerLeader: () => request.post('/api/distributed/scheduler/leader'),
  schedulerInfo: () => request.get('/api/distributed/scheduler/info')
}
