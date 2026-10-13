import axios from 'axios'

const camel = value => {
  if (Array.isArray(value)) return value.map(camel)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [
      key.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase()), camel(item)
    ]))
  }
  return value
}

const api = axios.create({ baseURL: '/api', timeout: 10000 })

api.interceptors.request.use(config => {
  const token = localStorage.getItem('liveseat-token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  response => {
    const payload = camel(response.data)
    if (payload.code !== 200) return Promise.reject(new Error(payload.message || '请求失败'))
    return payload.data
  },
  error => Promise.reject(new Error(error.response?.data?.message || error.message || '网络连接失败'))
)

export default api
