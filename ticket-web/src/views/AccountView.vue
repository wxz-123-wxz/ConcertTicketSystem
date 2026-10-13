<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Bell, Heart, KeyRound, RefreshCw, ShieldCheck, UserRound } from '@lucide/vue'
import api from '../api'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import { formatDateTime, money } from '../utils/format'

const auth = useAuthStore(), ui = useUiStore()
const active = ref('profile'), loading = ref(true), saving = ref(false), error = ref('')
const favorites = ref([]), notifications = ref([])
const profile = reactive({ nickname:auth.user?.nickname || '', phone:auth.user?.phone || '' })
const password = reactive({ oldPassword:'', newPassword:'' })
const tabs = [{value:'profile',label:'个人资料',icon:UserRound},{value:'favorites',label:'收藏演出',icon:Heart},{value:'notifications',label:'站内通知',icon:Bell}]

const load = async () => {
  loading.value = true; error.value = ''
  try {
    const [me, favoriteData, notificationData] = await Promise.all([
      api.get('/auth/me'), api.get('/shows/favorites'), api.get('/shows/notifications')
    ])
    auth.updateUser(me); profile.nickname = me.nickname; profile.phone = me.phone
    favorites.value = favoriteData; notifications.value = notificationData
  } catch (exception) { error.value = exception.message }
  finally { loading.value = false }
}
const saveProfile = async () => {
  saving.value = true; error.value = ''
  try { const user = await api.put('/auth/profile', profile); auth.updateUser(user); ui.notify('个人资料已更新') }
  catch (exception) { error.value = exception.message; ui.notify(exception.message, 'error') }
  finally { saving.value = false }
}
const savePassword = async () => {
  saving.value = true; error.value = ''
  try {
    await api.put('/auth/password', password); ui.notify('密码已更新，请重新登录', 'info'); auth.clear(); window.location.href = '/auth'
  } catch (exception) { error.value = exception.message; ui.notify(exception.message, 'error') }
  finally { saving.value = false }
}
const removeFavorite = async show => {
  await api.put(`/shows/${show.id}/favorite`, { favorite:false }); favorites.value = favorites.value.filter(item => item.id !== show.id); ui.notify('已取消收藏', 'info')
}
const markRead = async item => {
  if (item.isRead) return
  await api.patch(`/shows/notifications/${item.id}/read`); item.isRead = true
}
onMounted(load)
</script>

<template>
  <main id="main-content" class="page account-page" tabindex="-1"><div class="content-rail narrow">
    <div class="section-heading"><div><p class="eyebrow dark">MEMBER DESK / 账户中心</p><h1>我的账户</h1></div><p>管理登录资料、关注演出和场次提醒。</p></div>
    <nav class="account-tabs" aria-label="账户功能"><button v-for="tab in tabs" :key="tab.value" type="button" :class="{active:active === tab.value}" @click="active = tab.value"><component :is="tab.icon" :size="18"/>{{ tab.label }}</button></nav>
    <div v-if="error" class="notice error" role="alert"><span>{{ error }}</span><button class="button secondary" type="button" @click="load"><RefreshCw :size="18"/>重试</button></div>
    <LoadingSkeleton v-if="loading" :rows="2" compact/>
    <div v-else-if="active === 'profile'" class="account-profile-grid">
      <form class="account-form" @submit.prevent="saveProfile"><div><p class="eyebrow dark">PROFILE</p><h2><UserRound :size="21"/>个人资料</h2></div><label>用户名<input :value="auth.user?.username" disabled/><small>用户名用于登录，当前不可修改。</small></label><label>昵称<input v-model.trim="profile.nickname" maxlength="30" required/></label><label>手机号<input v-model.trim="profile.phone" type="tel" inputmode="numeric" pattern="1[3-9][0-9]{9}" required/></label><button class="button primary" :disabled="saving">{{ saving ? '正在保存…' : '保存资料' }}</button></form>
      <form class="account-form security" @submit.prevent="savePassword"><div><p class="eyebrow dark">SECURITY</p><h2><ShieldCheck :size="21"/>账号安全</h2></div><label>当前密码<input v-model="password.oldPassword" type="password" minlength="6" required autocomplete="current-password"/></label><label>新密码<input v-model="password.newPassword" type="password" minlength="6" maxlength="32" required autocomplete="new-password"/></label><p class="form-hint"><KeyRound :size="16"/>修改密码后，所有登录会话都会退出。</p><button class="button secondary" :disabled="saving">更新密码</button></form>
    </div>
    <section v-else-if="active === 'favorites'" class="account-list"><div class="admin-section-title"><div><p class="eyebrow dark">FAVORITES</p><h2><Heart :size="21"/>收藏演出</h2></div><span class="mono">{{ favorites.length }} 场</span></div><div v-if="!favorites.length" class="empty-state compact"><Heart :size="34"/><h3>还没有收藏演出</h3><p>在演出详情页收藏后，会集中显示在这里。</p></div><article v-for="show in favorites" :key="show.id" class="account-show-row"><div><b>{{ show.title }}</b><span>{{ show.artist }} · {{ show.city || '城市待定' }}</span><small>{{ formatDateTime(show.nextSessionTime) }} · ¥{{ money(show.minPrice) }} 起</small></div><div><RouterLink class="button secondary" :to="`/shows/${show.id}`">查看演出</RouterLink><button class="text-button danger" type="button" @click="removeFavorite(show)">取消收藏</button></div></article></section>
    <section v-else class="account-list"><div class="admin-section-title"><div><p class="eyebrow dark">NOTIFICATIONS</p><h2><Bell :size="21"/>站内通知</h2></div><span class="mono">{{ notifications.filter(item => !item.isRead).length }} 条未读</span></div><div v-if="!notifications.length" class="empty-state compact"><Bell :size="34"/><h3>暂无通知</h3><p>关注的演出开售或场次变化后会在这里提醒。</p></div><button v-for="item in notifications" :key="item.id" class="notification-row" :class="{unread:!item.isRead}" type="button" @click="markRead(item)"><i></i><span><b>{{ item.title }}</b><small>{{ item.content }}</small><time>{{ formatDateTime(item.createdAt) }}</time></span></button></section>
  </div></main>
</template>
