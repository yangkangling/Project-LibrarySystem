// 后端状态值对应的中文文案。
export const statusMap = {
  enabled: '启用',
  disabled: '停用',
  borrowed: '借阅中',
  returned: '已归还',
  overdue: '已逾期',
  available: '可借',
  lost: '丢失',
  damaged: '损坏',
  none: '无罚款',
  unpaid: '待缴纳',
  paid: '已缴纳',
  waived: '已免罚'
}

export const extensionStatusMap = {
  none: '未申请',
  pending: '待审批',
  approved: '已同意',
  rejected: '已拒绝'
}

// 表格标签通用格式化。
export function statusText(value) {
  return statusMap[value] || value || '-'
}

export function statusType(value) {
  if (value === 'enabled' || value === 'returned' || value === 'available') return 'success'
  if (value === 'disabled' || value === 'overdue' || value === 'lost' || value === 'unpaid') return 'danger'
  if (value === 'borrowed') return 'warning'
  if (value === 'paid') return 'success'
  return 'info'
}

export function extensionStatusText(value) {
  return extensionStatusMap[value || 'none'] || value || '-'
}

export function extensionStatusType(value) {
  if (value === 'pending') return 'warning'
  if (value === 'approved') return 'success'
  if (value === 'rejected') return 'danger'
  return 'info'
}

// 兼容分页接口和普通数组接口。
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

// 单册编号统一展示：套书为 ISBN-套号-册号，普通书为 ISBN-册序号，隐藏内部副本流水号。
export function formatCopyCode(value, isbn) {
  if (!value) return value
  const text = String(value).trim()
  const isbnText = onlyIsbn(isbn)
  let match = text.match(/^\d{12,13}-(\d{2})-(\d{3})-\d{3}$/)
  if (match) return match[1] === '00' && match[2] === '000' ? `${text.split('-')[0]}-${text.split('-')[3]}` : `${text.split('-')[0]}-${match[1]}-${match[2]}`
  match = text.match(/^\d{12,13}-(\d{2})-(\d{3})$/)
  if (match) return match[1] === '00' && match[2] === '000' ? `${text.split('-')[0]}-001` : text
  match = text.match(/^\d{12,13}-(\d{3})$/)
  if (match) return text
  if (/^\d{2}-\d{3}$/.test(text) && isbn) return text === '00-000' ? `${onlyIsbn(isbn)}-001` : `${onlyIsbn(isbn)}-${text}`
  match = text.match(/^\d{3}-(\d{3})$/)
  if (match && isbn) return `${onlyIsbn(isbn)}-${match[1]}`
  if (isbnText && text === isbnText) return `${isbnText}-001`
  return text
}

function onlyIsbn(isbn) {
  return String(isbn || '').trim()
}
