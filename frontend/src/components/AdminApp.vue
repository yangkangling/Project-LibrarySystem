<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Collection,
  CopyDocument,
  DataLine,
  Finished,
  House,
  Key,
  Location,
  Reading,
  Refresh,
  Search,
  SwitchButton,
  Tickets,
  User,
  Warning
} from '@element-plus/icons-vue'
import { http, pageParams } from '@/api/http'
import {
  extensionStatusText,
  extensionStatusType,
  formatDateTime,
  pageContent,
  pageTotal,
  statusText,
  statusType
} from '@/utils/format'

const emit = defineEmits(['logout'])

// 管理端菜单和分页基础状态。
const pageSize = 10
const activePage = ref('dashboard')
const menus = [
  { key: 'dashboard', label: '工作台', icon: House },
  { key: 'warnings', label: '预警中心', icon: Warning },
  { key: 'categories', label: '图书分类', icon: Collection },
  { key: 'books', label: '图书管理', icon: Reading },
  { key: 'readers', label: '读者管理', icon: User },
  { key: 'returns', label: '未还书办理', icon: Finished },
  { key: 'records', label: '全部借阅记录', icon: DataLine },
  { key: 'extensions', label: '延期申请', icon: Tickets },
  { key: 'overdue', label: '逾期查询', icon: Warning },
  { key: 'storage', label: '书架查询', icon: Location }
]
const pageSubtitles = {
  dashboard: '查看馆藏、借阅、读者与逾期概况',
  warnings: '查看 7 天、30 天、90 天或全部未还的到期风险',
  categories: '维护分类、查看分类图书、迁移删除和合并分类',
  books: '维护书目信息、书架位置和启停状态',
  readers: '维护读者账号、手机号、密码和账号状态',
  returns: '只显示当前未归还、可以办理还书的记录',
  records: '显示全部借阅历史，包括借阅中、已归还和已逾期',
  extensions: '查看读者提交的延期申请并同意延期',
  overdue: '集中查看所有曾经逾期的记录，未还记录可办理归还',
  storage: '查询图书所在书架与可借数量'
}
const currentMenu = computed(() => menus.find((item) => item.key === activePage.value))

// 各模块独立加载状态。
const loading = reactive({
  dashboard: false,
  warnings: false,
  categories: false,
  books: false,
  readers: false,
  borrowReaders: false,
  borrowBooks: false,
  returns: false,
  records: false,
  extensions: false,
  overdue: false,
  storage: false
})

// 工作台统计数据。
const dashboard = reactive({
  bookTypes: 0,
  totalBooks: 0,
  availableBooks: 0,
  borrowedBooks: 0,
  readers: 0,
  overdue: 0,
  borrowRecordTotal: 0,
  categoryStats: [],
  borrowTrend: [],
  statusStats: []
})

// 工作台统计图配色和状态文案。
const chartColors = ['#2563eb', '#16a34a', '#f59e0b', '#dc2626', '#7c3aed', '#0891b2', '#64748b']
const trendChart = { width: 320, height: 168, left: 38, right: 14, top: 16, bottom: 40 }
const dashboardStatusLabels = {
  borrowed: '借阅中',
  returned: '已归还',
  overdue: '逾期未还'
}
const categoryChartTotal = computed(() => dashboard.categoryStats.reduce((sum, item) => sum + Number(item.total || 0), 0))
const trendMax = computed(() => niceTrendMax(Math.max(1, ...dashboard.borrowTrend.flatMap((item) => [Number(item.borrowCount || 0), Number(item.returnCount || 0)]))))
const trendYTicks = computed(() => {
  const step = trendTickStep(trendMax.value)
  const ticks = []
  for (let value = 0; value <= trendMax.value; value += step) {
    ticks.push(value)
  }
  if (ticks[ticks.length - 1] !== trendMax.value) {
    ticks.push(trendMax.value)
  }
  return ticks.sort((left, right) => right - left)
})
const trendBorrowPoints = computed(() => trendPoints('borrowCount'))
const trendReturnPoints = computed(() => trendPoints('returnCount'))
const trendBorrowTotal = computed(() => dashboard.borrowTrend.reduce((sum, item) => sum + Number(item.borrowCount || 0), 0))
const trendReturnTotal = computed(() => dashboard.borrowTrend.reduce((sum, item) => sum + Number(item.returnCount || 0), 0))
const hasTrendActivity = computed(() => trendBorrowTotal.value + trendReturnTotal.value > 0)
const categoryPieStyle = computed(() => ({ background: categoryPieGradient() }))
const hasCategoryStats = computed(() => dashboard.categoryStats.length > 0 && categoryChartTotal.value > 0)
const categoryAuditRows = computed(() => dashboard.categoryStats.map((item, index) => {
  const total = Number(item.total || 0)
  const available = Number(item.available || 0)
  const borrowed = Number(item.borrowed ?? Math.max(0, total - available))
  const diff = total - available - borrowed
  return {
    ...item,
    total,
    available,
    borrowed,
    diff,
    color: chartColor(index),
    availablePercent: percentOf(available, total),
    borrowedPercent: percentOf(borrowed, total),
    diffPercent: percentOf(Math.abs(diff), Math.max(total, available + borrowed + Math.abs(diff)))
  }
}))
const statusTotal = computed(() => dashboard.statusStats.reduce((sum, item) => sum + Number(item.value || 0), 0))
const statusExpectedTotal = computed(() => Number(dashboard.borrowRecordTotal || statusTotal.value))
const statusDiff = computed(() => statusExpectedTotal.value - statusTotal.value)
const statusChartRows = computed(() => {
  const rows = dashboard.statusStats.map((item, index) => ({
    ...item,
    value: Number(item.value || 0),
    label: dashboardStatusLabels[item.name] || item.name,
    color: chartColor(index)
  }))
  if (statusDiff.value > 0) {
    rows.push({ name: 'missing', label: '未归类/漏算', value: statusDiff.value, color: '#dc2626' })
  }
  if (statusDiff.value < 0) {
    rows.push({ name: 'overflow', label: '超出总数', value: Math.abs(statusDiff.value), color: '#dc2626' })
  }
  return rows.map((item) => ({
    ...item,
    percent: percentOf(item.value, Math.max(statusExpectedTotal.value, statusTotal.value))
  }))
})
const hasStatusStats = computed(() => statusExpectedTotal.value > 0)

// 预警中心和工作台预警摘要。
const warningRecords = ref([])
const warningTotal = ref(0)
const warningDefaultDays = 90
const warningQuery = reactive({ keyword: '', days: warningDefaultDays, page: 0 })
const warningRangeOptions = [
  { label: '未来 7 天', value: 7 },
  { label: '未来 30 天', value: 30 },
  { label: '未来 90 天', value: 90 },
  { label: '全部未还', value: 365 }
]
const dashboardWarnings = ref([])
const dashboardWarningTotal = ref(0)

// 分类和书架下拉选项。
const categoryOptions = ref([])
const shelfLocationOptions = ref([])
const shelfAreaOptions = Array.from({ length: 26 }, (_, index) => String.fromCharCode(65 + index))
const SHELF_NUMBER_MAX = 50
const BOOK_STOCK_MAX = 50
const categories = ref([])
const categoryTotal = ref(0)
const categoryQuery = reactive({ keyword: '', page: 0 })
const categoryDialog = reactive({
  visible: false,
  editing: false,
  form: { id: null, name: '', description: '' }
})
const categoryBooksLoading = ref(false)
const categoryBooksDialog = reactive({
  visible: false,
  category: null,
  rows: [],
  total: 0,
  query: { keyword: '', page: 0 }
})
const categoryDeleteDialog = reactive({
  visible: false,
  row: null,
  targetCategoryId: ''
})
const categoryMergeDialog = reactive({
  visible: false,
  row: null,
  targetCategoryId: ''
})

// 图书列表、筛选和弹窗状态。
const books = ref([])
const bookTotal = ref(0)
const bookQuery = reactive({ keyword: '', categoryId: '', status: '', page: 0 })
const bookDialog = reactive({
  visible: false,
  editing: false,
  form: emptyBook()
})
const bookDrawer = reactive({ visible: false, data: null })
const bookStorageQuery = reactive({ page: 0 })
const bookCopyQuery = reactive({ page: 0 })

// 读者列表、弹窗和重置密码结果。
const readers = ref([])
const readerTotal = ref(0)
const readerQuery = reactive({ keyword: '', status: '', page: 0 })
const readerDialog = reactive({
  visible: false,
  editing: false,
  form: emptyReader()
})
const readerPasswordResult = reactive({
  visible: false,
  readerCard: '',
  realName: '',
  password: ''
})
const readerDrawer = reactive({ visible: false, loading: false, error: '', data: null })
const readerCurrentQuery = reactive({ page: 0 })
const readerHistoryQuery = reactive({ page: 0 })

const bookStorageTotal = computed(() => bookDrawer.data?.storageLocations?.length || 0)
const bookCopyTotal = computed(() => bookDrawer.data?.copies?.length || 0)
const readerCurrentTotal = computed(() => readerDrawer.data?.currentBorrowRecords?.length || 0)
const readerHistoryTotal = computed(() => readerDrawer.data?.historyRecords?.length || 0)
const pagedBookStorageRows = computed(() => pageSlice(bookDrawer.data?.storageLocations || [], bookStorageQuery.page))
const pagedBookCopyRows = computed(() => pageSlice(bookDrawer.data?.copies || [], bookCopyQuery.page))
const pagedReaderCurrentRows = computed(() => pageSlice(readerDrawer.data?.currentBorrowRecords || [], readerCurrentQuery.page))
const pagedReaderHistoryRows = computed(() => pageSlice(readerDrawer.data?.historyRecords || [], readerHistoryQuery.page))

const readerLoginNotice = computed(() => [
  `借阅证号：${readerPasswordResult.readerCard}`,
  `临时密码：${readerPasswordResult.password}`,
  '登录地址：http://localhost:8080/',
  '登录后可点击“修改密码”改成自己的密码。'
].join('\n'))

// 借书弹窗中的读者和图书候选项。
const readerOptions = ref([])
const bookOptions = ref([])
const borrowReaderKeyword = ref('')
const borrowBookKeyword = ref('')
const borrowReaderTotal = ref(0)
const borrowBookTotal = ref(0)
const borrowReaderQuery = reactive({ page: 0 })
const borrowBookQuery = reactive({ page: 0 })
const selectedReader = ref(null)
const selectedBooks = ref([])
const borrowDialog = reactive({ visible: false, mode: '' })
const borrowDialogTitle = computed(() => {
  if (borrowDialog.mode === 'reader') {
    return '为读者办理借书'
  }
  if (borrowDialog.mode === 'book') {
    return '为图书选择读者'
  }
  return '办理借书'
})

// 还书办理列表和勾选项。
const returnRecords = ref([])
const returnTotal = ref(0)
const returnQuery = reactive({ keyword: '', page: 0 })
const returnSelection = ref([])

// 借阅记录查询状态。
const records = ref([])
const recordTotal = ref(0)
const recordQuery = reactive({
  keyword: '',
  status: '',
  borrowStart: '',
  dueRange: [],
  page: 0
})
const recordDrawer = reactive({ visible: false, data: null })
const extensionRequests = ref([])
const extensionTotal = ref(0)
const extensionQuery = reactive({ keyword: '', extensionStatus: '', page: 0 })

// 逾期查询支持普通查询和罚款冻结处理模式。
const overdueRecords = ref([])
const overdueTotal = ref(0)
const overdueQuery = reactive({ keyword: '', page: 0 })
const overdueMode = ref('records')
const overdueModeDialog = reactive({ visible: false })

// 书架查询状态。
const storageRows = ref([])
const storageTotal = ref(0)
const storageQuery = reactive({ keyword: '', page: 0 })
const storageDialog = reactive({
  visible: false,
  form: { shelfLocation: '', remark: '' }
})
const storageShelfParts = reactive(emptyShelfParts())
const bookShelfParts = reactive(emptyShelfParts())

// 新增图书表单默认值。
function emptyBook() {
  return {
    id: null,
    isbn: '',
    title: '',
    author: '',
    categoryId: '',
    shelfLocation: '',
    totalCount: 1,
    status: 'enabled'
  }
}

// 书架位置默认拆成区域、排号、格号三段。
function emptyShelfParts() {
  return { area: 'A', row: 1, slot: 1 }
}

// 新增读者表单默认值。
function emptyReader() {
  return {
    id: null,
    username: '',
    realName: '',
    phone: '',
    password: '',
    remark: '',
    status: 'enabled'
  }
}

// 根据当前菜单分发加载逻辑。
async function loadPage(page = activePage.value) {
  const loaders = {
    dashboard: loadDashboard,
    warnings: loadWarnings,
    categories: loadCategories,
    books: loadBooks,
    readers: loadReaders,
    returns: loadReturnRecords,
    records: loadRecords,
    extensions: loadExtensionRequests,
    overdue: loadOverdue,
    storage: loadStorage
  }
  await loaders[page]?.()
}

async function selectPage(page) {
  const isSamePage = activePage.value === page
  activePage.value = page
  if (page === 'overdue' && !isSamePage) {
    overdueModeDialog.visible = true
  }
  await loadPage(page)
}

// 进入逾期查询时选择查看模式。
async function chooseOverdueMode(mode) {
  overdueMode.value = mode
  overdueModeDialog.visible = false
  await loadOverdue(0)
}

async function quickAddBook() {
  await selectPage('books')
  openBook()
}

async function quickAddReader() {
  await selectPage('readers')
  openReader()
}

async function quickBorrowFromReaders() {
  await selectPage('readers')
}

async function quickBorrowFromBooks() {
  await selectPage('books')
}

function resetQuery(query, loader) {
  Object.keys(query).forEach((key) => {
    if (key === 'page') query[key] = 0
    else if (Array.isArray(query[key])) query[key] = []
    else query[key] = ''
  })
  loader(0)
}

function resetWarnings() {
  warningQuery.keyword = ''
  warningQuery.days = warningDefaultDays
  warningQuery.page = 0
  loadWarnings(0)
}

function resetCategories() {
  categoryQuery.keyword = ''
  categoryQuery.page = 0
  loadCategories(0)
}

function setPage(query, loader, page) {
  query.page = page - 1
  loader(query.page)
}

function setLocalPage(query, page) {
  query.page = page - 1
}

function padShelfNumber(value) {
  return String(normalizeShelfNumber(value)).padStart(2, '0')
}

function normalizeShelfNumber(value) {
  return Math.min(SHELF_NUMBER_MAX, Math.max(1, Number(value) || 1))
}

function normalizeShelfArea(value) {
  const area = String(value || 'A').trim().toUpperCase()
  return /^[A-Z]$/.test(area) ? area : 'A'
}

function shelfFromParts(parts) {
  parts.area = normalizeShelfArea(parts.area)
  parts.row = normalizeShelfNumber(parts.row)
  parts.slot = normalizeShelfNumber(parts.slot)
  return `${parts.area}-${padShelfNumber(parts.row)}-${padShelfNumber(parts.slot)}`
}

function fillShelfParts(parts, shelfLocation) {
  const match = String(shelfLocation || '').trim().match(/^([A-Z])-(\d{1,2})-(\d{1,2})$/i)
  parts.area = match ? normalizeShelfArea(match[1]) : 'A'
  parts.row = match ? normalizeShelfNumber(match[2]) : 1
  parts.slot = match ? normalizeShelfNumber(match[3]) : 1
}

function syncBookShelfFromParts() {
  bookDialog.form.shelfLocation = shelfFromParts(bookShelfParts)
}

function syncStorageShelfFromParts() {
  storageDialog.form.shelfLocation = shelfFromParts(storageShelfParts)
}

function pageSlice(rows, page) {
  const start = page * pageSize
  return rows.slice(start, start + pageSize)
}

function disableFutureDate(date) {
  const today = new Date()
  today.setHours(23, 59, 59, 999)
  return date.getTime() > today.getTime()
}

function chartColor(index) {
  return chartColors[index % chartColors.length]
}

function percentOf(value, total) {
  if (!total) return 0
  return Math.round((value / total) * 100)
}

function segmentWidth(value, total) {
  if (!value || !total) return 0
  return Math.max(4, percentOf(value, total))
}

function statusDiffText(diff) {
  if (!diff) return '无遗漏'
  return diff > 0 ? `漏算 ${diff} 条` : `多算 ${Math.abs(diff)} 条`
}

function statusDiffType(diff) {
  return diff ? 'danger' : 'success'
}

function warningTagType(level) {
  if (level === 'danger') return 'danger'
  if (level === 'warning') return 'warning'
  return 'info'
}

function shortDate(value) {
  return value ? value.slice(5) : ''
}

function niceTrendMax(value) {
  if (value <= 4) return 4
  if (value <= 10) return Math.ceil(value / 2) * 2
  if (value <= 20) return Math.ceil(value / 5) * 5
  if (value <= 50) return Math.ceil(value / 10) * 10
  return Math.ceil(value / 20) * 20
}

function trendTickStep(value) {
  if (value <= 4) return 1
  if (value <= 10) return 2
  if (value <= 20) return 5
  if (value <= 50) return 10
  return Math.ceil(value / 5 / 10) * 10
}

function trendX(index, total) {
  const plotWidth = trendChart.width - trendChart.left - trendChart.right
  if (total <= 1) return trendChart.left + plotWidth / 2
  return trendChart.left + (index * plotWidth) / (total - 1)
}

function trendY(value) {
  const plotHeight = trendChart.height - trendChart.top - trendChart.bottom
  const bottomY = trendChart.height - trendChart.bottom
  return bottomY - (Number(value || 0) / trendMax.value) * plotHeight
}

function trendPoints(field) {
  return dashboard.borrowTrend
    .map((item, index) => `${trendX(index, dashboard.borrowTrend.length).toFixed(1)},${trendY(item[field]).toFixed(1)}`)
    .join(' ')
}

function categoryPieGradient() {
  if (!categoryChartTotal.value) {
    return '#e2e8f0'
  }
  let start = 0
  const segments = dashboard.categoryStats.map((item, index) => {
    const value = Number(item.total || 0)
    const end = start + (value / categoryChartTotal.value) * 360
    const segment = `${chartColor(index)} ${start}deg ${end}deg`
    start = end
    return segment
  })
  return `conic-gradient(${segments.join(', ')})`
}

function normalizeDashboardPayload(data) {
  const statusStats = Array.isArray(data?.statusStats) ? data.statusStats : []
  const recordTotal = statusStats.reduce((sum, item) => sum + Number(item.value || 0), 0)
  return {
    ...data,
    categoryStats: Array.isArray(data?.categoryStats) ? data.categoryStats : [],
    borrowTrend: Array.isArray(data?.borrowTrend) ? data.borrowTrend : [],
    statusStats,
    borrowRecordTotal: Number(data?.borrowRecordTotal ?? recordTotal)
  }
}

function localDateString(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function recentSevenDates() {
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date()
    date.setDate(date.getDate() - (6 - index))
    return localDateString(date)
  })
}

function buildCategoryStats(rows) {
  const grouped = new Map()
  rows.forEach((book) => {
    const name = book.category?.trim() || '未分类'
    const current = grouped.get(name) || { name, total: 0, available: 0, borrowed: 0 }
    const total = Number(book.totalCount || 0)
    const available = Number(book.availableCount || 0)
    current.total += total
    current.available += available
    current.borrowed += Math.max(0, total - available)
    grouped.set(name, current)
  })
  return Array.from(grouped.values())
}

function buildBorrowTrend(rows) {
  return recentSevenDates().map((date) => ({
    date,
    borrowCount: rows.filter((record) => record.borrowDate === date).length,
    returnCount: rows.filter((record) => record.returnDate === date).length
  }))
}

function buildStatusStats(rows) {
  const grouped = new Map([
    ['borrowed', 0],
    ['returned', 0],
    ['overdue', 0]
  ])
  rows.forEach((record) => {
    const status = record.status || record.rawStatus || 'borrowed'
    grouped.set(status, (grouped.get(status) || 0) + 1)
  })
  return Array.from(grouped.entries()).map(([name, value]) => ({ name, value }))
}

async function fillDashboardChartsFromLists() {
  const [bookData, recordData] = await Promise.all([
    http.get('/books', { params: pageParams({ page: 0, size: pageSize }) }),
    http.get('/borrow/records', { params: pageParams({ page: 0, size: pageSize }) })
  ])
  const bookRows = pageContent(bookData)
  const recordRows = pageContent(recordData)
  if (!dashboard.categoryStats.length) {
    dashboard.categoryStats = buildCategoryStats(bookRows)
  }
  if (!dashboard.borrowTrend.length) {
    dashboard.borrowTrend = buildBorrowTrend(recordRows)
  }
  if (!dashboard.statusStats.length) {
    dashboard.statusStats = buildStatusStats(recordRows)
  }
  if (!dashboard.borrowRecordTotal) {
    dashboard.borrowRecordTotal = pageTotal(recordData) || recordRows.length
  }
}

// 加载工作台统计。
async function loadDashboard() {
  loading.dashboard = true
  try {
    const data = await http.get('/dashboard')
    Object.assign(dashboard, normalizeDashboardPayload(data))
    if (!dashboard.categoryStats.length || !dashboard.borrowTrend.length || !dashboard.statusStats.length) {
      await fillDashboardChartsFromLists()
    }
    await loadDashboardWarnings()
  } finally {
    loading.dashboard = false
  }
}

async function loadDashboardWarnings() {
  const data = await http.get('/borrow/warnings', {
    params: pageParams({ page: 0, size: 5, days: warningDefaultDays })
  })
  dashboardWarnings.value = pageContent(data)
  dashboardWarningTotal.value = pageTotal(data)
}

async function loadWarnings(page = warningQuery.page) {
  warningQuery.page = page
  loading.warnings = true
  try {
    const data = await http.get('/borrow/warnings', {
      params: pageParams({ ...warningQuery, size: pageSize })
    })
    warningRecords.value = pageContent(data)
    warningTotal.value = pageTotal(data)
  } finally {
    loading.warnings = false
  }
}

async function loadCategoryOptions() {
  const data = await http.get('/categories')
  categoryOptions.value = pageContent(data)
}

// 新增图书时使用已有书架位置作为可选项。
async function loadShelfLocationOptions() {
  shelfLocationOptions.value = await http.get('/storage-locations/options')
}

async function loadCategories(page = categoryQuery.page) {
  categoryQuery.page = page
  loading.categories = true
  try {
    const data = await http.get('/categories', {
      params: pageParams({ ...categoryQuery, size: pageSize })
    })
    categories.value = pageContent(data)
    categoryTotal.value = pageTotal(data)
  } finally {
    loading.categories = false
  }
}

function openCategory(row) {
  categoryDialog.editing = Boolean(row)
  Object.assign(categoryDialog.form, row ? { ...row } : { id: null, name: '', description: '' })
  categoryDialog.visible = true
}

async function saveCategory() {
  const payload = {
    name: categoryDialog.form.name,
    description: categoryDialog.form.description
  }
  if (categoryDialog.editing) {
    await http.put(`/categories/${categoryDialog.form.id}`, payload)
  } else {
    await http.post('/categories', payload)
  }
  ElMessage.success('分类已保存')
  categoryDialog.visible = false
  await refreshCategoryMaintenance()
}

function targetCategoryOptions(sourceId) {
  return categoryOptions.value.filter((item) => item.id !== sourceId)
}

async function openCategoryBooks(row, page = 0) {
  if (!row) return
  categoryBooksDialog.category = row
  categoryBooksDialog.query.page = page
  categoryBooksDialog.visible = true
  categoryBooksLoading.value = true
  try {
    const data = await http.get(`/categories/${row.id}/books`, {
      params: pageParams({ ...categoryBooksDialog.query, size: pageSize })
    })
    categoryBooksDialog.rows = pageContent(data)
    categoryBooksDialog.total = pageTotal(data)
  } finally {
    categoryBooksLoading.value = false
  }
}

function setCategoryBooksPage(page) {
  openCategoryBooks(categoryBooksDialog.category, page - 1)
}

async function deleteCategory(row) {
  if (row.bookCount > 0) {
    categoryDeleteDialog.row = row
    categoryDeleteDialog.targetCategoryId = ''
    categoryDeleteDialog.visible = true
    return
  }
  await deleteEmptyCategory(row)
}

async function deleteEmptyCategory(row) {
  await ElMessageBox.confirm(`确认删除分类“${row.name}”？`, '删除分类', { type: 'warning' })
  await http.delete(`/categories/${row.id}`)
  ElMessage.success('分类已删除')
  await refreshCategoryMaintenance()
}

async function confirmDeleteCategory() {
  const row = categoryDeleteDialog.row
  if (!row) return
  if (!categoryDeleteDialog.targetCategoryId) {
    ElMessage.warning('请选择图书迁移到哪个分类')
    return
  }
  await http.delete(`/categories/${row.id}`, {
    params: { targetCategoryId: categoryDeleteDialog.targetCategoryId }
  })
  ElMessage.success('图书已迁移，分类已删除')
  categoryDeleteDialog.visible = false
  await refreshCategoryMaintenance()
}

function openMergeCategory(row) {
  categoryMergeDialog.row = row
  categoryMergeDialog.targetCategoryId = ''
  categoryMergeDialog.visible = true
}

async function confirmMergeCategory() {
  const row = categoryMergeDialog.row
  if (!row) return
  if (!categoryMergeDialog.targetCategoryId) {
    ElMessage.warning('请选择要合并到的目标分类')
    return
  }
  await http.post(`/categories/${row.id}/merge`, {
    targetCategoryId: categoryMergeDialog.targetCategoryId
  })
  ElMessage.success('分类已合并')
  categoryMergeDialog.visible = false
  await refreshCategoryMaintenance()
}

async function refreshCategoryMaintenance() {
  await Promise.all([
    loadCategories(categoryQuery.page),
    loadCategoryOptions(),
    loadBooks(bookQuery.page),
    loadDashboard()
  ])
}

async function loadBooks(page = bookQuery.page) {
  bookQuery.page = page
  loading.books = true
  try {
    const data = await http.get('/books', {
      params: pageParams({ ...bookQuery, size: pageSize })
    })
    books.value = pageContent(data)
    bookTotal.value = pageTotal(data)
  } finally {
    loading.books = false
  }
}

function openBook(row) {
  bookDialog.editing = Boolean(row)
  Object.assign(bookDialog.form, row ? {
    id: row.id,
    isbn: row.isbn,
    title: row.title,
    author: row.author,
    categoryId: row.categoryId || '',
    shelfLocation: row.shelfLocation,
    totalCount: row.totalCount,
    status: row.status || 'enabled'
  } : emptyBook())
  fillShelfParts(bookShelfParts, bookDialog.form.shelfLocation)
  syncBookShelfFromParts()
  bookDialog.visible = true
}

// 保存图书并同步分类、书架和库存。
async function saveBook() {
  syncBookShelfFromParts()
  const selectedCategory = categoryOptions.value.find((item) => item.id === bookDialog.form.categoryId)
  const payload = {
    isbn: bookDialog.form.isbn,
    title: bookDialog.form.title,
    author: bookDialog.form.author,
    categoryId: bookDialog.form.categoryId,
    category: selectedCategory?.name,
    shelfLocation: bookDialog.form.shelfLocation,
    status: bookDialog.form.status
  }
  if (bookDialog.editing) {
    await http.put(`/books/${bookDialog.form.id}`, payload)
  } else {
    payload.totalCount = Number(bookDialog.form.totalCount)
    await http.post('/books', payload)
  }
  ElMessage.success('图书已保存')
  bookDialog.visible = false
  await Promise.all([loadBooks(), loadStorage(storageQuery.page), loadShelfLocationOptions()])
}

async function toggleBook(row) {
  const nextAction = row.status === 'disabled' ? 'enable' : 'disable'
  if (nextAction === 'disable') {
    const activeBorrowCount = Number(row.activeBorrowCount || 0)
    const message = activeBorrowCount > 0
      ? `当前还有 ${activeBorrowCount} 册在读者手中。停用后会立即禁止新借，但这些借阅记录仍保留，读者和管理员都可以继续办理归还；重新启用前不会再对外借出。确定停用吗？`
      : `停用后会立即禁止新借，重新启用前不会再对外借出。确定停用《${row.title}》吗？`
    await ElMessageBox.confirm(message, '停用图书', {
      type: 'warning',
      confirmButtonText: '停用',
      cancelButtonText: '取消'
    })
  }
  await http.put(`/books/${row.id}/${nextAction}`)
  ElMessage.success(nextAction === 'enable' ? '图书已启用' : '图书已停用')
  await loadBooks()
}

async function deleteBook(row) {
  await ElMessageBox.confirm(`确认删除图书“${row.title}”？`, '删除图书', { type: 'warning' })
  await http.delete(`/books/${row.id}`)
  ElMessage.success('图书已删除')
  await loadBooks()
}

async function openBookDetail(row) {
  bookStorageQuery.page = 0
  bookCopyQuery.page = 0
  bookDrawer.data = await http.get(`/books/${row.id}`)
  bookDrawer.visible = true
}

function closeBookDetail() {
  bookDrawer.data = null
  bookStorageQuery.page = 0
  bookCopyQuery.page = 0
}

async function loadReaders(page = readerQuery.page) {
  readerQuery.page = page
  loading.readers = true
  try {
    const data = await http.get('/readers', {
      params: pageParams({ ...readerQuery, size: pageSize })
    })
    readers.value = pageContent(data)
    readerTotal.value = pageTotal(data)
  } finally {
    loading.readers = false
  }
}

function openReader(row) {
  readerDialog.editing = Boolean(row)
  Object.assign(readerDialog.form, row ? {
    id: row.id,
    username: row.username,
    realName: row.realName,
    phone: row.phone,
    password: '',
    remark: row.remark,
    status: row.status || 'enabled'
  } : emptyReader())
  readerDialog.visible = true
}

// 管理员重置读者密码时生成临时密码。
function generateReaderPassword() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
  const values = new Uint32Array(8)
  if (window.crypto?.getRandomValues) {
    window.crypto.getRandomValues(values)
  } else {
    for (let index = 0; index < values.length; index += 1) {
      values[index] = Math.floor(Math.random() * chars.length)
    }
  }
  readerDialog.form.password = Array.from(values, (value) => chars[value % chars.length]).join('')
  ElMessage.success('已生成临时密码，保存后请告知读者')
}

function openReaderPasswordResult(reader, password) {
  readerPasswordResult.readerCard = reader.username || readerDialog.form.username
  readerPasswordResult.realName = reader.realName || readerDialog.form.realName
  readerPasswordResult.password = password
  readerPasswordResult.visible = true
}

async function copyReaderLoginNotice() {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(readerLoginNotice.value)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = readerLoginNotice.value
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    ElMessage.success('登录信息已复制')
  } catch (error) {
    ElMessage.error('复制失败，请手动复制登录信息')
  }
}

// 保存读者后把新密码展示给管理员转告读者。
async function saveReader() {
  const issuedPassword = readerDialog.form.password?.trim()
  const payload = {
    realName: readerDialog.form.realName,
    phone: readerDialog.form.phone,
    password: issuedPassword,
    remark: readerDialog.form.remark,
    status: readerDialog.form.status
  }
  let savedReader
  if (readerDialog.editing) {
    savedReader = await http.put(`/readers/${readerDialog.form.id}`, payload)
  } else {
    savedReader = await http.post('/readers', payload)
  }
  readerDialog.visible = false
  await loadReaders()
  if (issuedPassword) {
    openReaderPasswordResult(savedReader, issuedPassword)
  } else {
    ElMessage.success('读者已保存')
  }
}

async function toggleReader(row) {
  const nextAction = row.status === 'disabled' ? 'enable' : 'disable'
  await http.put(`/readers/${row.id}/${nextAction}`)
  ElMessage.success(nextAction === 'enable' ? '读者已启用' : '读者已停用')
  await loadReaders()
}

async function openReaderDetail(row) {
  readerDrawer.visible = true
  readerDrawer.loading = true
  readerDrawer.error = ''
  readerDrawer.data = null
  readerCurrentQuery.page = 0
  readerHistoryQuery.page = 0
  try {
    readerDrawer.data = await http.get(`/readers/${row.id}`)
  } catch (error) {
    readerDrawer.error = error.message || '读者详情加载失败'
    ElMessage.error(readerDrawer.error)
  } finally {
    readerDrawer.loading = false
  }
}

function closeReaderDetail() {
  readerDrawer.visible = false
  readerDrawer.loading = false
  readerDrawer.error = ''
  readerDrawer.data = null
  readerCurrentQuery.page = 0
  readerHistoryQuery.page = 0
}

function toBorrowReader(row) {
  return {
    id: row.id,
    cardNumber: row.cardNumber || row.username,
    realName: row.realName,
    phone: row.phone,
    status: row.status,
    currentBorrowCount: row.currentBorrowCount
  }
}

function toBorrowBook(row) {
  return {
    id: row.id,
    isbn: row.isbn,
    title: row.title,
    author: row.author,
    category: row.category,
    status: row.status,
    availableCount: row.availableCount,
    totalCount: row.totalCount
  }
}

function resetBorrowDialogState() {
  borrowReaderKeyword.value = ''
  borrowBookKeyword.value = ''
  borrowReaderQuery.page = 0
  borrowBookQuery.page = 0
  selectedReader.value = null
  selectedBooks.value = []
  borrowDialog.mode = ''
}

async function openBorrowFromReader(row) {
  resetBorrowDialogState()
  borrowDialog.mode = 'reader'
  selectedReader.value = toBorrowReader(row)
  borrowDialog.visible = true
  await loadBookOptions(0)
}

async function openBorrowFromBook(row) {
  resetBorrowDialogState()
  borrowDialog.mode = 'book'
  selectedBooks.value = [toBorrowBook(row)]
  borrowDialog.visible = true
  await loadReaderOptions(0)
}

function closeBorrowDialog() {
  resetBorrowDialogState()
}

async function searchReaderOptions() {
  borrowReaderQuery.page = 0
  await loadReaderOptions(0)
}

async function searchBookOptions() {
  borrowBookQuery.page = 0
  await loadBookOptions(0)
}

async function loadReaderOptions(page = borrowReaderQuery.page) {
  borrowReaderQuery.page = page
  loading.borrowReaders = true
  try {
    const data = await http.get('/borrow/reader-options', {
      params: pageParams({ keyword: borrowReaderKeyword.value, page: borrowReaderQuery.page, size: pageSize })
    })
    readerOptions.value = pageContent(data)
    borrowReaderTotal.value = pageTotal(data)
  } finally {
    loading.borrowReaders = false
  }
}

async function loadBookOptions(page = borrowBookQuery.page) {
  borrowBookQuery.page = page
  loading.borrowBooks = true
  try {
    const data = await http.get('/borrow/book-options', {
      params: pageParams({ keyword: borrowBookKeyword.value, page: borrowBookQuery.page, size: pageSize })
    })
    bookOptions.value = pageContent(data)
    borrowBookTotal.value = pageTotal(data)
  } finally {
    loading.borrowBooks = false
  }
}

function addBorrowBook(row) {
  if (row.status === 'disabled') {
    ElMessage.warning('停用图书不能加入借阅清单')
    return
  }
  if (Number(row.availableCount || 0) <= 0) {
    ElMessage.warning('该图书暂无可借库存')
    return
  }
  if (selectedBooks.value.some((item) => item.id === row.id)) {
    ElMessage.warning('同一本图书不能重复选择')
    return
  }
  selectedBooks.value.push(toBorrowBook(row))
}

function removeBorrowBook(id) {
  selectedBooks.value = selectedBooks.value.filter((item) => item.id !== id)
}

// 提交批量借书。
async function submitBorrow() {
  if (!selectedReader.value) {
    ElMessage.warning('请先选择读者')
    return
  }
  if (!selectedBooks.value.length) {
    ElMessage.warning('请至少选择一本图书')
    return
  }
  const result = await http.post('/borrow/batch', {
    userId: selectedReader.value.id,
    bookIds: selectedBooks.value.map((item) => item.id)
  })
  ElMessage.success(`借书成功，本次借出 ${result.length} 本`)
  borrowDialog.visible = false
  selectedBooks.value = []
  selectedReader.value = null
  await Promise.all([loadBookOptions(), loadBooks(bookQuery.page), loadReaders(readerQuery.page), loadWarnings(warningQuery.page), loadDashboard()])
}

async function loadReturnRecords(page = returnQuery.page) {
  returnQuery.page = page
  loading.returns = true
  try {
    const data = await http.get('/borrow/return-options', {
      params: pageParams({ ...returnQuery, size: pageSize })
    })
    returnRecords.value = pageContent(data)
    returnTotal.value = pageTotal(data)
    returnSelection.value = []
  } finally {
    loading.returns = false
  }
}

function setReturnSelection(rows) {
  returnSelection.value = rows
}

// 提交批量还书。
async function submitReturns() {
  if (!returnSelection.value.length) {
    ElMessage.warning('请勾选要归还的记录')
    return
  }
  await http.post('/borrow/return', {
    recordIds: returnSelection.value.map((item) => item.id)
  })
  ElMessage.success(`还书成功，本次归还 ${returnSelection.value.length} 本`)
  await Promise.all([loadReturnRecords(), loadDashboard()])
}

async function loadRecords(page = recordQuery.page) {
  recordQuery.page = page
  loading.records = true
  try {
    const params = {
      keyword: recordQuery.keyword,
      status: recordQuery.status,
      borrowStart: recordQuery.borrowStart,
      dueStart: recordQuery.dueRange?.[0],
      dueEnd: recordQuery.dueRange?.[1],
      page,
      size: pageSize
    }
    const data = await http.get('/borrow/records', { params: pageParams(params) })
    records.value = pageContent(data)
    recordTotal.value = pageTotal(data)
  } finally {
    loading.records = false
  }
}

async function openRecordDetail(row) {
  recordDrawer.data = await http.get(`/borrow/records/${row.id}`)
  recordDrawer.visible = true
}

async function loadExtensionRequests(page = extensionQuery.page) {
  extensionQuery.page = page
  loading.extensions = true
  try {
    const data = await http.get('/borrow/extension-requests', {
      params: pageParams({ ...extensionQuery, size: pageSize })
    })
    extensionRequests.value = pageContent(data)
    extensionTotal.value = pageTotal(data)
  } finally {
    loading.extensions = false
  }
}

async function approveExtension(row) {
  await ElMessageBox.confirm(
    `确认同意“${row.readerName || row.readerCard}”将《${row.bookTitle}》延期到 ${row.extensionRequestedDueDate}？`,
    '同意延期',
    { type: 'warning' }
  )
  await http.post(`/borrow/records/${row.id}/extension/approve`)
  ElMessage.success('已同意延期')
  await Promise.all([loadExtensionRequests(extensionQuery.page), loadRecords(recordQuery.page), loadWarnings(warningQuery.page), loadDashboard()])
}

// 加载所有曾经产生逾期天数的记录。
async function loadOverdue(page = overdueQuery.page) {
  overdueQuery.page = page
  loading.overdue = true
  try {
    const data = await http.get('/borrow/overdue', {
      params: pageParams({ ...overdueQuery, size: pageSize })
    })
    overdueRecords.value = pageContent(data)
    overdueTotal.value = pageTotal(data)
  } finally {
    loading.overdue = false
  }
}

async function returnOne(row) {
  await http.post(`/borrow/return/${row.id}`)
  ElMessage.success('已归还')
  await Promise.all([loadOverdue(), loadReturnRecords(returnQuery.page), loadWarnings(warningQuery.page), loadDashboard()])
}

function formatMoney(value) {
  return `¥${moneyValue(value).toFixed(2)}`
}

function moneyValue(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function fineHandled(row) {
  return row?.fineStatus === 'waived' || row?.fineStatus === 'paid'
}

function canPayFine(row) {
  return row?.rawStatus === 'returned' && !fineHandled(row)
}

function canWaiveFine(row) {
  return !fineHandled(row)
}

function fineStatusText(row) {
  return statusText(row?.fineStatus)
}

function fineStatusType(row) {
  return statusType(row?.fineStatus)
}

// 管理员确认罚款缴纳或免罚，后端会同步评估读者冻结状态。
async function updateFine(row, action) {
  const isPaid = action === 'paid'
  if (isPaid && row?.rawStatus !== 'returned') {
    ElMessage.warning('请先办理还书，归还后才能结清逾期罚款')
    return
  }
  await ElMessageBox.confirm(
    isPaid
      ? `确认读者“${row.readerName || row.readerCard}”已缴纳 ${formatMoney(row.fineAmount)} 逾期罚款？确认后系统会结清罚款，并在没有其他逾期或待缴罚款时自动解冻账号。`
      : `确认免除 ${formatMoney(row.fineAmount)} 逾期罚款？免罚后系统会重新评估读者账号状态。`,
    isPaid ? '确认缴纳罚款' : '免除罚款',
    { type: isPaid ? 'warning' : 'info' }
  )
  const result = await http.post(`/borrow/records/${row.id}/fine/${isPaid ? 'paid' : 'waived'}`)
  ElMessage.success(isPaid ? `罚款已确认缴纳 ${formatMoney(result.paidAmount ?? row.fineAmount)}` : '罚款已免除')
  await Promise.all([loadOverdue(overdueQuery.page), loadRecords(recordQuery.page), loadReaders(readerQuery.page), loadDashboard()])
}

// 冻结逾期记录关联的读者账号。
async function freezeOverdueReader(row) {
  await ElMessageBox.confirm(
    `确认冻结读者“${row.readerName || row.readerCard}”？冻结后该读者不能登录自助端，也不能继续借书。`,
    '冻结借阅账户',
    { type: 'warning' }
  )
  await http.post(`/borrow/records/${row.id}/freeze-reader`)
  ElMessage.success('读者借阅账户已冻结')
  await Promise.all([loadOverdue(overdueQuery.page), loadReaders(readerQuery.page), loadDashboard()])
}

async function loadStorage(page = storageQuery.page) {
  storageQuery.page = page
  loading.storage = true
  try {
    const data = await http.get('/storage-locations', {
      params: pageParams({ ...storageQuery, size: pageSize })
    })
    storageRows.value = pageContent(data)
    storageTotal.value = pageTotal(data)
  } finally {
    loading.storage = false
  }
}

async function openStorageDialog() {
  fillShelfParts(storageShelfParts, '')
  syncStorageShelfFromParts()
  storageDialog.form.remark = ''
  storageDialog.visible = true
}

async function saveStorageLocation() {
  syncStorageShelfFromParts()
  if (!storageDialog.form.shelfLocation?.trim()) {
    ElMessage.warning('请填写书架位置')
    return
  }
  await http.post('/storage-locations', {
    shelfLocation: storageDialog.form.shelfLocation,
    remark: storageDialog.form.remark
  })
  ElMessage.success('书架已新增')
  storageDialog.visible = false
  await Promise.all([loadStorage(storageQuery.page), loadShelfLocationOptions()])
}

async function deleteStorageLocation(row) {
  await ElMessageBox.confirm(
    `确认删除书架“${row.shelfLocation}”？只有没有图书库存的空书架才能删除。`,
    '删除书架',
    { type: 'warning' }
  )
  await http.delete(`/storage-locations/${row.id}`)
  ElMessage.success('书架已删除')
  await Promise.all([loadStorage(storageQuery.page), loadShelfLocationOptions()])
}

function bookDetails(data) {
  const book = data?.book || {}
  return [
    ['ISBN', book.isbn],
    ['书名', book.title],
    ['作者', book.author],
    ['分类', book.category],
    ['书架位置', book.shelfLocation],
    ['状态', statusText(book.status)],
    ['馆藏总数', book.totalCount],
    ['可借数量', book.availableCount],
    ['在借数量', data?.activeBorrowCount ?? data?.borrowedCount],
    ['创建时间', formatDateTime(book.createdAt)]
  ]
}

function readerDetails(data) {
  const reader = data?.reader || {}
  return [
    ['借阅证号', reader.username],
    ['姓名', reader.realName],
    ['手机号', reader.phone],
    ['当前借阅数', data?.currentBorrowCount],
    ['状态', statusText(reader.status)],
    ['备注', reader.remark],
    ['创建时间', formatDateTime(reader.createdAt)]
  ]
}

function recordDetails(record) {
  return [
    ['记录编号', record?.id],
    ['批次号', record?.batchNo],
    ['借阅证号', record?.readerCard],
    ['读者姓名', record?.readerName],
    ['手机号', record?.readerPhone],
    ['图书名称', record?.bookTitle],
    ['ISBN', record?.isbn],
    ['作者', record?.bookAuthor],
    ['单册编号', record?.copyCode],
    ['书架位置', record?.copyShelfLocation],
    ['借阅日期', record?.borrowDate],
    ['应还日期', record?.dueDate],
    ['归还日期', record?.returnDate || '-'],
    ['状态', statusText(record?.status)],
    ['延期状态', extensionStatusText(record?.extensionStatus)],
    ['申请延期天数', record?.extensionRequestedDays || '-'],
    ['申请后应还日期', record?.extensionRequestedDueDate || '-'],
    ['延期申请时间', formatDateTime(record?.extensionRequestedAt)],
    ['延期处理时间', formatDateTime(record?.extensionHandledAt)],
    ['逾期天数', record?.overdueDays],
    ['应收罚款', formatMoney(record?.fineAmount)],
    ['罚款状态', fineStatusText(record)],
    ['罚款处理时间', formatDateTime(record?.fineHandledAt)],
    ['罚款备注', record?.fineNote || '-'],
    ['创建时间', formatDateTime(record?.createdAt)]
  ]
}

onMounted(async () => {
  await Promise.all([loadCategoryOptions(), loadShelfLocationOptions()])
  await loadDashboard()
})
</script>

<template>
  <el-container class="shell">
    <el-aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">书</span>
        <span>借阅管理</span>
      </div>
      <el-menu
        :default-active="activePage"
        class="side-menu"
        background-color="transparent"
        text-color="#d7dee4"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menus" :key="item.key" :index="item.key" @click="selectPage(item.key)">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="main-area">
      <el-header class="topbar">
        <div>
          <h2>{{ currentMenu?.label }}</h2>
          <small>{{ pageSubtitles[activePage] }}</small>
        </div>
        <el-button :icon="SwitchButton" @click="emit('logout')">退出</el-button>
      </el-header>

      <el-main class="content">
        <section v-show="activePage === 'dashboard'" v-loading="loading.dashboard">
          <div class="stat-grid">
            <div class="stat-card"><span>图书种类</span><strong>{{ dashboard.bookTypes }}</strong></div>
            <div class="stat-card"><span>馆藏总量</span><strong>{{ dashboard.totalBooks }}</strong></div>
            <div class="stat-card"><span>可借图书</span><strong>{{ dashboard.availableBooks }}</strong></div>
            <div class="stat-card"><span>借阅中</span><strong>{{ dashboard.borrowedBooks }}</strong></div>
            <div class="stat-card"><span>读者数量</span><strong>{{ dashboard.readers }}</strong></div>
            <div class="stat-card"><span>逾期未还</span><strong>{{ dashboard.overdue }}</strong></div>
          </div>
          <div class="panel quick-panel">
            <div class="panel-title"><h3>快捷入口</h3></div>
            <div class="quick-actions">
              <el-button type="primary" :icon="User" @click="quickBorrowFromReaders">从读者借书</el-button>
              <el-button type="primary" plain :icon="Reading" @click="quickBorrowFromBooks">从图书借书</el-button>
              <el-button type="success" :icon="Finished" @click="selectPage('returns')">办理还书</el-button>
              <el-button type="warning" :icon="Reading" @click="quickAddBook">新增图书</el-button>
              <el-button :icon="User" @click="quickAddReader">新增读者</el-button>
            </div>
          </div>
          <div class="panel warning-panel">
            <div class="panel-title">
              <h3>还书预警</h3>
              <el-button type="warning" plain :icon="Warning" @click="selectPage('warnings')">查看全部 {{ dashboardWarningTotal }}</el-button>
            </div>
            <div v-if="dashboardWarnings.length" class="warning-list compact">
              <article v-for="row in dashboardWarnings" :key="row.id" class="warning-card">
                <div>
                  <strong>{{ row.readerName || row.readerCard }}</strong>
                  <span>{{ row.bookTitle }}</span>
                </div>
                <el-tag :type="warningTagType(row.warningLevel)">{{ row.warningText }}</el-tag>
              </article>
            </div>
            <el-empty v-else description="暂无还书预警" />
          </div>
          <div class="dashboard-charts">
            <section class="panel chart-panel chart-wide">
              <div class="panel-title">
                <h3>近 7 天借还趋势</h3>
                <el-button :icon="Refresh" @click="loadDashboard()">刷新</el-button>
              </div>
              <div class="trend-summary">
                <div><span>7 日借出</span><strong>{{ trendBorrowTotal }}</strong></div>
                <div><span>7 日归还</span><strong>{{ trendReturnTotal }}</strong></div>
              </div>
              <div class="trend-chart">
                <div class="trend-svg-wrap">
                  <div v-if="!hasTrendActivity" class="chart-empty">近 7 天暂无借还数据</div>
                  <svg :viewBox="`0 0 ${trendChart.width} ${trendChart.height}`" role="img" aria-label="近 7 天借还趋势">
                    <template v-for="tick in trendYTicks" :key="`trend-y-${tick}`">
                      <line :x1="trendChart.left" :y1="trendY(tick)" :x2="trendChart.width - trendChart.right" :y2="trendY(tick)" :class="tick === 0 ? 'chart-axis' : 'chart-grid'" />
                      <text :x="trendChart.left - 8" :y="trendY(tick)" text-anchor="end" dominant-baseline="middle" class="axis-text">{{ tick }}</text>
                    </template>
                    <line :x1="trendChart.left" :y1="trendChart.top" :x2="trendChart.left" :y2="trendChart.height - trendChart.bottom" class="chart-axis" />
                    <template v-for="(item, index) in dashboard.borrowTrend" :key="`trend-x-${item.date}`">
                      <line :x1="trendX(index, dashboard.borrowTrend.length)" :y1="trendChart.top" :x2="trendX(index, dashboard.borrowTrend.length)" :y2="trendChart.height - trendChart.bottom" class="chart-x-grid" />
                      <text :x="trendX(index, dashboard.borrowTrend.length)" :y="trendChart.height - 12" text-anchor="middle" class="axis-text">{{ shortDate(item.date) }}</text>
                    </template>
                    <polyline class="trend-line borrow-line" :points="trendBorrowPoints" />
                    <polyline class="trend-line return-line" :points="trendReturnPoints" />
                    <circle v-for="(item, index) in dashboard.borrowTrend" :key="`borrow-${item.date}`" :cx="trendX(index, dashboard.borrowTrend.length)" :cy="trendY(item.borrowCount)" r="3.2" class="borrow-dot" />
                    <circle v-for="(item, index) in dashboard.borrowTrend" :key="`return-${item.date}`" :cx="trendX(index, dashboard.borrowTrend.length)" :cy="trendY(item.returnCount)" r="3.2" class="return-dot" />
                  </svg>
                </div>
                <div class="chart-legend inline">
                  <span><i class="legend-mark borrow"></i>借出</span>
                  <span><i class="legend-mark return"></i>归还</span>
                </div>
              </div>
            </section>

            <section class="panel chart-panel">
              <div class="panel-title"><h3>分类馆藏占比</h3></div>
              <div v-if="hasCategoryStats" class="pie-layout">
                <div class="pie-chart" :style="categoryPieStyle">
                  <strong>{{ categoryChartTotal }}</strong>
                  <span>册</span>
                </div>
                <div class="chart-legend">
                  <span v-for="(item, index) in dashboard.categoryStats" :key="item.name">
                    <i class="legend-mark" :style="{ background: chartColor(index) }"></i>
                    {{ item.name }} {{ item.total }}
                  </span>
                </div>
              </div>
              <div v-else class="chart-empty block">暂无馆藏分类数据</div>
            </section>

            <section class="panel chart-panel chart-wide">
              <div class="panel-title">
                <h3>分类馆藏明细</h3>
              </div>
              <div v-if="hasCategoryStats" class="audit-list">
                <div v-for="item in categoryAuditRows" :key="item.name" class="inventory-audit-row">
                  <div class="bar-row-title">
                    <strong>{{ item.name }}</strong>
                    <span>馆藏 {{ item.total }} 册</span>
                  </div>
                  <div class="stacked-track">
                    <span v-if="item.available" class="stack-segment available" :style="{ width: `${segmentWidth(item.available, item.total)}%` }"></span>
                    <span v-if="item.borrowed" class="stack-segment borrowed" :style="{ width: `${segmentWidth(item.borrowed, item.total)}%` }"></span>
                    <span v-if="item.diff" class="stack-segment diff" :style="{ width: `${segmentWidth(Math.abs(item.diff), Math.max(item.total, item.available + item.borrowed + Math.abs(item.diff)))}%` }"></span>
                  </div>
                  <div class="audit-row-foot">
                    <span><i class="legend-mark total"></i>馆藏 {{ item.total }}</span>
                    <span><i class="legend-mark available"></i>可借 {{ item.available }}</span>
                    <span><i class="legend-mark borrowed"></i>借出 {{ item.borrowed }}</span>
                  </div>
                </div>
              </div>
              <div v-else class="chart-empty block">暂无馆藏分类数据</div>
            </section>

            <section class="panel chart-panel">
              <div class="panel-title">
                <h3>借阅状态核对</h3>
                <span :class="['audit-pill', statusDiffType(statusDiff)]">{{ statusDiffText(statusDiff) }}</span>
              </div>
              <div v-if="hasStatusStats" class="status-audit">
                <div class="status-total-card">
                  <strong>{{ statusExpectedTotal }}</strong>
                  <span>应统计记录</span>
                  <small>状态合计 {{ statusTotal }} 条</small>
                </div>
                <div class="status-audit-main">
                  <div class="stacked-track status-stacked">
                    <template v-for="item in statusChartRows" :key="item.name">
                      <span v-if="item.value" class="stack-segment" :style="{ width: `${segmentWidth(item.value, Math.max(statusExpectedTotal, statusTotal))}%`, background: item.color }"></span>
                    </template>
                  </div>
                  <div class="status-detail-grid">
                    <div v-for="item in statusChartRows" :key="item.name" class="status-detail-item">
                      <span><i class="legend-mark" :style="{ background: item.color }"></i>{{ item.label }}</span>
                      <strong>{{ item.value }} 条</strong>
                      <small>{{ item.percent }}%</small>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="chart-empty block">暂无借阅记录数据</div>
            </section>
          </div>
        </section>

        <section v-show="activePage === 'warnings'">
          <div class="panel toolbar">
            <el-input v-model="warningQuery.keyword" :prefix-icon="Search" clearable placeholder="读者 / 图书 / 单册编号" @keyup.enter="loadWarnings(0)" />
            <el-select v-model="warningQuery.days" placeholder="预警范围" style="width: 150px" @change="loadWarnings(0)">
              <el-option v-for="item in warningRangeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="loadWarnings(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetWarnings">重置</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="warningRecords" v-loading="loading.warnings" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="readerCard" label="借阅证号" width="130" />
              <el-table-column prop="readerName" label="读者" width="120" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="190" show-overflow-tooltip />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
              <el-table-column prop="borrowDate" label="借阅日期" width="120" />
              <el-table-column prop="dueDate" label="应还日期" width="120" />
              <el-table-column label="预警" width="130">
                <template #default="{ row }">
                  <el-tag :type="warningTagType(row.warningLevel)">{{ row.warningText }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button size="small" @click="openRecordDetail(row)">详情</el-button>
                    <el-button size="small" type="success" @click="returnOne(row)">还书</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="warningQuery.page + 1" :page-size="pageSize" :total="warningTotal" @current-change="(page) => setPage(warningQuery, loadWarnings, page)" />
          </div>
        </section>

        <section v-show="activePage === 'categories'">
          <div class="panel toolbar">
            <el-input v-model="categoryQuery.keyword" :prefix-icon="Search" clearable placeholder="分类名称" @keyup.enter="loadCategories(0)" />
            <el-button type="primary" :icon="Search" @click="loadCategories(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetCategories">重置</el-button>
            <el-button type="success" @click="openCategory()">新增分类</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="categories" v-loading="loading.categories" border>
              <el-table-column prop="id" label="编号" width="90" />
              <el-table-column prop="name" label="分类名称" min-width="160" />
              <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
              <el-table-column label="图书数量" width="120">
                <template #default="{ row }">
                  <el-button link type="primary" :disabled="row.bookCount <= 0" @click="openCategoryBooks(row)">
                    {{ row.bookCount }}
                  </el-button>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="180">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="230" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button size="small" @click="openCategory(row)">编辑</el-button>
                    <el-button size="small" type="primary" @click="openMergeCategory(row)">合并</el-button>
                    <el-button size="small" type="danger" @click="deleteCategory(row)">删除</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="categoryQuery.page + 1" :page-size="pageSize" :total="categoryTotal" @current-change="(page) => setPage(categoryQuery, loadCategories, page)" />
          </div>
        </section>

        <section v-show="activePage === 'books'">
          <div class="panel toolbar">
            <el-input v-model="bookQuery.keyword" :prefix-icon="Search" clearable placeholder="ISBN / 书名 / 作者" @keyup.enter="loadBooks(0)" />
            <el-select v-model="bookQuery.categoryId" clearable placeholder="分类">
              <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <el-select v-model="bookQuery.status" clearable placeholder="状态">
              <el-option label="启用" value="enabled" />
              <el-option label="停用" value="disabled" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="loadBooks(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery(bookQuery, loadBooks)">重置</el-button>
            <el-button type="success" @click="openBook()">新增图书</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="books" v-loading="loading.books" border>
              <el-table-column prop="isbn" label="ISBN" width="150" />
              <el-table-column prop="title" label="书名" min-width="180" show-overflow-tooltip />
              <el-table-column prop="author" label="作者" width="130" />
              <el-table-column prop="category" label="分类" width="120" />
              <el-table-column prop="shelfLocation" label="书架位置" width="130" />
              <el-table-column prop="totalCount" label="馆藏" width="80" />
              <el-table-column prop="availableCount" label="可借" width="80" />
              <el-table-column prop="activeBorrowCount" label="在借" width="80" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="360" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button size="small" @click="openBookDetail(row)">详情</el-button>
                    <el-button size="small" type="primary" :disabled="row.status === 'disabled' || row.availableCount <= 0" @click="openBorrowFromBook(row)">借书</el-button>
                    <el-button size="small" @click="openBook(row)">编辑</el-button>
                    <el-button size="small" :type="row.status === 'disabled' ? 'success' : 'warning'" @click="toggleBook(row)">
                      {{ row.status === 'disabled' ? '启用' : '停用' }}
                    </el-button>
                    <el-button size="small" type="danger" @click="deleteBook(row)">删除</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="bookQuery.page + 1" :page-size="pageSize" :total="bookTotal" @current-change="(page) => setPage(bookQuery, loadBooks, page)" />
          </div>
        </section>

        <section v-show="activePage === 'readers'">
          <div class="panel toolbar">
            <el-input v-model="readerQuery.keyword" :prefix-icon="Search" clearable placeholder="借阅证号 / 姓名 / 手机号" @keyup.enter="loadReaders(0)" />
            <el-select v-model="readerQuery.status" clearable placeholder="状态">
              <el-option label="启用" value="enabled" />
              <el-option label="停用" value="disabled" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="loadReaders(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery(readerQuery, loadReaders)">重置</el-button>
            <el-button type="success" @click="openReader()">新增读者</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="readers" v-loading="loading.readers" border>
              <el-table-column prop="username" label="借阅证号" width="130" />
              <el-table-column prop="realName" label="姓名" width="120" />
              <el-table-column prop="phone" label="手机号" width="140" />
              <el-table-column prop="currentBorrowCount" label="当前借阅数" width="120" />
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="180">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="310" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button size="small" @click="openReaderDetail(row)">详情</el-button>
                    <el-button size="small" type="primary" :disabled="row.status === 'disabled'" @click="openBorrowFromReader(row)">借书</el-button>
                    <el-button size="small" @click="openReader(row)">编辑</el-button>
                    <el-button size="small" :type="row.status === 'disabled' ? 'success' : 'warning'" @click="toggleReader(row)">
                      {{ row.status === 'disabled' ? '启用' : '停用' }}
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="readerQuery.page + 1" :page-size="pageSize" :total="readerTotal" @current-change="(page) => setPage(readerQuery, loadReaders, page)" />
          </div>
        </section>

        <section v-show="activePage === 'returns'">
          <div class="scope-tip">
            <strong>未还书办理</strong>
            <span>这里仅显示当前还没归还的记录，用于勾选后办理还书。</span>
          </div>
          <div class="panel toolbar">
            <el-input v-model="returnQuery.keyword" :prefix-icon="Search" clearable placeholder="读者 / 图书 / 单册编号" @keyup.enter="loadReturnRecords(0)" />
            <el-button type="primary" :icon="Search" @click="loadReturnRecords(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery(returnQuery, loadReturnRecords)">重置</el-button>
            <el-button type="success" :disabled="!returnSelection.length" @click="submitReturns">归还选中</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="returnRecords" v-loading="loading.returns" border @selection-change="setReturnSelection">
              <el-table-column type="selection" width="48" />
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="readerCard" label="借阅证号" width="130" />
              <el-table-column prop="readerName" label="读者" width="120" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="190" show-overflow-tooltip />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
              <el-table-column prop="borrowDate" label="借阅日期" width="120" />
              <el-table-column prop="dueDate" label="应还日期" width="120" />
              <el-table-column prop="overdueDays" label="逾期天数" width="100" />
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="returnQuery.page + 1" :page-size="pageSize" :total="returnTotal" @current-change="(page) => setPage(returnQuery, loadReturnRecords, page)" />
          </div>
        </section>

        <section v-show="activePage === 'records'">
          <div class="scope-tip">
            <strong>全部借阅记录</strong>
            <span>这里显示所有历史记录，已归还的记录只用于查询，不能再次办理还书。</span>
          </div>
          <div class="panel toolbar">
            <el-input v-model="recordQuery.keyword" :prefix-icon="Search" clearable placeholder="读者 / 图书 / 编号" @keyup.enter="loadRecords(0)" />
            <el-select v-model="recordQuery.status" clearable placeholder="状态">
              <el-option label="借阅中" value="borrowed" />
              <el-option label="已归还" value="returned" />
              <el-option label="已逾期" value="overdue" />
            </el-select>
            <el-date-picker v-model="recordQuery.borrowStart" type="date" value-format="YYYY-MM-DD" placeholder="借阅开始" :disabled-date="disableFutureDate" />
            <el-date-picker v-model="recordQuery.dueRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="应还开始" end-placeholder="应还结束" />
            <el-button type="primary" :icon="Search" @click="loadRecords(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery(recordQuery, loadRecords)">重置</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="records" v-loading="loading.records" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="batchNo" label="批次号" width="150" />
              <el-table-column prop="readerCard" label="借阅证号" width="130" />
              <el-table-column prop="readerName" label="读者" width="120" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="190" show-overflow-tooltip />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
              <el-table-column prop="borrowDate" label="借阅日期" width="120" />
              <el-table-column prop="dueDate" label="应还日期" width="120" />
              <el-table-column prop="returnDate" label="归还日期" width="120" />
              <el-table-column label="延期状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="extensionStatusType(row.extensionStatus)">{{ extensionStatusText(row.extensionStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button size="small" @click="openRecordDetail(row)">详情</el-button>
                    <el-button
                      size="small"
                      type="warning"
                      :disabled="row.extensionStatus !== 'pending'"
                      @click="approveExtension(row)"
                    >
                      同意延期
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="recordQuery.page + 1" :page-size="pageSize" :total="recordTotal" @current-change="(page) => setPage(recordQuery, loadRecords, page)" />
          </div>
        </section>

        <section v-show="activePage === 'extensions'">
          <div class="panel toolbar">
            <el-input v-model="extensionQuery.keyword" :prefix-icon="Search" clearable placeholder="读者 / 图书 / 单册编号" @keyup.enter="loadExtensionRequests(0)" />
            <el-select v-model="extensionQuery.extensionStatus" clearable placeholder="处理状态">
              <el-option label="待处理" value="pending" />
              <el-option label="已同意" value="approved" />
              <el-option label="已拒绝" value="rejected" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="loadExtensionRequests(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery(extensionQuery, loadExtensionRequests)">重置</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="extensionRequests" v-loading="loading.extensions" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="readerCard" label="借阅证号" width="130" />
              <el-table-column prop="readerName" label="读者" width="120" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="190" show-overflow-tooltip />
              <el-table-column prop="borrowDate" label="借阅日期" width="120" />
              <el-table-column prop="dueDate" label="当前应还" width="120" />
              <el-table-column prop="extensionRequestedDays" label="申请天数" width="100" />
              <el-table-column prop="extensionRequestedDueDate" label="申请应还" width="120" />
              <el-table-column label="申请时间" width="180">
                <template #default="{ row }">{{ formatDateTime(row.extensionRequestedAt) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="extensionStatusType(row.extensionStatus)">{{ extensionStatusText(row.extensionStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button size="small" @click="openRecordDetail(row)">详情</el-button>
                    <el-button size="small" type="warning" :disabled="row.extensionStatus !== 'pending'" @click="approveExtension(row)">同意延期</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="extensionQuery.page + 1" :page-size="pageSize" :total="extensionTotal" @current-change="(page) => setPage(extensionQuery, loadExtensionRequests, page)" />
          </div>
        </section>

        <section v-show="activePage === 'overdue'">
          <div class="overdue-mode-bar">
            <div>
              <strong>{{ overdueMode === 'fine' ? '罚款与冻结处理' : '逾期记录查询' }}</strong>
              <span>{{ overdueMode === 'fine' ? '处理逾期罚款，并冻结违规读者账号' : '查看所有曾经逾期的记录，未还记录可办理归还' }}</span>
            </div>
            <el-button type="primary" plain @click="overdueModeDialog.visible = true">切换处理方式</el-button>
          </div>
          <div class="panel toolbar">
            <el-input v-model="overdueQuery.keyword" :prefix-icon="Search" clearable placeholder="读者 / 图书 / 单册编号" @keyup.enter="loadOverdue(0)" />
            <el-button type="primary" :icon="Search" @click="loadOverdue(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery(overdueQuery, loadOverdue)">重置</el-button>
          </div>
          <div class="panel table-panel">
            <el-table v-if="overdueMode === 'records'" :data="overdueRecords" v-loading="loading.overdue" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="readerCard" label="借阅证号" width="130" />
              <el-table-column prop="readerName" label="读者" width="120" />
              <el-table-column prop="readerPhone" label="手机号" width="140" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="190" show-overflow-tooltip />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
              <el-table-column prop="dueDate" label="应还日期" width="120" />
              <el-table-column prop="returnDate" label="归还日期" width="120" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="overdueDays" label="逾期天数" width="100" />
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button size="small" @click="openRecordDetail(row)">详情</el-button>
                    <el-button size="small" type="success" :disabled="row.rawStatus === 'returned'" @click="returnOne(row)">还书</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <div v-else v-loading="loading.overdue" class="fine-board">
              <el-empty v-if="!overdueRecords.length" description="暂无可处理的逾期记录" />
              <article v-for="row in overdueRecords" v-else :key="row.id" class="fine-card">
                <div class="fine-card-main">
                  <div class="fine-card-title">
                    <strong>{{ row.readerName || row.readerCard }}</strong>
                    <el-tag size="small" :type="statusType(row.readerStatus)">{{ row.readerStatus === 'disabled' ? '已冻结' : statusText(row.readerStatus) }}</el-tag>
                  </div>
                  <div class="fine-card-meta">
                    <span>借阅证号：{{ row.readerCard }}</span>
                    <span>图书：{{ row.bookTitle }}</span>
                    <span>应还：{{ row.dueDate }}</span>
                    <span>归还：{{ row.returnDate || '未归还' }}</span>
                    <span>逾期：{{ row.overdueDays }} 天</span>
                  </div>
                </div>
                <div class="fine-card-side">
                  <div class="fine-amount">
                    <span>应缴罚款</span>
                    <strong>{{ formatMoney(row.fineAmount) }}</strong>
                    <el-tag size="small" :type="fineStatusType(row)">{{ fineStatusText(row) }}</el-tag>
                  </div>
                  <div class="fine-balance">
                    <span>缴纳前账号默认冻结</span>
                    <span v-if="fineHandled(row)">已结清，系统会重新评估账号状态</span>
                    <small v-else-if="row.rawStatus !== 'returned'">请先还书，再结清罚款</small>
                    <small v-else>管理员确认已缴纳后结清罚款</small>
                  </div>
                  <div class="fine-card-actions">
                    <el-button type="warning" :disabled="!canPayFine(row)" @click="updateFine(row, 'paid')">确认缴纳</el-button>
                    <el-button :disabled="!canWaiveFine(row)" @click="updateFine(row, 'waived')">免罚</el-button>
                    <el-button type="danger" :disabled="row.readerStatus === 'disabled'" @click="freezeOverdueReader(row)">冻结账号</el-button>
                    <el-button type="success" :disabled="row.rawStatus === 'returned'" @click="returnOne(row)">还书</el-button>
                    <el-button @click="openRecordDetail(row)">详情</el-button>
                  </div>
                </div>
              </article>
            </div>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="overdueQuery.page + 1" :page-size="pageSize" :total="overdueTotal" @current-change="(page) => setPage(overdueQuery, loadOverdue, page)" />
          </div>
        </section>

        <section v-show="activePage === 'storage'">
          <div class="panel toolbar">
            <el-input v-model="storageQuery.keyword" :prefix-icon="Search" clearable placeholder="书架位置 / 图书" @keyup.enter="loadStorage(0)" />
            <el-button type="primary" :icon="Search" @click="loadStorage(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery(storageQuery, loadStorage)">重置</el-button>
            <el-button type="success" @click="openStorageDialog">新增书架</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="storageRows" v-loading="loading.storage" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="shelfLocation" label="书架位置" width="150" />
              <el-table-column prop="bookTypes" label="图书种类" width="110" />
              <el-table-column prop="totalCount" label="馆藏" width="90" />
              <el-table-column prop="availableCount" label="可借" width="90" />
              <el-table-column prop="borrowedCount" label="借出" width="90" />
              <el-table-column prop="bookTitles" label="当前图书" min-width="320" show-overflow-tooltip />
              <el-table-column label="更新时间" width="180">
                <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="110" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button size="small" type="danger" :disabled="!row.canDelete" @click="deleteStorageLocation(row)">删除</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="storageQuery.page + 1" :page-size="pageSize" :total="storageTotal" @current-change="(page) => setPage(storageQuery, loadStorage, page)" />
          </div>
        </section>
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="borrowDialog.visible" :title="borrowDialogTitle" width="980px" append-to-body @closed="closeBorrowDialog">
    <div class="borrow-dialog-body">
      <div class="selection-bar borrow-selection">
        <template v-if="borrowDialog.mode === 'reader'">
          <span class="selection-label">当前读者</span>
          <el-tag v-if="selectedReader" type="success">
            {{ selectedReader.cardNumber }} {{ selectedReader.realName }}
          </el-tag>
          <span class="selection-label">选择图书</span>
          <el-tag v-for="book in selectedBooks" :key="book.id" closable @close="removeBorrowBook(book.id)">
            {{ book.isbn }} {{ book.title }}
          </el-tag>
          <span v-if="!selectedBooks.length" class="selection-empty">请在下方选择要借的图书</span>
        </template>
        <template v-else>
          <span class="selection-label">当前图书</span>
          <el-tag v-for="book in selectedBooks" :key="book.id">
            {{ book.isbn }} {{ book.title }}
          </el-tag>
          <span class="selection-label">选择读者</span>
          <el-tag v-if="selectedReader" type="success" closable @close="selectedReader = null">
            {{ selectedReader.cardNumber }} {{ selectedReader.realName }}
          </el-tag>
          <span v-else class="selection-empty">请在下方选择借书读者</span>
        </template>
      </div>

      <div class="toolbar borrow-dialog-toolbar">
        <template v-if="borrowDialog.mode === 'book'">
          <el-input v-model="borrowReaderKeyword" :prefix-icon="Search" clearable placeholder="搜索读者" @keyup.enter="searchReaderOptions" />
          <el-button :icon="Search" @click="searchReaderOptions">查读者</el-button>
        </template>
        <template v-else>
          <el-input v-model="borrowBookKeyword" :prefix-icon="Search" clearable placeholder="搜索图书" @keyup.enter="searchBookOptions" />
          <el-button :icon="Search" @click="searchBookOptions">查图书</el-button>
        </template>
      </div>

      <div class="borrow-dialog-grid borrow-dialog-grid-single">
        <section v-if="borrowDialog.mode === 'book'" class="dialog-table-section">
          <h3>读者候选</h3>
          <el-table :data="readerOptions" v-loading="loading.borrowReaders" border height="280">
            <el-table-column prop="cardNumber" label="借阅证号" width="130" />
            <el-table-column prop="realName" label="姓名" width="110" />
            <el-table-column prop="phone" label="手机号" width="140" />
            <el-table-column prop="currentBorrowCount" label="当前未还" width="100" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="selectedReader = toBorrowReader(row)">选择</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" layout="prev, pager, next, total" :current-page="borrowReaderQuery.page + 1" :page-size="pageSize" :total="borrowReaderTotal" @current-change="(page) => setPage(borrowReaderQuery, loadReaderOptions, page)" />
        </section>

        <section v-if="borrowDialog.mode === 'reader'" class="dialog-table-section">
          <h3>图书候选</h3>
          <el-table :data="bookOptions" v-loading="loading.borrowBooks" border height="280">
            <el-table-column prop="isbn" label="ISBN" width="140" />
            <el-table-column prop="title" label="书名" min-width="170" show-overflow-tooltip />
            <el-table-column prop="author" label="作者" width="100" />
            <el-table-column prop="availableCount" label="可借" width="70" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" :disabled="row.status === 'disabled' || row.availableCount <= 0" @click="addBorrowBook(row)">加入</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" layout="prev, pager, next, total" :current-page="borrowBookQuery.page + 1" :page-size="pageSize" :total="borrowBookTotal" @current-change="(page) => setPage(borrowBookQuery, loadBookOptions, page)" />
        </section>
      </div>
    </div>
    <template #footer>
      <el-button @click="borrowDialog.visible = false">取消</el-button>
      <el-button type="primary" :disabled="!selectedReader || !selectedBooks.length" @click="submitBorrow">提交借阅</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="overdueModeDialog.visible" title="选择逾期处理方式" width="560px" append-to-body>
    <div class="mode-choice">
      <button class="mode-choice-button" type="button" @click="chooseOverdueMode('records')">
        <strong>逾期记录查询</strong>
        <span>查看曾经逾期记录，未还记录可办理详情查看和还书。</span>
      </button>
      <button class="mode-choice-button warning" type="button" @click="chooseOverdueMode('fine')">
        <strong>罚款与冻结处理</strong>
        <span>收取或免除逾期罚款，并冻结违规读者账号。</span>
      </button>
    </div>
  </el-dialog>

  <el-dialog v-model="storageDialog.visible" title="新增书架" width="560px" append-to-body>
    <el-form label-position="top" :model="storageDialog.form">
      <el-form-item label="书架位置">
        <div class="shelf-picker">
          <el-select v-model="storageShelfParts.area" filterable allow-create default-first-option placeholder="区" @change="syncStorageShelfFromParts">
            <el-option v-for="area in shelfAreaOptions" :key="area" :label="area" :value="area" />
          </el-select>
          <el-input-number v-model="storageShelfParts.row" :min="1" :max="SHELF_NUMBER_MAX" controls-position="right" @change="syncStorageShelfFromParts" />
          <el-input-number v-model="storageShelfParts.slot" :min="1" :max="SHELF_NUMBER_MAX" controls-position="right" @change="syncStorageShelfFromParts" />
        </div>
        <div class="shelf-preview">当前书架：{{ storageDialog.form.shelfLocation }}</div>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="storageDialog.form.remark" type="textarea" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="storageDialog.visible = false">取消</el-button>
      <el-button type="primary" @click="saveStorageLocation">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="categoryDialog.visible" :title="categoryDialog.editing ? '编辑分类' : '新增分类'" width="520px">
    <el-form label-position="top">
      <el-form-item label="分类名称">
        <el-input v-model="categoryDialog.form.name" />
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="categoryDialog.form.description" type="textarea" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="categoryDialog.visible = false">取消</el-button>
      <el-button type="primary" @click="saveCategory">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="categoryBooksDialog.visible" :title="`分类图书：${categoryBooksDialog.category?.name || ''}`" width="960px">
    <div class="toolbar dialog-toolbar">
      <el-input v-model="categoryBooksDialog.query.keyword" :prefix-icon="Search" clearable placeholder="ISBN / 书名 / 作者 / 书架" @keyup.enter="openCategoryBooks(categoryBooksDialog.category, 0)" />
      <el-button type="primary" :icon="Search" @click="openCategoryBooks(categoryBooksDialog.category, 0)">查询</el-button>
      <el-button :icon="Refresh" @click="categoryBooksDialog.query.keyword = ''; openCategoryBooks(categoryBooksDialog.category, 0)">重置</el-button>
    </div>
    <el-table :data="categoryBooksDialog.rows" v-loading="categoryBooksLoading" border height="380">
      <el-table-column prop="isbn" label="ISBN" width="150" />
      <el-table-column prop="title" label="书名" min-width="180" show-overflow-tooltip />
      <el-table-column prop="author" label="作者" width="120" />
      <el-table-column prop="shelfLocation" label="书架位置" width="120" />
      <el-table-column prop="totalCount" label="馆藏" width="80" />
      <el-table-column prop="availableCount" label="可借" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" layout="prev, pager, next, total" :current-page="categoryBooksDialog.query.page + 1" :page-size="pageSize" :total="categoryBooksDialog.total" @current-change="setCategoryBooksPage" />
  </el-dialog>

  <el-dialog v-model="categoryDeleteDialog.visible" title="删除分类并迁移图书" width="520px">
    <div v-if="categoryDeleteDialog.row" class="extension-summary">
      <p>分类“{{ categoryDeleteDialog.row.name }}”下还有 {{ categoryDeleteDialog.row.bookCount }} 本图书。</p>
      <p>删除前需要把这些图书迁移到另一个分类。</p>
      <el-form label-position="top">
        <el-form-item label="迁移到">
          <el-select v-model="categoryDeleteDialog.targetCategoryId" placeholder="请选择目标分类" style="width: 100%">
            <el-option v-for="item in targetCategoryOptions(categoryDeleteDialog.row.id)" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="categoryDeleteDialog.visible = false">取消</el-button>
      <el-button type="danger" @click="confirmDeleteCategory">迁移并删除</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="categoryMergeDialog.visible" title="合并分类" width="520px">
    <div v-if="categoryMergeDialog.row" class="extension-summary">
      <p>把“{{ categoryMergeDialog.row.name }}”下的图书合并到目标分类，原分类会被删除。</p>
      <el-form label-position="top">
        <el-form-item label="合并到">
          <el-select v-model="categoryMergeDialog.targetCategoryId" placeholder="请选择目标分类" style="width: 100%">
            <el-option v-for="item in targetCategoryOptions(categoryMergeDialog.row.id)" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="categoryMergeDialog.visible = false">取消</el-button>
      <el-button type="primary" @click="confirmMergeCategory">确认合并</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="bookDialog.visible" :title="bookDialog.editing ? '编辑图书' : '新增图书'" width="720px">
    <el-form label-position="top" :model="bookDialog.form">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="ISBN"><el-input v-model="bookDialog.form.isbn" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="书名"><el-input v-model="bookDialog.form.title" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="作者"><el-input v-model="bookDialog.form.author" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="分类">
            <el-select v-model="bookDialog.form.categoryId" style="width: 100%">
              <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="书架位置">
            <div class="shelf-picker">
              <el-select v-model="bookShelfParts.area" filterable allow-create default-first-option placeholder="区" @change="syncBookShelfFromParts">
                <el-option v-for="area in shelfAreaOptions" :key="area" :label="area" :value="area" />
              </el-select>
              <el-input-number v-model="bookShelfParts.row" :min="1" :max="SHELF_NUMBER_MAX" controls-position="right" @change="syncBookShelfFromParts" />
              <el-input-number v-model="bookShelfParts.slot" :min="1" :max="SHELF_NUMBER_MAX" controls-position="right" @change="syncBookShelfFromParts" />
            </div>
            <div class="shelf-preview">当前书架：{{ bookDialog.form.shelfLocation }}</div>
          </el-form-item>
        </el-col>
        <el-col v-if="!bookDialog.editing" :span="12">
          <el-form-item label="初始馆藏数量">
            <el-input-number v-model="bookDialog.form.totalCount" :min="1" :max="BOOK_STOCK_MAX" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-select v-model="bookDialog.form.status" style="width: 100%">
              <el-option label="启用" value="enabled" />
              <el-option label="停用" value="disabled" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="bookDialog.visible = false">取消</el-button>
      <el-button type="primary" @click="saveBook">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="readerDialog.visible" :title="readerDialog.editing ? '编辑读者' : '新增读者'" width="620px">
    <el-form label-position="top" :model="readerDialog.form">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="姓名"><el-input v-model="readerDialog.form.realName" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="手机号"><el-input v-model="readerDialog.form.phone" maxlength="11" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="readerDialog.editing ? '重置密码' : '登录密码'">
            <el-input
              v-model="readerDialog.form.password"
              type="password"
              show-password
              :placeholder="readerDialog.editing ? '留空则不修改密码' : '至少 6 位'"
            >
              <template #append>
                <el-button :icon="Key" @click="generateReaderPassword">生成</el-button>
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-select v-model="readerDialog.form.status" style="width: 100%">
              <el-option label="启用" value="enabled" />
              <el-option label="停用" value="disabled" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注"><el-input v-model="readerDialog.form.remark" type="textarea" /></el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="readerDialog.visible = false">取消</el-button>
      <el-button type="primary" @click="saveReader">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="readerPasswordResult.visible" title="读者登录信息" width="520px">
    <div class="password-result">
      <el-alert
        title="请将以下信息告知读者。读者登录后，可在登录页点击“修改密码”换成自己的密码。"
        type="warning"
        :closable="false"
      />
      <div class="credential-list">
        <div class="credential-item">
          <span>读者</span>
          <strong>{{ readerPasswordResult.realName || '-' }}</strong>
        </div>
        <div class="credential-item">
          <span>借阅证号</span>
          <strong>{{ readerPasswordResult.readerCard }}</strong>
        </div>
        <div class="credential-item">
          <span>临时密码</span>
          <strong class="credential-value">{{ readerPasswordResult.password }}</strong>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button :icon="CopyDocument" @click="copyReaderLoginNotice">复制登录信息</el-button>
      <el-button type="primary" @click="readerPasswordResult.visible = false">知道了</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="bookDrawer.visible" title="图书详情" size="62%" @closed="closeBookDetail">
    <div v-if="bookDrawer.data">
      <div class="detail-list">
        <div v-for="[label, value] in bookDetails(bookDrawer.data)" :key="label" class="detail-item">
          <span>{{ label }}</span>
          <strong>{{ value || '-' }}</strong>
        </div>
      </div>
      <div class="drawer-section">
        <h4>书架分布</h4>
        <el-table :data="pagedBookStorageRows" border>
          <el-table-column prop="shelfLocation" label="书架位置" />
          <el-table-column prop="totalCount" label="馆藏" width="90" />
          <el-table-column prop="availableCount" label="可借" width="90" />
          <el-table-column prop="borrowedCount" label="已借" width="90" />
        </el-table>
        <el-pagination class="pager" layout="prev, pager, next, total" :current-page="bookStorageQuery.page + 1" :page-size="pageSize" :total="bookStorageTotal" @current-change="(page) => setLocalPage(bookStorageQuery, page)" />
      </div>
      <div class="drawer-section">
        <h4>单册状态</h4>
        <el-table :data="pagedBookCopyRows" border>
          <el-table-column prop="copyCode" label="单册编号" min-width="190" show-overflow-tooltip />
          <el-table-column prop="shelfLocation" label="书架位置" />
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="currentReaderName" label="当前读者" />
        </el-table>
        <el-pagination class="pager" layout="prev, pager, next, total" :current-page="bookCopyQuery.page + 1" :page-size="pageSize" :total="bookCopyTotal" @current-change="(page) => setLocalPage(bookCopyQuery, page)" />
      </div>
    </div>
  </el-drawer>

  <el-drawer v-model="readerDrawer.visible" title="读者详情" size="62%" append-to-body @closed="closeReaderDetail">
    <div v-loading="readerDrawer.loading" class="drawer-body">
      <el-empty v-if="readerDrawer.error" :description="readerDrawer.error" />
      <div v-else-if="readerDrawer.data">
      <div class="detail-list">
        <div v-for="[label, value] in readerDetails(readerDrawer.data)" :key="label" class="detail-item">
          <span>{{ label }}</span>
          <strong>{{ value === 0 ? 0 : (value || '-') }}</strong>
        </div>
      </div>
      <div class="drawer-section">
        <h4>当前未还</h4>
        <el-table :data="pagedReaderCurrentRows" border empty-text="暂无未还记录">
          <el-table-column prop="bookTitle" label="图书" />
          <el-table-column prop="copyCode" label="单册编号" min-width="190" show-overflow-tooltip />
          <el-table-column prop="borrowDate" label="借阅日期" />
          <el-table-column prop="dueDate" label="应还日期" />
          <el-table-column prop="overdueDays" label="逾期天数" />
        </el-table>
        <el-pagination class="pager" layout="prev, pager, next, total" :current-page="readerCurrentQuery.page + 1" :page-size="pageSize" :total="readerCurrentTotal" @current-change="(page) => setLocalPage(readerCurrentQuery, page)" />
      </div>
      <div class="drawer-section">
        <h4>历史记录</h4>
        <el-table :data="pagedReaderHistoryRows" border empty-text="暂无历史记录">
          <el-table-column prop="bookTitle" label="图书" />
          <el-table-column prop="borrowDate" label="借阅日期" />
          <el-table-column prop="returnDate" label="归还日期" />
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="pager" layout="prev, pager, next, total" :current-page="readerHistoryQuery.page + 1" :page-size="pageSize" :total="readerHistoryTotal" @current-change="(page) => setLocalPage(readerHistoryQuery, page)" />
      </div>
    </div>
    </div>
  </el-drawer>

  <el-drawer v-model="recordDrawer.visible" title="借阅记录详情" size="58%">
    <div v-if="recordDrawer.data" class="detail-list">
      <div v-for="[label, value] in recordDetails(recordDrawer.data)" :key="label" class="detail-item">
        <span>{{ label }}</span>
        <strong>{{ value || '-' }}</strong>
      </div>
    </div>
  </el-drawer>
</template>
