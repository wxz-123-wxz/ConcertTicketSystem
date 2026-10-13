<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QRCode from 'qrcode'
import { ArrowLeft, CheckCircle2, Clock3, ExternalLink, MonitorSmartphone, QrCode, RefreshCw, ShieldCheck, Smartphone, TriangleAlert } from '@lucide/vue'
import api from '../api'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import { useUiStore } from '../stores/ui'
import { formatDateTime, money } from '../utils/format'

const route = useRoute(), router = useRouter(), ui = useUiStore()
const order = ref(null), channel = ref('ALIPAY'), submitting = ref(false), loading = ref(true), error = ref(''), now = ref(Date.now()), success = ref(false)
const wechat = ref(null), qrDataUrl = ref(''), mobileOrigin = ref(window.location.origin)
const payment = ref(null), alipayPending = ref(false)
let timer, pollTimer, alipayPollTimer

const remain = computed(() => order.value ? Math.max(0, Math.floor((new Date(order.value.expireTime).getTime() - now.value) / 1000)) : 0)
const expired = computed(() => Boolean(order.value) && order.value.status === 'PENDING_PAYMENT' && remain.value === 0)
const countdown = computed(() => `${String(Math.floor(remain.value / 60)).padStart(2, '0')}:${String(remain.value % 60).padStart(2, '0')}`)
const mockUrl = computed(() => wechat.value?.token ? `${mobileOrigin.value.replace(/\/$/, '')}/wechat-pay/${wechat.value.token}` : '')
const localOnlyOrigin = computed(() => {
  try { return ['localhost', '127.0.0.1'].includes(new URL(mobileOrigin.value, window.location.origin).hostname) }
  catch { return true }
})
const qrExpired = computed(() => Boolean(wechat.value?.expiresAt) && new Date(wechat.value.expiresAt).getTime() <= now.value)
const wechatBusy = computed(() => ['WAITING', 'CONFIRMING'].includes(wechat.value?.status) && !qrExpired.value)

const loadMobileOrigin = async () => {
  if (!['localhost', '127.0.0.1'].includes(window.location.hostname)) {
    mobileOrigin.value = window.location.origin
    return
  }
  try {
    const network = await api.get('/payments/wechat/network-origin')
    if (network?.origin) mobileOrigin.value = network.origin
  } catch {
    mobileOrigin.value = window.location.origin
  }
}

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    order.value = await api.get(`/orders/${route.params.orderNo}`)
    success.value = order.value.status === 'PAID'
    if (success.value) {
      alipayPending.value = false
      window.sessionStorage.removeItem('liveseat-alipay-pending-order')
    } else {
      try {
        payment.value = await api.get(`/payments/${route.params.orderNo}`)
        if (payment.value.status === 'PROCESSING' && payment.value.channel === 'ALIPAY') {
          channel.value = 'ALIPAY'
          alipayPending.value = true
          startAlipayPolling()
        }
      } catch {
        payment.value = null
      }
    }
  } catch (exception) { error.value = exception.message }
  finally { loading.value = false }
}

const renderQr = async () => {
  if (!mockUrl.value) { qrDataUrl.value = ''; return }
  try {
    qrDataUrl.value = await QRCode.toDataURL(mockUrl.value, {
      width: 280, margin: 2, errorCorrectionLevel: 'M',
      color: { dark:'#151719', light:'#ffffff' }
    })
  } catch { qrDataUrl.value = '' }
}

const stopPolling = () => {
  if (pollTimer) window.clearInterval(pollTimer)
  pollTimer = undefined
}

const pollWechat = async () => {
  try {
    const status = await api.get(`/payments/wechat/status/${route.params.orderNo}`)
    if (wechat.value) wechat.value = { ...wechat.value, ...status, status:status.mockStatus || status.status }
    if (status.status === 'SUCCESS') {
      stopPolling()
      success.value = true
      await load()
      ui.notify('微信模拟支付确认成功，座位已售出')
    } else if (status.mockStatus === 'FAILED') {
      stopPolling()
      error.value = status.failureMessage || '手机端确认失败，请重新生成支付二维码'
    }
  } catch (exception) {
    stopPolling()
    error.value = exception.message
  }
}

const startPolling = () => {
  stopPolling()
  pollTimer = window.setInterval(pollWechat, 2000)
}

const stopAlipayPolling = () => {
  if (alipayPollTimer) window.clearTimeout(alipayPollTimer)
  alipayPollTimer = undefined
}

const pollAlipay = async () => {
  if (!alipayPending.value) return
  try {
    payment.value = await api.post(`/payments/alipay/query/${route.params.orderNo}`, null, { timeout:12000 })
    if (payment.value.status === 'SUCCESS') {
      alipayPending.value = false
      stopAlipayPolling()
      window.sessionStorage.removeItem('liveseat-alipay-pending-order')
      await load()
      ui.notify('支付宝交易已确认，订单状态已同步')
      return
    }
    if (payment.value.status === 'FAILED') {
      alipayPending.value = false
      stopAlipayPolling()
      window.sessionStorage.removeItem('liveseat-alipay-pending-order')
      ui.notify('支付宝交易已关闭，可以重新选择支付方式', 'info')
      return
    }
  } catch {
    // 查询超时只代表结果未知，后台和当前页面都会继续对账。
  }
  if (alipayPending.value) alipayPollTimer = window.setTimeout(pollAlipay, 3000)
}

const startAlipayPolling = () => {
  stopAlipayPolling()
  alipayPollTimer = window.setTimeout(pollAlipay, 300)
}

const startWechat = async () => {
  submitting.value = true
  error.value = ''
  try {
    const checkout = await api.post('/payments/wechat/native', { orderNo:route.params.orderNo })
    if (checkout.alreadyPaid) { success.value = true; await load(); return }
    wechat.value = checkout
    await renderQr()
    startPolling()
  } catch (exception) {
    error.value = exception.message
    ui.notify(exception.message, 'error')
  } finally { submitting.value = false }
}

const pay = async () => {
  if (channel.value === 'WECHAT') return startWechat()
  submitting.value = true
  error.value = ''
  try {
    const checkout = await api.post('/payments/alipay/page-pay', { orderNo:route.params.orderNo })
    if (checkout.alreadyPaid) { success.value = true; await load(); return }
    if (checkout.reconciliationPending || !checkout.gatewayUrl) {
      payment.value = checkout
      alipayPending.value = true
      window.sessionStorage.setItem('liveseat-alipay-pending-order', String(route.params.orderNo))
      startAlipayPolling()
      return
    }
    const form = document.createElement('form')
    form.method = 'POST'; form.action = checkout.gatewayUrl; form.style.display = 'none'
    const alipayFieldNames = { appId:'app_id', bizContent:'biz_content', signType:'sign_type', returnUrl:'return_url' }
    Object.entries(checkout.fields || {}).forEach(([name, value]) => {
      const input = document.createElement('input')
      input.type = 'hidden'; input.name = alipayFieldNames[name] || name; input.value = value
      form.appendChild(input)
    })
    document.body.appendChild(form)
    window.sessionStorage.setItem('liveseat-alipay-pending-order', String(route.params.orderNo))
    form.submit()
  } catch (exception) {
    error.value = exception.message
    ui.notify(exception.message, 'error')
  } finally { submitting.value = false }
}

watch(mobileOrigin, renderQr)
watch(channel, value => { if (value === 'ALIPAY') stopPolling(); else if (wechatBusy.value) startPolling() })
onMounted(async () => {
  await loadMobileOrigin()
  load()
  timer = window.setInterval(() => { now.value = Date.now(); if (qrExpired.value) stopPolling() }, 1000)
})
onUnmounted(() => { window.clearInterval(timer); stopPolling(); stopAlipayPolling() })
</script>

<template>
  <main id="main-content" class="page payment-page" tabindex="-1"><div class="content-rail payment-shell">
    <LoadingSkeleton v-if="loading" :rows="2" />
    <section v-else-if="success" class="success-panel"><div class="success-mark"><CheckCircle2 :size="58"/></div><p class="eyebrow dark">PAYMENT CONFIRMED</p><h1>支付完成，座位已确认</h1><p>订单 <span class="mono">{{ route.params.orderNo }}</span> 已进入已支付状态。</p><div class="success-ticket"><span>LIVESEAT</span><strong>{{ order?.items?.map(item => item.seatCode).join(' · ') }}</strong><small>ADMIT CONFIRMED</small></div><div class="success-actions"><button class="button secondary" type="button" @click="router.push('/')">返回演出大厅</button><button class="button primary" type="button" @click="router.push('/orders')">查看我的订单</button></div></section>
    <template v-else>
      <section class="payment-main"><button class="back-link" type="button" @click="router.push('/orders')"><ArrowLeft :size="18"/>返回订单</button><p class="eyebrow dark">SECURE CHECKOUT</p><h1>确认支付</h1><div class="simulation-notice"><ShieldCheck :size="22"/><span><b>沙箱与模拟支付环境</b><small>支付宝进入官方沙箱收银台；微信使用本地 Native 仿真链路，均不会扣除真实资金。</small></span></div><div v-if="alipayPending" class="notice warning" role="status"><RefreshCw :size="18" class="spin"/><span>支付宝支付结果确认中。查询超时不会判定为未支付，确认完成前不能重复支付或切换微信。</span></div><div v-if="error" class="notice error" role="alert"><span>{{ error }}</span><button class="button secondary" type="button" @click="error = ''; load()"><RefreshCw :size="18"/>重新读取订单</button></div><div v-if="expired && !alipayPending" class="notice warning" role="alert"><TriangleAlert :size="20"/><span>支付时间已结束，订单将由服务端关闭并释放座位。请返回演出页面重新选座。</span></div>
        <div v-if="order" class="payment-order"><div><span>订单号</span><strong class="mono">{{ order.orderNo }}</strong></div><div><span>创建时间</span><strong>{{ formatDateTime(order.createdAt) }}</strong></div><div><span>座位</span><strong>{{ order.items?.map(item => item.seatCode).join(' · ') }}</strong></div><div><span>应付金额</span><strong class="pay-amount">¥{{ money(order.totalAmount) }}</strong></div></div>
        <fieldset :disabled="expired || submitting || alipayPending"><legend>选择支付方式</legend><label class="pay-method"><input v-model="channel" type="radio" value="ALIPAY"/><Smartphone :size="23"/><span><b>支付宝沙箱</b><small>跳转至支付宝官方沙箱收银台</small></span><i>选择</i></label><label class="pay-method"><input v-model="channel" type="radio" value="WECHAT"/><QrCode :size="23"/><span><b>微信 Native 仿真</b><small>扫码后在手机模拟收银台确认</small></span><i>选择</i></label></fieldset>
        <section v-if="channel === 'WECHAT' && wechat" class="wechat-native-panel" aria-live="polite"><div class="wechat-qr"><img v-if="qrDataUrl" :src="qrDataUrl" alt="微信模拟支付二维码"/><div v-else class="qr-placeholder"><QrCode :size="48"/><span>二维码生成失败</span></div><span v-if="qrExpired" class="qr-expired">二维码已过期</span></div><div class="wechat-instructions"><p class="eyebrow dark">NATIVE PAYMENT</p><h2>{{ wechat.status === 'CONFIRMING' ? '正在确认支付' : '请使用手机扫码' }}</h2><ol><li>手机与电脑连接同一局域网</li><li>扫码打开 LiveSeat 模拟收银台</li><li>确认后本页自动同步订单状态</li></ol><label>手机访问地址<input v-model.trim="mobileOrigin" type="url" placeholder="http://192.168.1.10:5173"/><small v-if="localOnlyOrigin">未能自动识别局域网地址，请手动填写电脑当前 IPv4 地址。</small><small v-else>已自动使用电脑当前联网地址；更换网络后重新打开支付页即可更新。</small></label><a class="button secondary" :href="mockUrl" target="_blank" rel="noopener"><MonitorSmartphone :size="18"/>在此浏览器模拟扫码</a></div></section>
      </section>
      <aside class="payment-summary"><p><Clock3 :size="18"/>支付剩余时间</p><strong class="countdown" :class="{ expired }">{{ countdown }}</strong><small>{{ alipayPending ? '订单已进入支付保护，系统正在持续核对支付宝结果。' : expired ? '本次锁座已到期，请重新选择座位。' : '超时后订单会自动关闭，座位重新开放。' }}</small><dl><div><dt>订单金额</dt><dd>¥{{ money(order?.totalAmount) }}</dd></div><div><dt>支付方式</dt><dd>{{ { ALIPAY:'支付宝沙箱', WECHAT:'微信 Native 仿真' }[channel] }}</dd></div><div v-if="channel === 'WECHAT' && wechat"><dt>扫码状态</dt><dd>{{ { WAITING:'等待扫码', CONFIRMING:'确认中', FAILED:'失败', SUCCESS:'成功' }[wechat.status] || '等待扫码' }}</dd></div></dl><button v-if="!alipayPending && (channel === 'ALIPAY' || !wechat || qrExpired || wechat.status === 'FAILED')" class="button primary full" :disabled="submitting || !order || expired" @click="pay"><ExternalLink v-if="channel === 'ALIPAY' && !submitting" :size="18"/><QrCode v-else-if="channel === 'WECHAT' && !submitting" :size="18"/>{{ submitting ? '正在准备支付…' : expired ? '支付时间已结束' : channel === 'ALIPAY' ? `前往支付宝支付 ¥${money(order?.totalAmount)}` : wechat ? '重新生成支付二维码' : `生成支付二维码 ¥${money(order?.totalAmount)}` }}</button><div v-else class="polling-status"><span class="live-dot"></span><b>{{ alipayPending ? '正在确认支付宝结果' : '等待手机端确认' }}</b><small>{{ alipayPending ? '本页与后台会持续自动查询' : '本页每 2 秒自动查询一次' }}</small></div></aside>
    </template>
  </div></main>
</template>
