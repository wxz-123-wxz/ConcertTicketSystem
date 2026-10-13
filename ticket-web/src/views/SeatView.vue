<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Armchair, Check, Clock3, Info, RotateCcw, X } from '@lucide/vue'
import api from '../api'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import { formatDateTime, money } from '../utils/format'

const route = useRoute(), router = useRouter(), auth = useAuthStore(), ui = useUiStore()
const seats = ref([]), session = ref(null), selected = ref([]), loading = ref(true), submitting = ref(false), error = ref('')
const area = ref('全部区域')
const recommendCount = ref(2)
const activeSeat = ref(null)
const areas = computed(() => ['全部区域', ...new Set(seats.value.map(seat => seat.areaName).filter(Boolean))])
const filteredSeats = computed(() => area.value === '全部区域' ? seats.value : seats.value.filter(seat => seat.areaName === area.value))
const rows = computed(() => filteredSeats.value.reduce((grouped, seat) => { (grouped[seat.rowNo] ??= []).push(seat); return grouped }, {}))
const selectedSeats = computed(() => seats.value.filter(seat => selected.value.includes(seat.id)).sort((a, b) => a.seatCode.localeCompare(b.seatCode, 'zh-CN', { numeric: true })))
const total = computed(() => selectedSeats.value.reduce((sum, seat) => sum + Number(seat.price), 0))
const counts = computed(() => seats.value.reduce((result, seat) => { result[seat.status] = (result[seat.status] || 0) + 1; return result }, { AVAILABLE: 0, LOCKED: 0, SOLD: 0 }))

const toggle = seat => {
  if (seat.status !== 'AVAILABLE') return
  if (selected.value.includes(seat.id)) selected.value = selected.value.filter(id => id !== seat.id)
  else if (selected.value.length < 6) selected.value.push(seat.id)
  else ui.notify('单个订单最多选择 6 个座位', 'error')
}
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const [nextSeats, nextSession] = await Promise.all([api.get(`/seats/session/${route.params.sessionId}`), api.get(`/shows/sessions/${route.params.sessionId}`)])
    seats.value = nextSeats
    session.value = nextSession
    const availableIds = new Set(nextSeats.filter(seat => seat.status === 'AVAILABLE').map(seat => seat.id))
    const retained = selected.value.filter(id => availableIds.has(id))
    if (retained.length !== selected.value.length) ui.notify('部分座位状态已变化，请重新确认', 'info')
    selected.value = retained
  } catch (exception) { error.value = exception.message }
  finally { loading.value = false }
}
const createOrder = async () => {
  if (!auth.loggedIn) return router.push({ path:'/auth', query:{ redirect:route.fullPath } })
  if (!selected.value.length) return
  submitting.value = true
  error.value = ''
  try {
    const order = await api.post('/orders', { sessionId:Number(route.params.sessionId), seatIds:selected.value })
    ui.notify('座位已锁定，请在 10 分钟内支付')
    router.push(`/pay/${order.orderNo}`)
  } catch (exception) { error.value = exception.message; await load(); selected.value = [] }
  finally { submitting.value = false }
}
const recommendSeats = async () => {
  error.value = ''
  try {
    const params = new URLSearchParams({ count:String(recommendCount.value) })
    if (area.value !== '全部区域') params.set('areaName', area.value)
    const result = await api.get(`/seats/session/${route.params.sessionId}/recommend?${params}`)
    selected.value = result.map(seat => seat.id)
    ui.notify(`已推荐 ${result.map(seat => seat.seatCode).join('、')}`)
  } catch (exception) { error.value = exception.message; ui.notify(exception.message, 'error') }
}
onMounted(load)
</script>

<template>
  <main id="main-content" class="page seat-page" tabindex="-1"><div class="content-rail">
    <div class="seat-title"><div><p class="eyebrow dark">SEAT CONTROL / 实时座位</p><h1>{{ session?.concertTitle || '选择座位' }}</h1><p v-if="session">{{ session.venueName }} · {{ formatDateTime(session.startTime) }}</p></div><button class="button ghost" type="button" :disabled="loading" @click="load"><RotateCcw :size="18"/>{{ loading ? '同步中…' : '刷新状态' }}</button></div>
    <div v-if="error" class="notice error" role="alert"><span>{{ error }}</span><button class="button secondary" type="button" @click="load">重新同步</button></div>

    <div class="seat-control-bar">
      <div class="area-tabs" role="group" aria-label="按区域筛选座位"><button v-for="option in areas" :key="option" type="button" :class="{ active: area === option }" :aria-pressed="area === option" @click="area = option">{{ option }}</button></div>
      <div class="recommend-control"><label>连座人数<select v-model.number="recommendCount"><option v-for="count in 5" :key="count" :value="count + 1">{{ count + 1 }} 人</option></select></label><button class="button secondary" type="button" :disabled="loading" @click="recommendSeats"><Armchair :size="18"/>推荐连座</button></div>
      <dl class="seat-counts"><div><dt>可选</dt><dd>{{ counts.AVAILABLE }}</dd></div><div><dt>锁定</dt><dd>{{ counts.LOCKED }}</dd></div><div><dt>已售</dt><dd>{{ counts.SOLD }}</dd></div></dl>
    </div>

    <div class="seat-layout">
      <section class="seat-map" aria-label="座位图" :aria-busy="loading">
        <div class="stage"><span>STAGE / 舞台</span><div></div><small>所有座位朝向舞台</small></div>
        <div class="legend" aria-label="座位状态图例"><span><i class="available"></i>可选</span><span><i class="selected"></i><Check :size="12"/>已选择</span><span><i class="locked"></i>锁定</span><span><i class="sold"></i><X :size="12"/>已售</span></div>
        <div class="seat-inspector" aria-live="polite"><Info :size="17"/><span v-if="activeSeat"><b>{{ activeSeat.seatCode }}</b> · {{ activeSeat.areaName }} · ¥{{ money(activeSeat.price) }} · {{ activeSeat.status === 'AVAILABLE' ? '可选择' : activeSeat.status === 'LOCKED' ? '暂时锁定' : '已售出' }}</span><span v-else>将鼠标移到座位上，或使用 Tab 键查看座位详情</span></div>
        <div v-if="loading" class="seat-loading"><span v-for="item in 50" :key="item"></span></div>
        <div v-else class="seat-rows">
          <div v-for="(rowSeats, rowNo) in rows" :key="rowNo" class="seat-row"><span class="row-label">{{ rowNo }}排<small>{{ rowSeats[0]?.areaName }}</small></span><div class="seat-buttons">
            <button v-for="seat in rowSeats" :key="seat.id" type="button" class="seat" :class="[seat.status.toLowerCase(), { chosen:selected.includes(seat.id) }]" :disabled="seat.status !== 'AVAILABLE'" :aria-pressed="selected.includes(seat.id)" :aria-label="`${seat.seatCode}，${seat.areaName}，${seat.status === 'AVAILABLE' ? '可选，票价'+seat.price+'元' : seat.status}`" :title="`${seat.seatCode} · ¥${seat.price}`" @mouseenter="activeSeat = seat" @mouseleave="activeSeat = null" @focus="activeSeat = seat" @blur="activeSeat = null" @click="toggle(seat)"><Check v-if="selected.includes(seat.id)" :size="16"/><X v-else-if="seat.status === 'SOLD'" :size="15"/><Armchair v-else :size="17"/><span>{{ seat.colNo }}</span></button>
          </div></div>
        </div>
      </section>

      <aside class="seat-summary" aria-labelledby="summary-title">
        <div class="summary-heading"><div><p class="eyebrow dark">YOUR TICKET</p><h2 id="summary-title">选座清单</h2></div><span class="mono">{{ selected.length }}/6</span></div>
        <div class="ticket-session" v-if="session"><strong>{{ session.concertTitle }}</strong><span>{{ session.venueName }}</span><small>{{ formatDateTime(session.startTime) }}</small></div>
        <div v-if="selectedSeats.length" class="chosen-list"><div v-for="seat in selectedSeats" :key="seat.id"><span><b>{{ seat.seatCode }}</b><small>{{ seat.areaName }}</small></span><strong>¥{{ money(seat.price) }}</strong><button type="button" :aria-label="`移除座位 ${seat.seatCode}`" @click="toggle(seat)"><X :size="16"/></button></div></div>
        <div v-else class="empty-copy"><Armchair :size="28"/><p>从座位图中选择绿色座位，已选座位会在这里按座位号排列。</p></div>
        <div class="ticket-perforation"></div>
        <div class="summary-total"><span>合计 <small>{{ selected.length }} 个座位</small></span><strong>¥{{ money(total) }}</strong></div>
        <p class="hold-note"><Clock3 :size="17"/>提交后座位保留 10 分钟</p>
        <button class="button primary full" :disabled="!selected.length || submitting" @click="createOrder">{{ submitting ? '正在锁定座位…' : auth.loggedIn ? '锁定并创建订单' : '登录后创建订单' }}</button>
        <small class="summary-disclaimer">最终金额和座位状态由服务端再次校验。</small>
      </aside>
    </div>
  </div></main>
</template>
