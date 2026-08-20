<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Key, Lock, Tickets, User, UserFilled } from '@element-plus/icons-vue'
import { formBody, http } from '@/api/http'

const emit = defineEmits(['authenticated'])

// 登录页先选身份，再进入对应流程。
const selectedRole = ref('')
const activeAction = ref('login')
const loading = ref(false)
const adminForm = reactive({ username: '', password: '' })
const readerForm = reactive({ username: '', password: '' })
const registerForm = reactive({ phone: '', password: '', confirmPassword: '' })
const passwordForm = reactive({ username: '', oldPassword: '', newPassword: '', confirmPassword: '' })
const registeredCard = ref('')

// 不同身份的标题、图标和按钮文案。
const roleMeta = {
  admin: {
    name: '管理员',
    title: '管理员登录',
    changeTitle: '管理员修改密码',
    usernameLabel: '账号',
    usernameIcon: User,
    buttonText: '进入管理端',
    buttonType: 'primary'
  },
  reader: {
    name: '读者',
    title: '读者登录',
    changeTitle: '读者修改密码',
    usernameLabel: '借阅证号',
    usernameIcon: Tickets,
    buttonText: '进入自助端',
    buttonType: 'success'
  }
}

const currentMeta = computed(() => roleMeta[selectedRole.value] || {})
const isReader = computed(() => selectedRole.value === 'reader')

// 切换身份会回到登录动作。
function chooseRole(role) {
  selectedRole.value = role
  activeAction.value = 'login'
}

function backToRoles() {
  selectedRole.value = ''
  activeAction.value = 'login'
}

function switchAction(action) {
  activeAction.value = action
  passwordForm.username = selectedRole.value === 'reader' ? readerForm.username : adminForm.username
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

// 按当前身份分发登录请求。
function submitLogin() {
  return selectedRole.value === 'reader' ? submitReader() : submitAdmin()
}

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

// 读者自助注册后自动带回借阅证号。
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
    activeAction.value = 'login'
    ElMessage.success(`注册成功，借阅证号：${data.readerCard}`)
  } finally {
    loading.value = false
  }
}

// 管理员和读者共用改密接口。
async function submitChangePassword() {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  loading.value = true
  try {
    await http.post('/auth/change-password', formBody({
      role: selectedRole.value,
      ...passwordForm
    }))
    if (selectedRole.value === 'reader') {
      readerForm.username = passwordForm.username
      readerForm.password = ''
    } else {
      adminForm.username = passwordForm.username
      adminForm.password = ''
    }
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    activeAction.value = 'login'
    ElMessage.success('密码修改成功，请使用新密码登录')
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
      </div>

      <div class="login-panel">
        <div v-if="!selectedRole" class="identity-panel">
          <h2>请选择身份</h2>
          <div class="identity-actions">
            <button class="identity-button reader" type="button" @click="chooseRole('reader')">
              <el-icon><Tickets /></el-icon>
              <span>您是读者</span>
            </button>
            <button class="identity-button admin" type="button" @click="chooseRole('admin')">
              <el-icon><UserFilled /></el-icon>
              <span>您是管理员</span>
            </button>
          </div>
        </div>

        <div v-else class="login-flow">
          <div class="flow-header">
            <el-button text :icon="ArrowLeft" @click="backToRoles">返回</el-button>
            <span class="role-pill">{{ currentMeta.name }}</span>
          </div>

          <template v-if="activeAction === 'login'">
            <h2>{{ currentMeta.title }}</h2>
            <el-alert
              v-if="registeredCard && isReader"
              :title="`请牢记借阅证号：${registeredCard}`"
              type="success"
              :closable="false"
            />
            <el-form label-position="top" @submit.prevent="submitLogin">
              <el-form-item :label="currentMeta.usernameLabel">
                <el-input
                  v-if="isReader"
                  v-model="readerForm.username"
                  :prefix-icon="currentMeta.usernameIcon"
                  autocomplete="username"
                />
                <el-input
                  v-else
                  v-model="adminForm.username"
                  :prefix-icon="currentMeta.usernameIcon"
                  autocomplete="username"
                />
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-if="isReader"
                  v-model="readerForm.password"
                  :prefix-icon="Lock"
                  type="password"
                  show-password
                  autocomplete="current-password"
                />
                <el-input
                  v-else
                  v-model="adminForm.password"
                  :prefix-icon="Lock"
                  type="password"
                  show-password
                  autocomplete="current-password"
                />
              </el-form-item>
              <el-button class="full-button" :type="currentMeta.buttonType" :loading="loading" native-type="submit">
                {{ currentMeta.buttonText }}
              </el-button>
            </el-form>
            <div class="form-links">
              <el-button link type="primary" :icon="Key" @click="switchAction('changePassword')">修改密码</el-button>
              <el-button v-if="isReader" link type="warning" :icon="User" @click="switchAction('register')">读者注册</el-button>
            </div>
          </template>

          <template v-else-if="activeAction === 'register'">
            <h2>读者注册</h2>
            <el-form label-position="top" @submit.prevent="submitRegister">
              <el-form-item label="手机号">
                <el-input v-model="registerForm.phone" maxlength="11" placeholder="请输入 11 位手机号" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="registerForm.password" type="password" show-password placeholder="至少 6 位" autocomplete="new-password" />
              </el-form-item>
              <el-form-item label="确认密码">
                <el-input v-model="registerForm.confirmPassword" type="password" show-password placeholder="请再次输入密码" autocomplete="new-password" />
              </el-form-item>
              <el-button class="full-button" type="warning" :loading="loading" native-type="submit">
                注册读者账号
              </el-button>
            </el-form>
            <div class="form-links">
              <el-button link :icon="ArrowLeft" @click="switchAction('login')">返回登录</el-button>
            </div>
          </template>

          <template v-else>
            <h2>{{ currentMeta.changeTitle }}</h2>
            <el-form label-position="top" @submit.prevent="submitChangePassword">
              <el-form-item :label="currentMeta.usernameLabel">
                <el-input v-model="passwordForm.username" :prefix-icon="currentMeta.usernameIcon" autocomplete="username" />
              </el-form-item>
              <el-form-item label="原密码">
                <el-input v-model="passwordForm.oldPassword" :prefix-icon="Lock" type="password" show-password autocomplete="current-password" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="passwordForm.newPassword" :prefix-icon="Key" type="password" show-password autocomplete="new-password" />
              </el-form-item>
              <el-form-item label="确认新密码">
                <el-input v-model="passwordForm.confirmPassword" :prefix-icon="Key" type="password" show-password autocomplete="new-password" />
              </el-form-item>
              <el-button class="full-button" type="primary" :loading="loading" native-type="submit">
                保存新密码
              </el-button>
            </el-form>
            <div class="form-links">
              <el-button link :icon="ArrowLeft" @click="switchAction('login')">返回登录</el-button>
            </div>
          </template>
        </div>
      </div>
    </section>
  </main>
</template>
