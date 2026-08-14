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
  Tickets
} from '@element-plus/icons-vue'
import { http, pageParams } from '@/api/http'
import { pageContent, pageTotal, statusText, statusType } from '@/utils/format'

const emit = defineEmits(['logout'])

const pageSize = 10
const activePage = ref('home')
const menus = [
  { key: 'home', label: '我的借阅', icon: House },
  { key: 'books', label: '图书查询', icon: Reading },
  { key: 'returns', label: '自助还书', icon: Finished },
  { key: 'records', label: '我的记录', icon: DataLine }
]
const subtitles = {
  home: '查看本人未还图书和借阅上限',
  books: '搜索可借图书并加入借阅清单',
  returns: '勾选本人未还记录后自助归还',
  records: '查看本人全部借阅历史'
}
const currentMenu = computed(() => menus.find((item) => item.key === activePage.value))

const loading = reactive({
  home: false,
  books: false,
  returns: false,
  records: false
})

const me = reactive({
  username: '',
  realName: '',
  phone: '',
  currentBorrowCount: 0,
  maxBorrowCount: 3
})
const homeRecords = ref([])
const categories = ref([])
const books = ref([])
const bookTotal = ref(0)
const bookQuery = reactive({ keyword: '', categoryId: '', page: 0 })
const selectedBooks = ref([])
const returnRecords = ref([])
const returnSelection = ref([])
const records = ref([])

async function selectPage(page) {
  activePage.value = page
  await loadPage(page)
}

async function loadPage(page = activePage.value) {
  const loaders = {
    home: loadHome,
    books: loadBooks,
    returns: loadReturns,
    records: loadRecords
  }
  await loaders[page]?.()
}

function setPage(query, loader, page) {
  query.page = page - 1
  loader(query.page)
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

async function loadHome() {
  loading.home = true
  try {
    const [profile, borrowed] = await Promise.all([
      http.get('/self/me'),
      http.get('/self/records', { params: { status: 'borrowed' } })
    ])
    Object.assign(me, profile)
    homeRecords.value = borrowed
  } finally {
    loading.home = false
  }
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
  if (selectedBooks.value.some((item) => item.id === row.id)) {
    ElMessage.warning('同一本图书不能重复选择')
    return
  }
  selectedBooks.value.push(row)
}

function removeBook(id) {
  selectedBooks.value = selectedBooks.value.filter((item) => item.id !== id)
}

async function submitBorrow() {
  if (!selectedBooks.value.length) {
    ElMessage.warning('请至少选择一本图书')
    return
  }
  const data = await http.post('/self/borrow', {
    bookIds: selectedBooks.value.map((item) => item.id)
  })
  ElMessage.success(`借书成功，本次借出 ${data.length} 本`)
  selectedBooks.value = []
  await Promise.all([loadHome(), loadBooks(bookQuery.page)])
}

async function loadReturns() {
  loading.returns = true
  try {
    returnRecords.value = await http.get('/self/records', { params: { status: 'borrowed' } })
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
    ElMessage.warning('请勾选要归还的图书')
    return
  }
  await http.post('/self/return', {
    recordIds: returnSelection.value.map((item) => item.id)
  })
  ElMessage.success(`还书成功，本次归还 ${returnSelection.value.length} 本`)
  await Promise.all([loadHome(), loadReturns()])
}

async function loadRecords() {
  loading.records = true
  try {
    records.value = await http.get('/self/records')
  } finally {
    loading.records = false
  }
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
          <small>{{ subtitles[activePage] }}</small>
        </div>
        <el-button :icon="SwitchButton" @click="emit('logout')">退出</el-button>
      </el-header>

      <el-main class="content">
        <section v-show="activePage === 'home'" v-loading="loading.home">
          <div class="stat-grid">
            <div class="stat-card"><span>借阅证号</span><strong>{{ me.username || '-' }}</strong></div>
            <div class="stat-card"><span>姓名</span><strong>{{ me.realName || '-' }}</strong></div>
            <div class="stat-card"><span>当前未还</span><strong>{{ me.currentBorrowCount }}</strong></div>
            <div class="stat-card"><span>可借上限</span><strong>{{ me.maxBorrowCount }}</strong></div>
          </div>
          <div class="panel table-panel">
            <div class="panel-title">
              <h3>当前未还</h3>
              <el-button :icon="Refresh" @click="loadHome">刷新</el-button>
            </div>
            <el-table :data="homeRecords" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="batchNo" label="批次号" width="150" />
              <el-table-column prop="isbn" label="ISBN" width="150" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="140" />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
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

        <section v-show="activePage === 'books'">
          <div class="panel">
            <div class="panel-title">
              <h3>借阅清单</h3>
              <el-button type="primary" :icon="Tickets" :disabled="!selectedBooks.length" @click="submitBorrow">提交借阅</el-button>
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
                  <el-button size="small" type="primary" :disabled="!row.borrowable" @click="addBook(row)">加入</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" layout="prev, pager, next, total" :current-page="bookQuery.page + 1" :page-size="pageSize" :total="bookTotal" @current-change="(page) => setPage(bookQuery, loadBooks, page)" />
          </div>
        </section>

        <section v-show="activePage === 'returns'">
          <div class="panel toolbar">
            <el-button type="success" :disabled="!returnSelection.length" @click="submitReturns">归还选中</el-button>
            <el-button :icon="Refresh" @click="loadReturns">刷新</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="returnRecords" v-loading="loading.returns" border @selection-change="setReturnSelection">
              <el-table-column type="selection" width="48" />
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="batchNo" label="批次号" width="150" />
              <el-table-column prop="isbn" label="ISBN" width="150" />
              <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
              <el-table-column prop="copyCode" label="单册编号" width="140" />
              <el-table-column prop="copyShelfLocation" label="书架位置" width="130" />
              <el-table-column prop="borrowDate" label="借阅日期" width="120" />
              <el-table-column prop="dueDate" label="应还日期" width="120" />
              <el-table-column prop="overdueDays" label="逾期天数" width="100" />
            </el-table>
          </div>
        </section>

        <section v-show="activePage === 'records'">
          <div class="panel toolbar">
            <el-button :icon="Refresh" @click="loadRecords">刷新</el-button>
          </div>
          <div class="panel table-panel">
            <el-table :data="records" v-loading="loading.records" border>
              <el-table-column prop="id" label="编号" width="80" />
              <el-table-column prop="batchNo" label="批次号" width="150" />
              <el-table-column prop="isbn" label="ISBN" width="150" />
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
            </el-table>
          </div>
        </section>
      </el-main>
    </el-container>
  </el-container>
</template>
