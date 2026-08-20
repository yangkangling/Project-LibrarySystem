import axios from 'axios'
import { ElMessage } from 'element-plus'
import { formatCopyCode } from '@/utils/format'

// 统一的后端请求实例。
export const http = axios.create({
  baseURL: '/',
  timeout: 15000,
  withCredentials: true
})

// 统一拆包响应并提示错误。
http.interceptors.response.use(
  (response) => normalizeCopyCodes(response.data),
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

function normalizeCopyCodes(data) {
  if (Array.isArray(data)) {
    return data.map(normalizeCopyCodes)
  }
  if (data && typeof data === 'object') {
    // 统一压缩后端返回的内部单册编号，避免各个表格重复写格式化逻辑。
    Object.entries(data).forEach(([key, value]) => {
      data[key] = key === 'copyCode' ? formatCopyCode(value, data.isbn || data.bookIsbn) : normalizeCopyCodes(value)
    })
  }
  return data
}

// 表单接口使用 URLSearchParams 提交。
export function formBody(data) {
  const body = new URLSearchParams()
  Object.entries(data).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      body.append(key, value)
    }
  })
  return body
}

// 过滤空查询参数，避免后端误判。
export function pageParams(query) {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== undefined && value !== null && value !== '')
  )
}
