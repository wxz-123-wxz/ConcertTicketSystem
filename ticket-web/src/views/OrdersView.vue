<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CheckCircle2, CircleX, Clock3, Hourglass, QrCode, RefreshCw, RotateCcw, SearchX, TicketCheck } from '@lucide/vue'
import QRCode from 'qrcode'
import api from '../api'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import { useUiStore } from '../stores/ui'
import { formatDateTime, money, orderLabels } from '../utils/format'

const ui = useUiStore()
const route = useRoute(), router = useRouter()
const orders = ref([]), loading = ref(true), error = ref(''), activeStatus = ref('ALL'), now = ref(Date.now()), syncingPayment = ref(false)
const refundOrder = ref(null), refundReason = ref(''), refundSubmitting = ref(false), ticketImages = ref({})
let timer
let paymentSyncStopped = false
const filters = [
  { value:'ALL', label:'全部订单' },
  { value:'PENDING_PAYMENT', label:'待支付' },
  { value:'PAID', label:'已支付' },
  { value:'REFUND_PENDING', label:'退款中' },
  { value:'REFUNDED', label:'已退款' },
  { value:'CANCELLED', label:'已取消' },
  { value:'CLOSED', label:'已超时' }
]
const icons = { PENDING_PAYMENT: Hourglass, PAID: CheckCircle2, REFUND_PENDING: Hourglass, REFUNDED: RotateCcw, CANCELLED: CircleX, CLOSED: Clock3 }
const filteredOrders = computed(() => activeStatus.value === 'ALL' ? orders.value : orders.value.filter(order => order.status === activeStatus.value))
const countFor = status => status === 'ALL' ? orders.value.length : orders.value.filter(order => order.status === status).length
const remaining = order => {
  const seconds = Math.max(0, Math.floor((new Date(order.expireTime).getTime() - now.value) / 1000))
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}
const load = async () => {
  loading.value = true
  error.value = ''
  try { orders.value = await api.get('/orders'); await generateTicketImages() }
  catch (exception) { error.value = exception.message }
  finally { loading.value = false }
}
const generateTicketImages = async () => {
  const next = {}
  for (const order of orders.value) for (const ticket of order.tickets || []) {
    next[ticket.ticketCode] = await QRCode.toDataURL(`${window.location.origin}/check-in/${ticket.ticketCode}`, { width:220, margin:1, color:{dark:'#151719',light:'#FFFFFF'} })
  }
  ticketImages.value = next
}
const openRefund = order => { refundOrder.value = order; refundReason.value = '' }
const submitRefund = async () => {
  if (!refundReason.value.trim()) return
  refundSubmitting.value = true
  try {
    await api.post(`/orders/${refundOrder.value.orderNo}/refunds`, { reason:refundReason.value.trim() })
    ui.notify('退款申请已提交'); refundOrder.value = null; await load()
  } catch (exception) { error.value = exception.message; ui.notify(exception.message, 'error') }
  finally { refundSubmitting.value = false }
}
const cancel = async order => {
  if (!window.confirm(`确认取消订单 ${order.orderNo}？取消后座位会立即释放。`)) return
  try { await api.post(`/orders/${order.orderNo}/cancel`); ui.notify('订单已取消，座位已释放', 'info'); await load() }
  catch (exception) { error.value = exception.message; ui.notify(exception.message, 'error') }
}
const syncAlipayReturn = async () => {
  const storedOrderNo = window.sessionStorage.getItem('liveseat-alipay-pending-order') || ''
  const orderNo = String(route.query.out_trade_no || storedOrderNo)
  if (route.query.alipayReturn !== '1' && !storedOrderNo) return
  if (!orderNo) { ui.notify('支付宝回跳缺少订单号，后台将继续核对支付记录', 'info'); return }
  await router.replace('/orders')
  syncingPayment.value = true
  let confirmed = false
  let finished = false
  for (let attempt = 0; attempt < 30 && !paymentSyncStopped; attempt += 1) {
    try {
      const payment = await api.post(`/payments/alipay/query/${encodeURIComponent(orderNo)}`, null, { timeout:12000 })
      if (payment.status === 'SUCCESS') {
        confirmed = true
        finished = true
        window.sessionStorage.removeItem('liveseat-alipay-pending-order')
        ui.notify('支付宝交易已确认，订单状态已同步')
        await load()
        break
      }
      if (payment.status === 'FAILED') {
        finished = true
        window.sessionStorage.removeItem('liveseat-alipay-pending-order')
        ui.notify('支付宝交易已关闭，可以重新选择支付方式', 'info')
        break
      }
    } catch {
      // 单次网络超时不是支付失败，保留待确认状态继续轮询。
    }
    await new Promise(resolve => window.setTimeout(resolve, 3000))
  }
  syncingPayment.value = false
  if (!finished && !paymentSyncStopped) {
    ui.notify('支付结果仍在后台确认中，请勿重复支付；刷新本页会继续查询', 'info')
    await load()
  }
}
onMounted(async () => { await load(); void syncAlipayReturn(); timer = window.setInterval(() => { now.value = Date.now() }, 1000) })
onUnmounted(() => { paymentSyncStopped = true; window.clearInterval(timer) })
</script>

<template>
  <main id="main-content" class="page orders-page" tabindex="-1"><div class="content-rail narrow">
    <div class="section-heading"><div><p class="eyebrow dark">ORDER DESK / 订单中心</p><h1>我的订单</h1></div><p>支付、取消和超时状态均以服务端记录为准。</p></div>
    <div v-if="syncingPayment" class="notice warning" role="status"><RefreshCw :size="18" class="spin"/><span>支付宝支付结果确认中。网络超时不会判定为未支付，请勿重复支付或切换支付方式…</span></div>
    <div class="order-filter" role="tablist" aria-label="按订单状态筛选">
      <button v-for="filter in filters" :key="filter.value" type="button" role="tab" :class="{ active: activeStatus === filter.value }" :aria-selected="activeStatus === filter.value" @click="activeStatus = filter.value"><span>{{ filter.label }}</span><b class="mono">{{ countFor(filter.value) }}</b></button>
    </div>
    <div v-if="error" class="notice error" role="alert"><span>{{ error }}</span><button class="button secondary" type="button" @click="load"><RefreshCw :size="18"/>重试</button></div>
    <LoadingSkeleton v-if="loading" :rows="3" compact />
    <div v-else-if="!filteredOrders.length" class="empty-state"><SearchX :size="38"/><h2>{{ activeStatus === 'ALL' ? '还没有订单' : `没有${filters.find(item => item.value === activeStatus)?.label}订单` }}</h2><p>选择一场演出和心仪座位，订单会出现在这里。</p><RouterLink class="button primary" to="/">去选择演出</RouterLink></div>
    <div v-else class="order-list">
      <article v-for="order in filteredOrders" :key="order.orderNo" class="order-row">
        <div class="order-main">
          <div class="order-status"><component :is="icons[order.status] || TicketCheck" :size="20"/><span :class="`status ${order.status.toLowerCase()}`">{{ orderLabels[order.status] || order.status }}</span><time>{{ formatDateTime(order.createdAt) }}</time></div>
          <h2>{{ order.items?.map(item => item.seatCode).join(' · ') || '座位信息加载中' }}</h2>
          <p class="mono order-number">订单号 {{ order.orderNo }}</p>
          <div class="order-items"><span v-for="item in order.items" :key="item.id">{{ item.areaName }} <b>{{ item.seatCode }}</b><small>¥{{ money(item.price) }}</small></span></div>
          <div v-if="order.tickets?.length" class="electronic-tickets"><article v-for="ticket in order.tickets" :key="ticket.ticketCode"><img :src="ticketImages[ticket.ticketCode]" :alt="`${ticket.seatCode} 电子票二维码`" width="132" height="132"/><div><span class="status" :class="ticket.status.toLowerCase()">{{ {VALID:'待核销',USED:'已核销',VOID:'已作废'}[ticket.status] }}</span><b>{{ ticket.seatCode }}</b><small class="mono">{{ ticket.ticketCode }}</small></div></article></div>
          <ol class="order-timeline" aria-label="订单进度"><li class="done"><i></i>订单创建</li><li :class="{ done:['PAID','REFUND_PENDING','REFUNDED'].includes(order.status) }"><i></i>{{ order.status === 'PENDING_PAYMENT' ? '等待支付' : orderLabels[order.status] }}</li></ol>
        </div>
        <div class="order-side"><small>订单金额</small><strong class="price">¥{{ money(order.totalAmount) }}</strong><p v-if="order.status === 'PENDING_PAYMENT'" class="order-countdown"><Clock3 :size="16"/>剩余 <b class="mono">{{ remaining(order) }}</b></p><p v-else-if="order.paidAt">支付于 {{ formatDateTime(order.paidAt) }}</p><RouterLink v-if="order.status === 'PENDING_PAYMENT'" class="button primary" :to="`/pay/${order.orderNo}`">继续支付</RouterLink><button v-if="order.status === 'PENDING_PAYMENT'" class="text-button danger" type="button" @click="cancel(order)">取消并释放座位</button><button v-if="order.status === 'PAID'" class="button ghost" type="button" @click="openRefund(order)"><RotateCcw :size="17"/>申请退款</button><span v-if="order.refund" class="refund-note"><QrCode :size="16"/>退款单 {{ order.refund.refundNo }} · {{ {PENDING:'等待审核',APPROVED:'审核通过',REJECTED:'未通过'}[order.refund.status] }}</span></div>
      </article>
    </div>
    <div v-if="refundOrder" class="modal-backdrop" role="presentation" @click.self="refundOrder = null"><form class="dialog-panel" role="dialog" aria-modal="true" aria-labelledby="refund-title" @submit.prevent="submitRefund"><p class="eyebrow dark">AFTER SALES</p><h2 id="refund-title">申请退款</h2><p>订单 <span class="mono">{{ refundOrder.orderNo }}</span></p><label>退款原因<textarea v-model="refundReason" rows="4" maxlength="300" required></textarea><small>{{ refundReason.length }}/300</small></label><div class="dialog-actions"><button class="button ghost" type="button" @click="refundOrder = null">取消</button><button class="button primary" :disabled="refundSubmitting || !refundReason.trim()">{{ refundSubmitting ? '正在提交…' : '提交申请' }}</button></div></form></div>
  </div></main>
</template>
