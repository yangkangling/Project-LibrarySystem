<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  DataLine,
  Finished,
  House,
  Reading,
  Refresh,
  Search,
  SwitchButton,
  Tickets,
  Warning
} from '@element-plus/icons-vue'
import { http, pageParams } from '@/api/http'
import { extensionStatusText, extensionStatusType, pageContent, pageTotal, statusText, statusType } from '@/utils/format'

const emit = defineEmits(['logout'])

// 读者端菜单和分页基础状态。
const pageSize = 10
const activePage = ref('home')
const menus = [
  { key: 'home', label: '我的借阅', icon: House },
  { key: 'warnings', label: '还书预警', icon: Warning },
  { key: 'books', label: '图书查询', icon: Reading },
  { key: 'renewals', label: '续借办理', icon: Tickets },
  { key: 'returns', label: '自助还书', icon: Finished },
  { key: 'records', label: '我的记录', icon: DataLine }
]
const subtitles = {
  home: '查看本人未还图书和借阅上限',
  warnings: '查看 7 天、30 天、90 天或全部未还的到期风险',
  books: '搜索可借图书并加入借阅清单',
  renewals: '查看当前未还图书并提交续借申请',
  returns: '勾选本人未还记录后自助归还',
  records: '查看本人全部借阅历史'
}
const currentMenu = computed(() => menus.find((item) => item.key === activePage.value))

// 各页面独立加载状态。
const loading = reactive({
  home: false,
  warnings: false,
  books: false,
  renewals: false,
  returns: false,
  records: false
})

// 当前读者资料和页面数据。
const me = reactive({
  username: '',
  realName: '',
  phone: '',
  status: 'enabled',
  currentBorrowCount: 0,
  maxBorrowCount: 3
})
const readerDisabled = computed(() => me.status === 'disabled')
const readerAccountStatusText = computed(() => readerDisabled.value ? '已冻结' : statusText(me.status))
const homeRecords = ref([])
const homeRecordTotal = ref(0)
const homeRecordQuery = reactive({ page: 0 })
const homeWarnings = ref([])
const homeWarningTotal = ref(0)
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
const categories = ref([])
const books = ref([])
const bookTotal = ref(0)
const bookQuery = reactive({ keyword: '', categoryId: '', page: 0 })
const selectedBooks = ref([])
// 读者借阅可选默认 30 天或自定义天数，自定义最长 90 天。
const DEFAULT_BORROW_DAYS = 30
const MAX_BORROW_DAYS = 90
const borrowMode = ref('default')
const borrowDays = ref(DEFAULT_BORROW_DAYS)
const selectedBorrowDays = computed(() => borrowMode.value === 'default' ? DEFAULT_BORROW_DAYS : borrowDays.value)
const expectedBorrowDueDate = computed(() => formatDate(addDays(new Date(), selectedBorrowDays.value)))
const renewalRecords = ref([])
const renewalTotal = ref(0)
const renewalQuery = reactive({ page: 0 })
const returnRecords = ref([])
const returnTotal = ref(0)
const returnQuery = reactive({ page: 0 })
const returnSelection = ref([])
const records = ref([])
const recordTotal = ref(0)
const recordQuery = reactive({ page: 0 })
const extensionDialog = reactive({
  visible: false,
  record: null,
  days: 1,
  maxDays: 1,
  maxDueDate: ''
})

async function selectPage(page) {
  activePage.value = page
  if (page !== 'home') {
    await loadProfile()
  }
  await loadPage(page)
}

// 根据左侧菜单加载对应页面。
async function loadPage(page = activePage.value) {
  const loaders = {
    home: loadHome,
    warnings: loadWarnings,
    books: loadBooks,
    renewals: loadRenewals,
    returns: loadReturns,
    records: loadRecords
  }
  await loaders[page]?.()
}

function setPage(query, loader, page) {
  query.page = page - 1
  loader(query.page)
}

function warningTagType(level) {
  if (level === 'danger') return 'danger'
  if (level === 'warning') return 'warning'
  return 'info'
}

function resetBooks() {
  bookQuery.keyword = ''
  bookQuery.categoryId = ''
  bookQuery.page = 0
  loadBooks(0)
}

async function loadCategories() {
  categories.value = await http.get('/self/categories')
}

async function loadProfile() {
  Object.assign(me, await http.get('/self/me'))
}

// 首页展示个人资料和未还记录。
async function loadHome(page = homeRecordQuery.page) {
  homeRecordQuery.page = page
  loading.home = true
  try {
    const [profile, borrowed] = await Promise.all([
      http.get('/self/me'),
      http.get('/self/records', {
        params: pageParams({ status: 'borrowed', ...homeRecordQuery, size: pageSize })
      })
    ])
    Object.assign(me, profile)
    homeRecords.value = pageContent(borrowed)
    homeRecordTotal.value = pageTotal(borrowed)
    await loadHomeWarnings()
  } finally {
    loading.home = false
  }
}

async function loadHomeWarnings() {
  const data = await http.get('/self/warnings', {
    params: pageParams({ page: 0, size: 5, days: warningDefaultDays })
  })
  homeWarnings.value = pageContent(data)
  homeWarningTotal.value = pageTotal(data)
}

async function loadWarnings(page = warningQuery.page) {
  warningQuery.page = page
  loading.warnings = true
  try {
    const data = await http.get('/self/warnings', {
      params: pageParams({ ...warningQuery, size: pageSize })
    })
    warningRecords.value = pageContent(data)
    warningTotal.value = pageTotal(data)
  } finally {
    loading.warnings = false
  }
}

async function refreshWarnings() {
  warningQuery.page = 0
  await Promise.all([loadWarnings(0), loadHomeWarnings()])
  ElMessage.success('预警已刷新')
}

function resetWarnings() {
  warningQuery.keyword = ''
  warningQuery.days = warningDefaultDays
  warningQuery.page = 0
  loadWarnings(0)
}

async function loadBooks(page = bookQuery.page) {
  bookQuery.page = page
  loading.books = true
  try {
    const data = await http.get('/self/books', {
      params: pageParams({ ...bookQuery, size: pageSize })
    })
    books.value = pageContent(data)
    bookTotal.value = pageTotal(data)
  } finally {
    loading.books = false
  }
}

function addBook(row) {
  if (readerDisabled.value) {
    ElMessage.warning('账号已冻结，暂不能借书')
    return
  }
  if (selectedBooks.value.some((item) => item.id === row.id)) {
    ElMessage.warning('同一本图书不能重复选择')
    return
  }
  selectedBooks.value.push(row)
}

function removeBook(id) {
  selectedBooks.value = selectedBooks.value.filter((item) => item.id !== id)
}

// 提交自助借书清单。
async function submitBorrow() {
  if (readerDisabled.value) {
    ElMessage.warning('账号已冻结，暂不能借书')
    return
  }
  if (!selectedBooks.value.length) {
    ElMessage.warning('请至少选择一本图书')
    return
  }
  const data = await http.post('/self/borrow', {
    bookIds: selectedBooks.value.map((item) => item.id),
    borrowMode: borrowMode.value,
    borrowDays: borrowMode.value === 'custom' ? borrowDays.value : null
  })
  ElMessage.success(`借书成功，本次借出 ${data.length} 本，应还日期 ${expectedBorrowDueDate.value}`)
  selectedBooks.value = []
  await Promise.all([loadHome(), loadWarnings(warningQuery.page), loadBooks(bookQuery.page), loadRenewals(renewalQuery.page)])
}

async function loadRenewals(page = renewalQuery.page) {
  renewalQuery.page = page
  loading.renewals = true
  try {
    const data = await http.get('/self/records', {
      params: pageParams({ status: 'borrowed', ...renewalQuery, size: pageSize })
    })
    renewalRecords.value = pageContent(data)
    renewalTotal.value = pageTotal(data)
  } finally {
    loading.renewals = false
  }
}

async function loadReturns(page = returnQuery.page) {
  returnQuery.page = page
  loading.returns = true
  try {
    const data = await http.get('/self/records', {
      params: pageParams({ status: 'borrowed', ...returnQuery, size: pageSize })
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

// 提交勾选的自助还书记录。
async function submitReturns() {
  if (!returnSelection.value.length) {
    ElMessage.warning('请勾选要归还的图书')
    return
  }
  await http.post('/self/return', {
    recordIds: returnSelection.value.map((item) => item.id)
  })
  ElMessage.success(`还书成功，本次归还 ${returnSelection.value.length} 本`)
  await Promise.all([loadHome(), loadWarnings(warningQuery.page), loadRenewals(renewalQuery.page), loadReturns(returnQuery.page), loadRecords(recordQuery.page)])
}

async function loadRecords(page = recordQuery.page) {
  recordQuery.page = page
  loading.records = true
  try {
    const data = await http.get('/self/records', {
      params: pageParams({ ...recordQuery, size: pageSize })
    })
    records.value = pageContent(data)
    recordTotal.value = pageTotal(data)
  } finally {
    loading.records = false
  }
}

function parseDate(value) {
  if (!value) return null
  const date = new Date(`${value}T00:00:00`)
  return Number.isNaN(date.getTime()) ? null : date
}

function addDays(date, days) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function maxExtensionDays(row) {
  const dueDate = parseDate(row?.dueDate)
  if (!dueDate) return 0
  const maxDueDate = row.maxDueDate ? parseDate(row.maxDueDate) : addDays(dueDate, MAX_BORROW_DAYS)
  return Math.max(0, Math.floor((maxDueDate - dueDate) / 86400000))
}

function canRequestExtension(row) {
  return !readerDisabled.value
    && row?.rawStatus === 'borrowed'
    && row?.status !== 'overdue'
    && row?.extensionStatus !== 'pending'
    && maxExtensionDays(row) > 0
}

// 续借入口只对未逾期、未归还且没有待审批申请的记录开放。
function renewalActionText(row) {
  if (row?.extensionStatus === 'pending') return '待审批'
  if (!canRequestExtension(row)) return '不可续借'
  return '申请续借'
}

// 打开续借弹窗时计算本次最多可申请天数，避免超过 90 天上限。
function openExtension(row) {
  if (row.extensionStatus === 'pending') {
    ElMessage.warning('该记录已有待审批续借申请')
    return
  }
  const maxDays = maxExtensionDays(row)
  if (maxDays <= 0) {
    ElMessage.warning('该记录暂无可续借天数')
    return
  }
  extensionDialog.record = row
  extensionDialog.maxDays = maxDays
  extensionDialog.days = Math.min(7, maxDays)
  const dueDate = parseDate(row.dueDate)
  extensionDialog.maxDueDate = row.maxDueDate || formatDate(addDays(dueDate, MAX_BORROW_DAYS))
  extensionDialog.visible = Boolean(dueDate)
}

async function submitExtensionRequest() {
  if (!extensionDialog.record) return
  if (readerDisabled.value) {
    ElMessage.warning('账号已冻结，暂不能申请续借')
    return
  }
  await http.post(`/self/records/${extensionDialog.record.id}/extension-request`, {
    days: extensionDialog.days
  })
  ElMessage.success('续借申请已提交，等待管理员同意')
  extensionDialog.visible = false
  await Promise.all([loadHome(), loadWarnings(warningQuery.page), loadRenewals(renewalQuery.page), loadReturns(returnQuery.page), loadRecords(recordQuery.page)])
}

onMounted(async () => {
  await loadCategories()
  await loadHome()
})
</script>

<template>
  <el-container class="shell">
    <el-aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">读</span>
        <span>读者自助</span>
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
          <small>{{ subtitles[activePage] }}</small>
        </div>
        <el-button :icon="SwitchButton" @click="emit('logout')">退出</el-button>
      </el-header>

      <el-main class="content">
        <el-alert
          v-if="readerDisabled"
          class="account-alert"
          title="账号已冻结"
          description="当前账号暂不能借书或申请续借，请先处理逾期违规记录并联系管理员恢复。"
          type="error"
          :closable="false"
          show-icon
        />
        <section v-show="activePage === 'home'" v-loading="loading.home">
          <div class="stat-grid">
            <div class="stat-card"><span>借阅证号</span><strong>{{ me.username || '-' }}</strong></div>
            <div class="stat-card"><span>姓名</span><strong>{{ me.realName || '-' }}</strong></div>
            <div class="stat-card"><span>账号状态</span><strong>{{ readerAccountStatusText }}</strong></div>
            <div class="stat-card"><span>当前未还</span><strong>{{ me.currentBorrowCount }}</strong></div>
            <div class="stat-card"><span>可借上限</span><strong>{{ me.maxBorrowCount }}</strong></div>
          </div>
          <div class="panel warning-panel">
            <div class="panel-title">
              <h3>还书预警</h3>
              <el-button type="warning" plain :icon="Warning" @click="selectPage('warnings')">查看全部 {{ homeWarningTotal }}</el-button>
            </div>
            <div v-if="homeWarnings.length" class="warning-list compact">
              <article v-for="row in homeWarnings" :key="row.id" class="warning-card">
                <div>
                  <strong>{{ row.bookTitle }}</strong>
                  <span>{{ row.copyCode }} / {{ row.copyShelfLocation || '未登记书架' }}</span>
                </div>
                <el-tag :type="warningTagType(row.warningLevel)">{{ row.warningText }}</el-tag>
              </article>
            </div>
            <el-empty v-else description="暂无还书预警" />
          </div>
          <div class="panel table-panel">
            <div class="panel-title">
              <h3>当前未还</h3>
              <el-button :icon="Refresh" @click="loadHome(homeRecordQuery.page)">刷新</el-button>
            </div>
            <el-table :data="homeRecords" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="batchNo" label="批次号" width="150" />
              <el-table-column prop="isbn" label="ISBN" width="150" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="190" show-overflow-tooltip />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
              <el-table-column prop="borrowDate" label="借阅日期" width="120" />
              <el-table-column prop="dueDate" label="应还日期" width="120" />
              <el-table-column label="续借状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="extensionStatusType(row.extensionStatus)">{{ extensionStatusText(row.extensionStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" :disabled="!canRequestExtension(row)" @click="openExtension(row)">{{ renewalActionText(row) }}</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="homeRecordQuery.page + 1" :page-size="pageSize" :total="homeRecordTotal" @current-change="(page) => setPage(homeRecordQuery, loadHome, page)" />
          </div>
        </section>

        <section v-show="activePage === 'warnings'">
          <div class="panel toolbar">
            <el-input v-model="warningQuery.keyword" :prefix-icon="Search" clearable placeholder="图书 / ISBN / 单册编号 / 批次号" @keyup.enter="loadWarnings(0)" />
            <el-select v-model="warningQuery.days" placeholder="预警范围" style="width: 150px" @change="loadWarnings(0)">
              <el-option v-for="item in warningRangeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="loadWarnings(0)">查询</el-button>
            <el-button :icon="Refresh" @click="refreshWarnings">刷新预警</el-button>
            <el-button @click="resetWarnings">重置</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="warningRecords" v-loading="loading.warnings" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="batchNo" label="批次号" width="150" />
              <el-table-column prop="isbn" label="ISBN" width="150" />
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
              <el-table-column label="续借状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="extensionStatusType(row.extensionStatus)">{{ extensionStatusText(row.extensionStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="130" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" :disabled="!canRequestExtension(row)" @click="openExtension(row)">{{ renewalActionText(row) }}</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="warningQuery.page + 1" :page-size="pageSize" :total="warningTotal" @current-change="(page) => setPage(warningQuery, loadWarnings, page)" />
          </div>
        </section>

        <section v-show="activePage === 'books'">
          <div class="panel">
            <div class="panel-title">
              <h3>借阅清单</h3>
              <el-button type="primary" :icon="Tickets" :disabled="readerDisabled || !selectedBooks.length" @click="submitBorrow">提交借阅</el-button>
            </div>
            <div class="borrow-options borrow-mode-options">
              <el-radio-group v-model="borrowMode" class="borrow-mode-group">
                <el-radio-button label="default">默认 30 天还书</el-radio-button>
                <el-radio-button label="custom">自由选择期限</el-radio-button>
              </el-radio-group>
              <div class="borrow-mode-detail">
                <template v-if="borrowMode === 'default'">
                  <span>默认期限</span>
                  <strong>30 天</strong>
                  <small>预计 {{ expectedBorrowDueDate }} 到期，借出后可申请续借</small>
                </template>
                <template v-else>
                  <span>借阅天数</span>
                  <el-input-number v-model="borrowDays" :min="1" :max="MAX_BORROW_DAYS" />
                  <small>最长 3 个月，预计 {{ expectedBorrowDueDate }} 到期，借出后也可申请续借</small>
                </template>
              </div>
            </div>
            <div class="selection-bar">
              <el-tag v-for="book in selectedBooks" :key="book.id" closable @close="removeBook(book.id)">
                {{ book.isbn }} {{ book.title }}
              </el-tag>
              <span v-if="!selectedBooks.length">还未选择图书</span>
            </div>
          </div>
          <div class="panel toolbar">
            <el-input v-model="bookQuery.keyword" :prefix-icon="Search" clearable placeholder="ISBN / 书名 / 作者" @keyup.enter="loadBooks(0)" />
            <el-select v-model="bookQuery.categoryId" clearable placeholder="分类">
              <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="loadBooks(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetBooks">重置</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="books" v-loading="loading.books" border>
              <el-table-column prop="isbn" label="ISBN" width="150" />
              <el-table-column prop="title" label="书名" min-width="180" show-overflow-tooltip />
              <el-table-column prop="author" label="作者" width="130" />
              <el-table-column prop="category" label="分类" width="120" />
              <el-table-column prop="shelfLocation" label="书架位置" width="130" />
              <el-table-column prop="availableCount" label="可借" width="90" />
              <el-table-column label="操作" width="110">
                <template #default="{ row }">
                  <el-button size="small" type="primary" :disabled="readerDisabled || !row.borrowable" @click="addBook(row)">加入</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="bookQuery.page + 1" :page-size="pageSize" :total="bookTotal" @current-change="(page) => setPage(bookQuery, loadBooks, page)" />
          </div>
        </section>

        <section v-show="activePage === 'renewals'">
          <div class="panel toolbar">
            <el-button :icon="Refresh" @click="loadRenewals(renewalQuery.page)">刷新</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="renewalRecords" v-loading="loading.renewals" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="batchNo" label="批次号" width="150" />
              <el-table-column prop="isbn" label="ISBN" width="150" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="190" show-overflow-tooltip />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
              <el-table-column prop="borrowDate" label="借阅日期" width="120" />
              <el-table-column prop="dueDate" label="当前应还" width="120" />
              <el-table-column prop="maxDueDate" label="最长可续至" width="120" />
              <el-table-column label="可续天数" width="100">
                <template #default="{ row }">{{ maxExtensionDays(row) }}</template>
              </el-table-column>
              <el-table-column label="续借状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="extensionStatusType(row.extensionStatus)">{{ extensionStatusText(row.extensionStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="借阅状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" :disabled="!canRequestExtension(row)" @click="openExtension(row)">{{ renewalActionText(row) }}</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="renewalQuery.page + 1" :page-size="pageSize" :total="renewalTotal" @current-change="(page) => setPage(renewalQuery, loadRenewals, page)" />
          </div>
        </section>

        <section v-show="activePage === 'returns'">
          <div class="panel toolbar">
            <el-button type="success" :disabled="!returnSelection.length" @click="submitReturns">归还选中</el-button>
            <el-button :icon="Refresh" @click="loadReturns(returnQuery.page)">刷新</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="returnRecords" v-loading="loading.returns" border @selection-change="setReturnSelection">
              <el-table-column type="selection" width="48" />
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="batchNo" label="批次号" width="150" />
              <el-table-column prop="isbn" label="ISBN" width="150" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="190" show-overflow-tooltip />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
              <el-table-column prop="borrowDate" label="借阅日期" width="120" />
              <el-table-column prop="dueDate" label="应还日期" width="120" />
              <el-table-column label="续借状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="extensionStatusType(row.extensionStatus)">{{ extensionStatusText(row.extensionStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="overdueDays" label="逾期天数" width="100" />
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="returnQuery.page + 1" :page-size="pageSize" :total="returnTotal" @current-change="(page) => setPage(returnQuery, loadReturns, page)" />
          </div>
        </section>

        <section v-show="activePage === 'records'">
          <div class="panel toolbar">
            <el-button :icon="Refresh" @click="loadRecords(recordQuery.page)">刷新</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="records" v-loading="loading.records" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="batchNo" label="批次号" width="150" />
              <el-table-column prop="isbn" label="ISBN" width="150" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="190" show-overflow-tooltip />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
              <el-table-column prop="borrowDate" label="借阅日期" width="120" />
              <el-table-column prop="dueDate" label="应还日期" width="120" />
              <el-table-column prop="returnDate" label="归还日期" width="120" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="续借状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="extensionStatusType(row.extensionStatus)">{{ extensionStatusText(row.extensionStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" :disabled="!canRequestExtension(row)" @click="openExtension(row)">{{ renewalActionText(row) }}</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="recordQuery.page + 1" :page-size="pageSize" :total="recordTotal" @current-change="(page) => setPage(recordQuery, loadRecords, page)" />
          </div>
        </section>
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="extensionDialog.visible" title="申请续借" width="460px">
    <div v-if="extensionDialog.record" class="extension-summary">
      <p><strong>{{ extensionDialog.record.bookTitle }}</strong></p>
      <p>当前应还日期：{{ extensionDialog.record.dueDate }}</p>
      <p>最长可续至：{{ extensionDialog.maxDueDate }}</p>
      <el-form label-position="top">
        <el-form-item label="申请续借天数">
          <el-input-number v-model="extensionDialog.days" :min="1" :max="extensionDialog.maxDays" style="width: 100%" />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="extensionDialog.visible = false">取消</el-button>
      <el-button type="primary" @click="submitExtensionRequest">提交申请</el-button>
    </template>
  </el-dialog>
</template>
