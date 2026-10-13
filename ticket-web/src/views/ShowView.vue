<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight, BadgeInfo, Bell, CalendarDays, Clock3, CreditCard, Heart, MapPin, Ticket } from '@lucide/vue'
import api from '../api'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import { formatDateTime, money } from '../utils/format'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

const route = useRoute()
const auth = useAuthStore(), ui = useUiStore()
const show = ref(null)
const loading = ref(true)
const error = ref('')
const imageFailed = ref(false)
const engagement = ref({ favorite:false, saleReminder:false, sessionReminder:false })
const heroImage = computed(() => show.value?.coverUrl || (Number(route.params.id) === 2 ? '/assets/concert-night.webp' : '/assets/concert-main.webp'))
const firstSession = computed(() => show.value?.sessions?.[0])
const minPrice = session => {
  const prices = (session.categories || []).map(item => Number(item.price)).filter(Number.isFinite)
  return prices.length ? Math.min(...prices) : null
}

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    show.value = await api.get(`/shows/${route.params.id}`)
    if (auth.loggedIn) engagement.value = await api.get(`/shows/${route.params.id}/engagement`)
  }
  catch (exception) { error.value = exception.message }
  finally { loading.value = false }
}
const toggleFavorite = async () => {
  if (!auth.loggedIn) return window.location.href = `/auth?redirect=${encodeURIComponent(route.fullPath)}`
  engagement.value = await api.put(`/shows/${route.params.id}/favorite`, { favorite:!engagement.value.favorite })
  ui.notify(engagement.value.favorite ? '已收藏演出' : '已取消收藏', 'info')
}
const saveSubscription = async () => {
  engagement.value = await api.put(`/shows/${route.params.id}/subscription`, {
    saleReminder:engagement.value.saleReminder, sessionReminder:engagement.value.sessionReminder
  })
  ui.notify('提醒设置已更新')
}
onMounted(load)
</script>

<template>
  <main id="main-content" class="page show-detail-page" tabindex="-1">
    <div class="content-rail">
      <div v-if="error" class="notice error" role="alert"><span>{{ error }}</span><button class="button secondary" type="button" @click="load">重新加载</button></div>
      <LoadingSkeleton v-else-if="loading" :rows="2" />
      <template v-else-if="show">
        <section class="detail-hero">
          <div class="detail-visual" :class="{ 'image-fallback': imageFailed }"><img v-if="!imageFailed" :src="heroImage" :alt="`${show.title} 演出现场`" width="1280" height="720" @error="imageFailed = true" /><span class="detail-visual-label mono">LIVESEAT / EVENT {{ String(show.id).padStart(2, '0') }}</span></div>
          <div class="detail-copy">
            <p class="eyebrow dark">ON SALE / 公开售票</p>
            <h1>{{ show.title }}</h1>
            <p class="detail-artist">{{ show.artist }}</p>
            <p class="description">{{ show.description }}</p>
            <dl class="sale-facts"><div><dt>开售时间</dt><dd>{{ formatDateTime(show.saleStartTime) }}</dd></div><div><dt>停止售票</dt><dd>{{ formatDateTime(show.saleEndTime) }}</dd></div></dl>
            <div class="show-engagement"><button class="button secondary" type="button" :aria-pressed="engagement.favorite" @click="toggleFavorite"><Heart :size="18" :fill="engagement.favorite ? 'currentColor' : 'none'"/>{{ engagement.favorite ? '已收藏' : '收藏演出' }}</button><div v-if="auth.loggedIn" class="reminder-switches"><label><input v-model="engagement.saleReminder" type="checkbox" @change="saveSubscription"/><Bell :size="16"/>开售提醒</label><label><input v-model="engagement.sessionReminder" type="checkbox" @change="saveSubscription"/><CalendarDays :size="16"/>场次变更</label></div></div>
          </div>
        </section>

        <section v-if="firstSession" class="venue-brief" aria-label="首场场馆信息">
          <MapPin :size="23" /><div><small>首场场馆</small><strong>{{ firstSession.venueName }}</strong><span>{{ firstSession.city }} · {{ firstSession.address }}</span></div><div><small>首场时间</small><strong>{{ formatDateTime(firstSession.startTime) }}</strong></div>
        </section>

        <section aria-labelledby="sessions-title" class="sessions-section">
          <div class="section-heading"><div><p class="eyebrow dark">SESSIONS</p><h2 id="sessions-title">选择场次</h2></div><p>进入座位图后，可选择最多 6 个座位。</p></div>
          <div v-if="!show.sessions?.length" class="empty-state"><CalendarDays :size="36" /><h3>暂无公开场次</h3><p>请稍后回来查看新的场次安排。</p></div>
          <article v-for="session in show.sessions" :key="session.id" class="session-row">
            <div class="session-status"><i></i><span>可选座</span></div>
            <div class="session-date"><CalendarDays :size="22"/><strong>{{ formatDateTime(session.startTime) }}</strong><span>预计 {{ new Date(session.endTime).toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit', hour12:false }) }} 结束</span></div>
            <div class="session-venue"><span><MapPin :size="18"/>{{ session.venueName }}</span><small>{{ session.city }} · {{ session.address }}</small></div>
            <div class="category-list"><span v-for="item in session.categories" :key="item.id">{{ item.areaName }} <b>¥{{ money(item.price) }}</b></span></div>
            <div class="session-action"><small>{{ minPrice(session) == null ? '票价待配置' : '票价起' }}</small><strong class="mono">{{ minPrice(session) == null ? '¥--' : `¥${money(minPrice(session))}` }}</strong><RouterLink class="button primary" :to="`/seats/${session.id}`">进入选座 <ArrowRight :size="18"/></RouterLink></div>
          </article>
        </section>

        <section class="booking-rules" aria-labelledby="rules-title">
          <div><p class="eyebrow dark">BOOKING NOTES</p><h2 id="rules-title">购票须知</h2><p>以下规则与当前系统实际流程一致。</p></div>
          <ul><li><Ticket :size="22"/><span><b>每单限购 6 张</b><small>同一订单不可重复选择座位。</small></span></li><li><Clock3 :size="22"/><span><b>锁座 10 分钟</b><small>超时未支付，订单关闭并释放座位。</small></span></li><li><CreditCard :size="22"/><span><b>模拟支付</b><small>课程项目不会产生真实扣款。</small></span></li><li><BadgeInfo :size="22"/><span><b>状态实时校验</b><small>最终座位状态和金额以服务端为准。</small></span></li></ul>
        </section>
      </template>
    </div>
  </main>
</template>
