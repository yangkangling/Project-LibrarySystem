import axios from 'axios'
import { ElMessage } from 'element-plus'

export const http = axios.create({
  baseURL: '/',
  timeout: 15000,
  withCredentials: true
})

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.message
      || error.response?.data?.error
      || error.message
      || '请求失败'
    if (error.response?.status !== 401) {
      ElMessage.error(message)
    }
    return Promise.reject(new Error(message))
  }
)

export function formBody(data) {
  const body = new URLSearchParams()
  Object.entries(data).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      body.append(key, value)
    }
  })
  return body
}

export function pageParams(query) {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== undefined && value !== null && value !== '')
  )
}
