<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '@/api/http'
import { installTableAutoScroll } from '@/utils/tableAutoScroll'
import LoginView from '@/components/LoginView.vue'
import AdminApp from '@/components/AdminApp.vue'
import ReaderApp from '@/components/ReaderApp.vue'

// 顶层只负责判断登录身份并切换端口。
const booting = ref(true)
const mode = ref('login')
let cleanupTableAutoScroll = null

// 刷新页面时恢复当前会话。
async function refreshAuth() {
  try {
    const data = await http.get('/auth/me')
    if (data.adminLoggedIn) {
      mode.value = 'admin'
    } else if (data.readerLoggedIn) {
      mode.value = 'reader'
    } else {
      mode.value = 'login'
    }
  } catch {
    mode.value = 'login'
  } finally {
    booting.value = false
  }
}

// 管理端和读者端共用退出逻辑。
async function logout() {
  try {
    await http.post('/auth/logout')
    ElMessage.success('已退出登录')
  } finally {
    mode.value = 'login'
  }
}

function handleAuthenticated(role) {
  mode.value = role === 'reader' ? 'reader' : 'admin'
}

onMounted(() => {
  cleanupTableAutoScroll = installTableAutoScroll()
  refreshAuth()
})

onBeforeUnmount(() => {
  cleanupTableAutoScroll?.()
})
</script>

<template>
  <div v-if="booting" class="login-view">
    <el-card shadow="never">
      <el-skeleton :rows="4" animated />
    </el-card>
  </div>
  <LoginView
    v-else-if="mode === 'login'"
    @authenticated="handleAuthenticated"
  />
  <AdminApp
    v-else-if="mode === 'admin'"
    @logout="logout"
  />
  <ReaderApp
    v-else
    @logout="logout"
  />
</template>
