<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Ticket, UserRound, ClipboardList, LogOut, Settings, Radio, ShieldCheck } from '@lucide/vue'
import { useAuthStore } from './stores/auth'
import { useUiStore } from './stores/ui'
import AppToast from './components/AppToast.vue'

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()
const route = useRoute()
const paymentOnly = computed(() => route.meta.paymentOnly === true)
const displayName = computed(() => auth.user?.nickname || auth.user?.username || '登录')
const logout = async () => {
  await auth.logout().catch(() => {})
  ui.notify('已退出登录', 'info')
  router.push('/')
}
</script>

<template>
  <header v-if="!paymentOnly" class="site-header">
    <div class="nav-rail">
      <RouterLink class="brand" to="/" aria-label="LiveSeat 首页"><span class="brand-mark"><Ticket :size="22" /></span><span>LiveSeat<small>现场选座终端</small></span></RouterLink>
      <nav aria-label="主导航">
        <RouterLink to="/"><Radio :size="18" />演出</RouterLink>
        <RouterLink v-if="auth.loggedIn" to="/orders"><ClipboardList :size="18" />订单</RouterLink>
        <RouterLink v-if="auth.loggedIn" to="/account"><UserRound :size="18" />账户</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin"><Settings :size="18" />管理</RouterLink>
      </nav>
      <div class="account-actions">
        <RouterLink v-if="!auth.loggedIn" class="nav-account" to="/auth"><UserRound :size="18" />{{ displayName }}</RouterLink>
        <span v-else class="user-name"><ShieldCheck v-if="auth.isAdmin" :size="16" />{{ displayName }}</span>
        <button v-if="auth.loggedIn" class="icon-button" type="button" aria-label="退出登录" title="退出登录" @click="logout"><LogOut :size="19" /></button>
      </div>
    </div>
  </header>
  <RouterView />
  <footer v-if="!paymentOnly" class="site-footer"><div class="content-rail footer-inner"><div><strong>LiveSeat</strong><span>演出发现、实时选座与限时支付。</span></div><div class="footer-links"><RouterLink to="/">演出大厅</RouterLink><RouterLink v-if="auth.loggedIn" to="/orders">我的订单</RouterLink></div><div class="footer-tech"><span>Spring Cloud · Redis · Vue 3</span><small>课程项目模拟购票，不产生真实扣款</small></div></div></footer>
  <AppToast />
</template>
