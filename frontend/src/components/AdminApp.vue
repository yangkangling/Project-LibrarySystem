<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Collection,
  DataLine,
  Finished,
  House,
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
import { formatDateTime, pageContent, pageTotal, statusText, statusType } from '@/utils/format'

const emit = defineEmits(['logout'])

const pageSize = 10
const activePage = ref('dashboard')
const menus = [
  { key: 'dashboard', label: '工作台', icon: House },
  { key: 'categories', label: '图书分类', icon: Collection },
  { key: 'books', label: '图书管理', icon: Reading },
  { key: 'readers', label: '读者管理', icon: User },
  { key: 'borrow', label: '借书办理', icon: Tickets },
  { key: 'returns', label: '还书办理', icon: Finished },
  { key: 'records', label: '借阅记录', icon: DataLine },
  { key: 'overdue', label: '逾期查询', icon: Warning },
  { key: 'storage', label: '书架查询', icon: Location }
]
const pageSubtitles = {
  dashboard: '查看馆藏、借阅、读者与逾期概况',
  categories: '维护图书分类，分类被图书引用后不能删除',
  books: '维护书目信息、馆藏数量、书架位置和启停状态',
  readers: '维护读者账号、手机号、密码和账号状态',
  borrow: '搜索读者与图书，一次可选择多本办理借阅',
  returns: '按借阅记录批量归还，可处理部分归还',
  records: '按读者、图书、日期和状态查询借阅历史',
  overdue: '集中查看逾期未还记录并办理归还',
  storage: '查询图书所在书架与可借数量'
}
const currentMenu = computed(() => menus.find((item) => item.key === activePage.value))

const loading = reactive({
  dashboard: false,
  categories: false,
  books: false,
  readers: false,
  borrowReaders: false,
  borrowBooks: false,
  returns: false,
  records: false,
  overdue: false,
  storage: false
})

const dashboard = reactive({
  bookTypes: 0,
  totalBooks: 0,
  availableBooks: 0,
  borrowedBooks: 0,
  readers: 0,
  overdue: 0,
  recentRecords: []
})

const categoryOptions = ref([])
const categories = ref([])
const categoryTotal = ref(0)
const categoryQuery = reactive({ keyword: '', page: 0 })
const categoryDialog = reactive({
  visible: false,
  editing: false,
  form: { id: null, name: '', description: '' }
})

const books = ref([])
const bookTotal = ref(0)
const bookQuery = reactive({ keyword: '', categoryId: '', status: '', page: 0 })
const bookDialog = reactive({
  visible: false,
  editing: false,
  form: emptyBook()
})
const bookDrawer = reactive({ visible: false, data: null })

const readers = ref([])
const readerTotal = ref(0)
const readerQuery = reactive({ keyword: '', status: '', page: 0 })
const readerDialog = reactive({
  visible: false,
  editing: false,
  form: emptyReader()
})
const readerDrawer = reactive({ visible: false, data: null })

const readerOptions = ref([])
const bookOptions = ref([])
const borrowReaderKeyword = ref('')
const borrowBookKeyword = ref('')
const selectedReader = ref(null)
const selectedBooks = ref([])

const returnRecords = ref([])
const returnTotal = ref(0)
const returnQuery = reactive({ keyword: '', page: 0 })
const returnSelection = ref([])

const records = ref([])
const recordTotal = ref(0)
const recordQuery = reactive({
  keyword: '',
  status: '',
  borrowRange: [],
  dueRange: [],
  page: 0
})
const recordDrawer = reactive({ visible: false, data: null })

const overdueRecords = ref([])
const overdueTotal = ref(0)
const overdueQuery = reactive({ keyword: '', page: 0 })

const storageRows = ref([])
const storageTotal = ref(0)
const storageQuery = reactive({ keyword: '', page: 0 })

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

function emptyReader() {
  return {
    id: null,
    realName: '',
    phone: '',
    password: '',
    remark: '',
    status: 'enabled'
  }
}

async function loadPage(page = activePage.value) {
  const loaders = {
    dashboard: loadDashboard,
    categories: loadCategories,
    books: loadBooks,
    readers: loadReaders,
    borrow: loadBorrowPage,
    returns: loadReturnRecords,
    records: loadRecords,
    overdue: loadOverdue,
    storage: loadStorage
  }
  await loaders[page]?.()
}

async function selectPage(page) {
  activePage.value = page
  await loadPage(page)
}

function resetQuery(query, loader) {
  Object.keys(query).forEach((key) => {
    if (key === 'page') query[key] = 0
    else if (Array.isArray(query[key])) query[key] = []
    else query[key] = ''
  })
  loader(0)
}

function setPage(query, loader, page) {
  query.page = page - 1
  loader(query.page)
}

async function loadDashboard() {
  loading.dashboard = true
  try {
    const data = await http.get('/dashboard')
    Object.assign(dashboard, data)
  } finally {
    loading.dashboard = false
  }
}

async function loadCategoryOptions() {
  const data = await http.get('/categories')
  categoryOptions.value = pageContent(data)
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
  await Promise.all([loadCategories(), loadCategoryOptions()])
}

async function deleteCategory(row) {
  await ElMessageBox.confirm(`确认删除分类“${row.name}”？`, '删除分类', { type: 'warning' })
  await http.delete(`/categories/${row.id}`)
  ElMessage.success('分类已删除')
  await Promise.all([loadCategories(), loadCategoryOptions()])
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
  bookDialog.visible = true
}

async function saveBook() {
  const selectedCategory = categoryOptions.value.find((item) => item.id === bookDialog.form.categoryId)
  const payload = {
    isbn: bookDialog.form.isbn,
    title: bookDialog.form.title,
    author: bookDialog.form.author,
    categoryId: bookDialog.form.categoryId,
    category: selectedCategory?.name,
    shelfLocation: bookDialog.form.shelfLocation,
    totalCount: Number(bookDialog.form.totalCount),
    status: bookDialog.form.status
  }
  if (bookDialog.editing) {
    await http.put(`/books/${bookDialog.form.id}`, payload)
  } else {
    await http.post('/books', payload)
  }
  ElMessage.success('图书已保存')
  bookDialog.visible = false
  await Promise.all([loadBooks(), loadStorage(storageQuery.page)])
}

async function toggleBook(row) {
  const nextAction = row.status === 'disabled' ? 'enable' : 'disable'
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
  bookDrawer.data = await http.get(`/books/${row.id}`)
  bookDrawer.visible = true
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
    realName: row.realName,
    phone: row.phone,
    password: '',
    remark: row.remark,
    status: row.status || 'enabled'
  } : emptyReader())
  readerDialog.visible = true
}

async function saveReader() {
  const payload = {
    realName: readerDialog.form.realName,
    phone: readerDialog.form.phone,
    password: readerDialog.form.password,
    remark: readerDialog.form.remark,
    status: readerDialog.form.status
  }
  if (readerDialog.editing) {
    await http.put(`/readers/${readerDialog.form.id}`, payload)
  } else {
    await http.post('/readers', payload)
  }
  ElMessage.success('读者已保存')
  readerDialog.visible = false
  await loadReaders()
}

async function toggleReader(row) {
  const nextAction = row.status === 'disabled' ? 'enable' : 'disable'
  await http.put(`/readers/${row.id}/${nextAction}`)
  ElMessage.success(nextAction === 'enable' ? '读者已启用' : '读者已停用')
  await loadReaders()
}

async function openReaderDetail(row) {
  readerDrawer.data = await http.get(`/readers/${row.id}`)
  readerDrawer.visible = true
}

async function loadBorrowPage() {
  await Promise.all([loadReaderOptions(), loadBookOptions()])
}

async function loadReaderOptions() {
  loading.borrowReaders = true
  try {
    const data = await http.get('/borrow/reader-options', {
      params: pageParams({ keyword: borrowReaderKeyword.value, page: 0, size: 8 })
    })
    readerOptions.value = pageContent(data)
  } finally {
    loading.borrowReaders = false
  }
}

async function loadBookOptions() {
  loading.borrowBooks = true
  try {
    const data = await http.get('/borrow/book-options', {
      params: pageParams({ keyword: borrowBookKeyword.value, page: 0, size: 8 })
    })
    bookOptions.value = pageContent(data)
  } finally {
    loading.borrowBooks = false
  }
}

function addBorrowBook(row) {
  if (selectedBooks.value.some((item) => item.id === row.id)) {
    ElMessage.warning('同一本图书不能重复选择')
    return
  }
  selectedBooks.value.push(row)
}

function removeBorrowBook(id) {
  selectedBooks.value = selectedBooks.value.filter((item) => item.id !== id)
}

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
  selectedBooks.value = []
  await Promise.all([loadBookOptions(), loadDashboard()])
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
      borrowStart: recordQuery.borrowRange?.[0],
      borrowEnd: recordQuery.borrowRange?.[1],
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
  await Promise.all([loadOverdue(), loadReturnRecords(returnQuery.page), loadDashboard()])
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
    ['已借数量', data?.borrowedCount],
    ['创建时间', formatDateTime(book.createdAt)]
  ]
}

function readerDetails(data) {
  const reader = data?.reader || {}
  return [
    ['借阅证号', reader.username],
    ['姓名', reader.realName],
    ['手机号', reader.phone],
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
    ['逾期天数', record?.overdueDays],
    ['创建时间', formatDateTime(record?.createdAt)]
  ]
}

onMounted(async () => {
  await loadCategoryOptions()
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
        @select="selectPage"
      >
        <el-menu-item v-for="item in menus" :key="item.key" :index="item.key">
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
          <div class="panel table-panel">
            <div class="panel-title">
              <h3>近期借阅记录</h3>
              <el-button :icon="Refresh" @click="loadDashboard">刷新</el-button>
            </div>
            <el-table :data="dashboard.recentRecords" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="readerCard" label="借阅证号" width="130" />
              <el-table-column prop="readerName" label="读者" width="120" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="140" />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="140" />
              <el-table-column prop="borrowDate" label="借阅日期" width="120" />
              <el-table-column prop="dueDate" label="应还日期" width="120" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <section v-show="activePage === 'categories'">
          <div class="panel toolbar">
            <el-input v-model="categoryQuery.keyword" :prefix-icon="Search" clearable placeholder="分类名称" @keyup.enter="loadCategories(0)" />
            <el-button type="primary" :icon="Search" @click="loadCategories(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery(categoryQuery, loadCategories)">重置</el-button>
            <el-button type="success" @click="openCategory()">新增分类</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="categories" v-loading="loading.categories" border>
              <el-table-column prop="id" label="编号" width="90" />
              <el-table-column prop="name" label="分类名称" min-width="160" />
              <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
              <el-table-column prop="bookCount" label="图书数量" width="110" />
              <el-table-column label="创建时间" width="180">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button size="small" @click="openCategory(row)">编辑</el-button>
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
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="310" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button size="small" @click="openBookDetail(row)">详情</el-button>
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
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="180">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="250" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button size="small" @click="openReaderDetail(row)">详情</el-button>
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

        <section v-show="activePage === 'borrow'">
          <div class="panel">
            <div class="panel-title"><h3>当前选择</h3></div>
            <div class="selection-bar">
              <el-tag v-if="selectedReader" type="success" closable @close="selectedReader = null">
                {{ selectedReader.cardNumber }} {{ selectedReader.realName }}
              </el-tag>
              <span v-else>请选择读者</span>
              <el-tag v-for="book in selectedBooks" :key="book.id" closable @close="removeBorrowBook(book.id)">
                {{ book.isbn }} {{ book.title }}
              </el-tag>
              <el-button type="primary" :disabled="!selectedReader || !selectedBooks.length" @click="submitBorrow">提交借阅</el-button>
            </div>
          </div>
          <div class="panel toolbar">
            <el-input v-model="borrowReaderKeyword" :prefix-icon="Search" clearable placeholder="搜索读者" @keyup.enter="loadReaderOptions" />
            <el-button :icon="Search" @click="loadReaderOptions">查读者</el-button>
            <el-input v-model="borrowBookKeyword" :prefix-icon="Search" clearable placeholder="搜索图书" @keyup.enter="loadBookOptions" />
            <el-button :icon="Search" @click="loadBookOptions">查图书</el-button>
          </div>
          <div class="panel table-panel">
            <div class="panel-title"><h3>读者候选</h3></div>
            <el-table :data="readerOptions" v-loading="loading.borrowReaders" border>
              <el-table-column prop="cardNumber" label="借阅证号" width="130" />
              <el-table-column prop="realName" label="姓名" width="120" />
              <el-table-column prop="phone" label="手机号" width="140" />
              <el-table-column prop="currentBorrowCount" label="当前未还" width="110" />
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button size="small" type="primary" @click="selectedReader = row">选择</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="panel table-panel">
            <div class="panel-title"><h3>图书候选</h3></div>
            <el-table :data="bookOptions" v-loading="loading.borrowBooks" border>
              <el-table-column prop="isbn" label="ISBN" width="150" />
              <el-table-column prop="title" label="书名" min-width="180" show-overflow-tooltip />
              <el-table-column prop="author" label="作者" width="130" />
              <el-table-column prop="category" label="分类" width="120" />
              <el-table-column prop="availableCount" label="可借" width="80" />
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button size="small" type="primary" :disabled="row.availableCount <= 0" @click="addBorrowBook(row)">加入</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <section v-show="activePage === 'returns'">
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
              <el-table-column prop="copyCode" label="单册编号" width="140" />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
              <el-table-column prop="borrowDate" label="借阅日期" width="120" />
              <el-table-column prop="dueDate" label="应还日期" width="120" />
              <el-table-column prop="overdueDays" label="逾期天数" width="100" />
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="returnQuery.page + 1" :page-size="pageSize" :total="returnTotal" @current-change="(page) => setPage(returnQuery, loadReturnRecords, page)" />
          </div>
        </section>

        <section v-show="activePage === 'records'">
          <div class="panel toolbar">
            <el-input v-model="recordQuery.keyword" :prefix-icon="Search" clearable placeholder="读者 / 图书 / 编号" @keyup.enter="loadRecords(0)" />
            <el-select v-model="recordQuery.status" clearable placeholder="状态">
              <el-option label="借阅中" value="borrowed" />
              <el-option label="已归还" value="returned" />
              <el-option label="已逾期" value="overdue" />
            </el-select>
            <el-date-picker v-model="recordQuery.borrowRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="借阅开始" end-placeholder="借阅结束" />
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
              <el-table-column prop="copyCode" label="单册编号" width="140" />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
              <el-table-column prop="borrowDate" label="借阅日期" width="120" />
              <el-table-column prop="dueDate" label="应还日期" width="120" />
              <el-table-column prop="returnDate" label="归还日期" width="120" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" @click="openRecordDetail(row)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="recordQuery.page + 1" :page-size="pageSize" :total="recordTotal" @current-change="(page) => setPage(recordQuery, loadRecords, page)" />
          </div>
        </section>

        <section v-show="activePage === 'overdue'">
          <div class="panel toolbar">
            <el-input v-model="overdueQuery.keyword" :prefix-icon="Search" clearable placeholder="读者 / 图书 / 单册编号" @keyup.enter="loadOverdue(0)" />
            <el-button type="primary" :icon="Search" @click="loadOverdue(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery(overdueQuery, loadOverdue)">重置</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="overdueRecords" v-loading="loading.overdue" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="readerCard" label="借阅证号" width="130" />
              <el-table-column prop="readerName" label="读者" width="120" />
              <el-table-column prop="readerPhone" label="手机号" width="140" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="140" />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
              <el-table-column prop="dueDate" label="应还日期" width="120" />
              <el-table-column prop="overdueDays" label="逾期天数" width="100" />
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="success" @click="returnOne(row)">还书</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="overdueQuery.page + 1" :page-size="pageSize" :total="overdueTotal" @current-change="(page) => setPage(overdueQuery, loadOverdue, page)" />
          </div>
        </section>

        <section v-show="activePage === 'storage'">
          <div class="panel toolbar">
            <el-input v-model="storageQuery.keyword" :prefix-icon="Search" clearable placeholder="书名 / ISBN / 书架" @keyup.enter="loadStorage(0)" />
            <el-button type="primary" :icon="Search" @click="loadStorage(0)">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery(storageQuery, loadStorage)">重置</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="storageRows" v-loading="loading.storage" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="isbn" label="ISBN" width="150" />
              <el-table-column prop="bookTitle" label="书名" min-width="180" show-overflow-tooltip />
              <el-table-column prop="author" label="作者" width="130" />
              <el-table-column prop="category" label="分类" width="120" />
              <el-table-column prop="shelfLocation" label="书架位置" width="140" />
              <el-table-column prop="totalCount" label="馆藏" width="90" />
              <el-table-column prop="availableCount" label="可借" width="90" />
              <el-table-column prop="borrowedCount" label="已借" width="90" />
              <el-table-column label="更新时间" width="180">
                <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="storageQuery.page + 1" :page-size="pageSize" :total="storageTotal" @current-change="(page) => setPage(storageQuery, loadStorage, page)" />
          </div>
        </section>
      </el-main>
    </el-container>
  </el-container>

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
          <el-form-item label="书架位置"><el-input v-model="bookDialog.form.shelfLocation" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="馆藏数量"><el-input-number v-model="bookDialog.form.totalCount" :min="1" style="width: 100%" /></el-form-item>
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
            <el-input v-model="readerDialog.form.password" type="password" show-password />
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

  <el-drawer v-model="bookDrawer.visible" title="图书详情" size="62%">
    <div v-if="bookDrawer.data">
      <div class="detail-list">
        <div v-for="[label, value] in bookDetails(bookDrawer.data)" :key="label" class="detail-item">
          <span>{{ label }}</span>
          <strong>{{ value || '-' }}</strong>
        </div>
      </div>
      <div class="drawer-section">
        <h4>书架分布</h4>
        <el-table :data="bookDrawer.data.storageLocations" border>
          <el-table-column prop="shelfLocation" label="书架位置" />
          <el-table-column prop="totalCount" label="馆藏" width="90" />
          <el-table-column prop="availableCount" label="可借" width="90" />
          <el-table-column prop="borrowedCount" label="已借" width="90" />
        </el-table>
      </div>
      <div class="drawer-section">
        <h4>单册状态</h4>
        <el-table :data="bookDrawer.data.copies" border>
          <el-table-column prop="copyCode" label="单册编号" />
          <el-table-column prop="shelfLocation" label="书架位置" />
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="currentReaderName" label="当前读者" />
        </el-table>
      </div>
    </div>
  </el-drawer>

  <el-drawer v-model="readerDrawer.visible" title="读者详情" size="62%">
    <div v-if="readerDrawer.data">
      <div class="detail-list">
        <div v-for="[label, value] in readerDetails(readerDrawer.data)" :key="label" class="detail-item">
          <span>{{ label }}</span>
          <strong>{{ value || '-' }}</strong>
        </div>
      </div>
      <div class="drawer-section">
        <h4>当前未还</h4>
        <el-table :data="readerDrawer.data.currentBorrowRecords" border>
          <el-table-column prop="bookTitle" label="图书" />
          <el-table-column prop="copyCode" label="单册编号" />
          <el-table-column prop="borrowDate" label="借阅日期" />
          <el-table-column prop="dueDate" label="应还日期" />
          <el-table-column prop="overdueDays" label="逾期天数" />
        </el-table>
      </div>
      <div class="drawer-section">
        <h4>历史记录</h4>
        <el-table :data="readerDrawer.data.historyRecords" border>
          <el-table-column prop="bookTitle" label="图书" />
          <el-table-column prop="borrowDate" label="借阅日期" />
          <el-table-column prop="returnDate" label="归还日期" />
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
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
