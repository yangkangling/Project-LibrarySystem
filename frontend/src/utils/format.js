export const statusMap = {
  enabled: '启用',
  disabled: '停用',
  borrowed: '借阅中',
  returned: '已归还',
  overdue: '已逾期',
  available: '可借',
  lost: '丢失',
  damaged: '损坏'
}

export function statusText(value) {
  return statusMap[value] || value || '-'
}

export function statusType(value) {
  if (value === 'enabled' || value === 'returned' || value === 'available') return 'success'
  if (value === 'disabled' || value === 'overdue' || value === 'lost') return 'danger'
  if (value === 'borrowed') return 'warning'
  return 'info'
}

export function pageContent(data) {
  return Array.isArray(data) ? data : data?.content || []
}

export function pageTotal(data) {
  return Array.isArray(data) ? data.length : data?.totalElements || 0
}

export function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

export function moneylessNumber(value) {
  return Number.isFinite(Number(value)) ? Number(value) : 0
}
