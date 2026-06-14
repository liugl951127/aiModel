import request from '@/utils/request'

export const authApi = {
  login: (data) => request.post('/api/auth/login', data),
  logout: () => request.post('/api/auth/logout'),
  refresh: (refreshToken) => request.post('/api/auth/refresh', null, { params: { refreshToken } })
}

export const userApi = {
  list: () => request.get('/api/user/list'),
  page: (params) => request.get('/api/user/page', { params }),
  create: (data) => request.post('/api/user', data),
  update: (data) => request.put('/api/user', data),
  remove: (id) => request.delete(`/api/user/${id}`)
}

export const tenantApi = {
  list: () => request.get('/api/tenant/list'),
  get: (code) => request.get(`/api/tenant/${code}`),
  create: (data) => request.post('/api/tenant', data)
}

export const modelApi = {
  list: () => request.get('/api/model/list'),
  page: (params) => request.get('/api/model/page', { params }),
  get: (id) => request.get(`/api/model/${id}`),
  create: (data) => request.post('/api/model', data),
  update: (data) => request.put('/api/model', data),
  remove: (id) => request.delete(`/api/model/${id}`),
  export: (id) => request.post(`/api/model/export/${id}`)
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
