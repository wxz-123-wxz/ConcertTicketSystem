<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CheckCircle2, Clock3, Eye, EyeOff, ShieldCheck, TicketCheck } from '@lucide/vue'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

const auth = useAuthStore(), ui = useUiStore(), route = useRoute(), router = useRouter()
const mode = ref('login'), submitting = ref(false), error = ref(''), showPassword = ref(false)
const touched = reactive({ username:false, phone:false, password:false })
const form = reactive({ account:'', username:'', phone:'', nickname:'', password:'' })
const phoneError = computed(() => touched.phone && mode.value === 'register' && !/^1[3-9]\d{9}$/.test(form.phone) ? '请输入有效的 11 位中国大陆手机号' : '')
const usernameError = computed(() => touched.username && mode.value === 'register' && (form.username.length < 3 || form.username.length > 30) ? '用户名长度需要在 3–30 个字符之间' : '')
const passwordError = computed(() => touched.password && form.password.length < 6 ? '密码至少需要 6 个字符' : '')
const switchMode = next => { mode.value = next; error.value = ''; touched.username = false; touched.phone = false; touched.password = false }
const submit = async () => {
  touched.username = touched.phone = touched.password = true
  if (phoneError.value || usernameError.value || passwordError.value) return
  submitting.value = true
  error.value = ''
  try {
    if (mode.value === 'login') await auth.login({ account:form.account, password:form.password })
    else await auth.register({ username:form.username, phone:form.phone, nickname:form.nickname, password:form.password })
    ui.notify(mode.value === 'login' ? '登录成功' : '注册成功，已自动登录')
    router.push(String(route.query.redirect || '/'))
  } catch (exception) { error.value = exception.message }
  finally { submitting.value = false }
}
</script>

<template>
  <main id="main-content" class="auth-page" tabindex="-1"><section class="auth-panel">
    <div class="auth-intro"><p class="eyebrow">MEMBER ACCESS</p><h1>进入你的观演席</h1><p>登录后，座位锁定、订单支付和取消释放都会记录在你的账户中。</p><ul><li><TicketCheck :size="20"/>查看全部订单状态</li><li><Clock3 :size="20"/>掌握 10 分钟支付倒计时</li><li><ShieldCheck :size="20"/>Gateway 与 Redis 双重会话校验</li></ul><div class="ticket-stub"><span>LIVESEAT</span><strong>2026</strong><small>ADMIT ONE</small></div></div>
    <form class="auth-form" novalidate @submit.prevent="submit"><div class="tab-switch" role="tablist" aria-label="登录或注册"><button type="button" role="tab" :aria-selected="mode === 'login'" :class="{ active:mode === 'login' }" @click="switchMode('login')">登录</button><button type="button" role="tab" :aria-selected="mode === 'register'" :class="{ active:mode === 'register' }" @click="switchMode('register')">注册</button></div>
      <div><p class="eyebrow dark">{{ mode === 'login' ? 'WELCOME BACK' : 'CREATE ACCOUNT' }}</p><h2>{{ mode === 'login' ? '欢迎回来' : '创建观演账户' }}</h2><p class="auth-form-copy">{{ mode === 'login' ? '使用用户名或手机号继续购票。' : '注册后会立即登录，无需重复操作。' }}</p></div>
      <div v-if="error" class="notice error" role="alert">{{ error }}</div>
      <label v-if="mode === 'login'" for="account">用户名或手机号<input id="account" v-model.trim="form.account" required autocomplete="username" placeholder="例如：admin" /></label>
      <template v-else><label for="username">用户名 <span class="required">必填</span><input id="username" v-model.trim="form.username" required minlength="3" maxlength="30" autocomplete="username" aria-describedby="username-error" @blur="touched.username = true"/><small id="username-error" :class="{ 'field-error': usernameError }">{{ usernameError || '3–30 个字符，用于登录' }}</small></label><label for="phone">手机号 <span class="required">必填</span><input id="phone" v-model.trim="form.phone" required type="tel" inputmode="numeric" pattern="1[3-9][0-9]{9}" autocomplete="tel" aria-describedby="phone-error" @blur="touched.phone = true"/><small id="phone-error" :class="{ 'field-error': phoneError }">{{ phoneError || '用于登录，不会发送真实短信' }}</small></label><label for="nickname">昵称（选填）<input id="nickname" v-model.trim="form.nickname" maxlength="30" autocomplete="nickname" /></label></template>
      <label for="password">密码 <span class="required">必填</span><span class="password-field"><input id="password" v-model="form.password" required minlength="6" maxlength="32" :type="showPassword ? 'text' : 'password'" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" aria-describedby="password-error" @blur="touched.password = true"/><button type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="19"/><Eye v-else :size="19"/></button></span><small id="password-error" :class="{ 'field-error': passwordError }">{{ passwordError || '至少 6 个字符' }}</small></label>
      <button class="button primary full" :disabled="submitting">{{ submitting ? '正在验证账户…' : mode === 'login' ? '登录并继续' : '注册并登录' }}</button>
      <p class="demo-account"><CheckCircle2 :size="16"/>演示管理员：<span class="mono">admin / 123456</span></p>
    </form>
  </section></main>
</template>
