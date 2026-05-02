import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const http = axios.create({
  baseURL: '/api/v1/org',
  timeout: 15000
})

http.interceptors.request.use(config => {
  const token = localStorage.getItem('orgToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  res => {
    const data = res.data
    if (data.code === 200) return data
    ElMessage.error(data.msg || '请求失败')
    return Promise.reject(new Error(data.msg))
  },
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('orgToken')
      router.replace('/login')
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(err.response?.data?.msg || '网络异常')
    }
    return Promise.reject(err)
  }
)

export default http
