<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { CheckCircle2, Clock3, LoaderCircle, LockKeyhole, ShieldCheck, Smartphone, TriangleAlert } from '@lucide/vue'
import api from '../api'
import { formatDateTime, money } from '../utils/format'

const route = useRoute()
const payment = ref(null), loading = ref(true), confirming = ref(false), error = ref(''), now = ref(Date.now())
let timer
const remain = computed(() => payment.value ? Math.max(0, Math.floor((new Date(payment.value.expiresAt).getTime() - now.value) / 1000)) : 0)
const countdown = computed(() => `${String(Math.floor(remain.value / 60)).padStart(2, '0')}:${String(remain.value % 60).padStart(2, '0')}`)
const expired = computed(() => Boolean(payment.value) && remain.value === 0 && payment.value.status !== 'SUCCESS')
const success = computed(() => payment.value?.status === 'SUCCESS')

const load = async () => {
  loading.value = true; error.value = ''
  try { payment.value = await api.get(`/payments/wechat/mock/${route.params.token}`) }
  catch (exception) { error.value = exception.message }
  finally { loading.value = false }
}

const confirm = async () => {
  confirming.value = true; error.value = ''
  try { payment.value = await api.post(`/payments/wechat/mock/${route.params.token}/confirm`) }
  catch (exception) { error.value = exception.message; await load().catch(() => {}) }
  finally { confirming.value = false }
}

onMounted(() => { load(); timer = window.setInterval(() => { now.value = Date.now() }, 1000) })
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <main id="main-content" class="wechat-mock-page" tabindex="-1"><section class="mobile-checkout">
    <header><div class="mock-merchant"><span><Smartphone :size="22"/></span><div><strong>LiveSeat</strong><small>微信 Native 本地仿真</small></div></div><ShieldCheck :size="22"/></header>
    <div v-if="loading" class="mobile-state"><LoaderCircle class="spin" :size="42"/><p>正在读取支付订单</p></div>
    <div v-else-if="success" class="mobile-result success"><CheckCircle2 :size="64"/><p class="eyebrow">PAYMENT CONFIRMED</p><h1>支付确认成功</h1><p>电脑端将在 2 秒内自动同步订单与座位状态。</p><dl><div><dt>订单号</dt><dd>{{ payment.orderNo }}</dd></div><div><dt>支付金额</dt><dd>¥{{ money(payment.amount) }}</dd></div><div><dt>确认时间</dt><dd>{{ formatDateTime(payment.paidAt) }}</dd></div></dl></div>
    <template v-else><div v-if="error" class="mobile-error" role="alert"><TriangleAlert :size="22"/><span>{{ error }}</span></div><div v-if="payment" class="mobile-payment"><div class="mobile-amount"><small>支付金额</small><strong>¥{{ money(payment.amount) }}</strong><span>课程项目模拟支付，不产生真实扣款</span></div><dl><div><dt>收款方</dt><dd>{{ payment.merchant }}</dd></div><div><dt>订单号</dt><dd class="mono">{{ payment.orderNo }}</dd></div></dl><div class="mobile-countdown"><Clock3 :size="18"/><span>支付码剩余</span><strong>{{ countdown }}</strong></div><button class="mobile-confirm" type="button" :disabled="confirming || expired" @click="confirm"><LoaderCircle v-if="confirming" class="spin" :size="20"/><LockKeyhole v-else :size="20"/>{{ confirming ? '正在安全确认…' : expired ? '支付二维码已过期' : '确认模拟支付' }}</button><p class="mobile-legal"><ShieldCheck :size="16"/>本页面仅仿真微信 Native 扫码支付交互，不连接微信商户平台，不会发起真实扣款。</p></div></template>
  </section></main>
</template>
