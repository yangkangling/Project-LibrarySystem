<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock, User, Tickets } from '@element-plus/icons-vue'
import { formBody, http } from '@/api/http'

const emit = defineEmits(['authenticated'])

const activeTab = ref('admin')
const loading = ref(false)
const adminForm = reactive({ username: '', password: '' })
const readerForm = reactive({ username: '', password: '' })
const registerForm = reactive({ phone: '', password: '', confirmPassword: '' })
const registeredCard = ref('')

async function submitAdmin() {
  loading.value = true
  try {
    await http.post('/auth/login', formBody(adminForm))
    ElMessage.success('管理员登录成功')
    emit('authenticated', 'admin')
  } finally {
    loading.value = false
  }
}

async function submitReader() {
  loading.value = true
  try {
    await http.post('/auth/reader-login', formBody(readerForm))
    ElMessage.success('读者登录成功')
    emit('authenticated', 'reader')
  } finally {
    loading.value = false
  }
}

async function submitRegister() {
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  loading.value = true
  try {
    const data = await http.post('/readers/register', registerForm)
    registeredCard.value = data.readerCard
    readerForm.username = data.readerCard
    readerForm.password = registerForm.password
    registerForm.phone = ''
    registerForm.password = ''
    registerForm.confirmPassword = ''
    activeTab.value = 'reader'
    ElMessage.success(`注册成功，借阅证号：${data.readerCard}`)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-view">
    <section class="login-card">
      <div class="login-hero">
        <h1>图书馆借阅系统</h1>
        <p>面向馆员与读者的借阅管理平台，覆盖图书维护、读者管理、批量借还、逾期查询和读者自助服务。</p>
        <div class="login-metrics">
          <div class="login-metric">
            <strong>Spring Boot</strong>
            <span>后端接口</span>
          </div>
          <div class="login-metric">
            <strong>Vue3</strong>
            <span>前端应用</span>
          </div>
          <div class="login-metric">
            <strong>Element Plus</strong>
            <span>组件库</span>
          </div>
        </div>
      </div>

      <div class="login-panel">
        <el-tabs v-model="activeTab" stretch>
          <el-tab-pane label="管理员登录" name="admin">
            <el-form label-position="top" @submit.prevent="submitAdmin">
              <el-form-item label="账号">
                <el-input v-model="adminForm.username" :prefix-icon="User" autocomplete="username" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="adminForm.password" :prefix-icon="Lock" type="password" show-password autocomplete="current-password" />
              </el-form-item>
              <el-button type="primary" :loading="loading" style="width: 100%" @click="submitAdmin">
                进入管理端
              </el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="读者登录" name="reader">
            <el-alert
              v-if="registeredCard"
              :title="`请牢记借阅证号：${registeredCard}`"
              type="success"
              :closable="false"
              style="margin-bottom: 12px"
            />
            <el-form label-position="top" @submit.prevent="submitReader">
              <el-form-item label="借阅证号">
                <el-input v-model="readerForm.username" :prefix-icon="Tickets" autocomplete="username" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="readerForm.password" :prefix-icon="Lock" type="password" show-password autocomplete="current-password" />
              </el-form-item>
              <el-button type="success" :loading="loading" style="width: 100%" @click="submitReader">
                进入自助端
              </el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="读者注册" name="register">
            <el-form label-position="top" @submit.prevent="submitRegister">
              <el-form-item label="手机号">
                <el-input v-model="registerForm.phone" maxlength="11" placeholder="请输入 11 位手机号" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="registerForm.password" type="password" show-password placeholder="至少 6 位" />
              </el-form-item>
              <el-form-item label="确认密码">
                <el-input v-model="registerForm.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
              </el-form-item>
              <el-button type="warning" :loading="loading" style="width: 100%" @click="submitRegister">
                注册读者账号
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </section>
  </main>
</template>
