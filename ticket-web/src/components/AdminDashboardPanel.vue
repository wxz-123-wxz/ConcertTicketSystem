<script setup>
import { computed, onMounted, ref } from 'vue'
import { Activity, Armchair, CreditCard, RefreshCw, TicketCheck } from '@lucide/vue'
import api from '../api'
import LoadingSkeleton from './LoadingSkeleton.vue'
import { money } from '../utils/format'

const loading = ref(true), error = ref(''), data = ref({ shows:{}, seats:{}, orders:{}, payments:{} })
const occupancy = computed(() => Number(data.value.seats.totalSeats) ? Math.round(Number(data.value.seats.soldSeats || 0) / Number(data.value.seats.totalSeats) * 100) : 0)
const load = async () => {
  loading.value = true; error.value = ''
  try {
    const [shows, seats, orders, payments] = await Promise.all([
      api.get('/admin/shows/metrics'), api.get('/seats/admin/metrics'), api.get('/orders/admin/metrics'), api.get('/payments/admin/metrics')
    ])
    data.value = { shows, seats, orders, payments }
  } catch (exception) { error.value = exception.message }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template><section class="admin-dashboard"><div class="admin-section-title"><div><p class="eyebrow dark">OPERATIONS OVERVIEW</p><h2><Activity :size="21"/>运营概览</h2></div><button class="icon-button light" type="button" aria-label="刷新运营指标" @click="load"><RefreshCw :size="18"/></button></div><div v-if="error" class="notice error" role="alert">{{ error }}</div><LoadingSkeleton v-if="loading" :rows="2" compact/><template v-else><dl class="metric-strip"><div><dt>在售演出</dt><dd>{{ data.shows.onSaleCount || 0 }}</dd><small>共 {{ data.shows.concertCount || 0 }} 个演出</small></div><div><dt>已支付订单</dt><dd>{{ data.orders.paidCount || 0 }}</dd><small>成交 ¥{{ money(data.orders.paidAmount) }}</small></div><div><dt>当前上座率</dt><dd>{{ occupancy }}%</dd><small>{{ data.seats.soldSeats || 0 }}/{{ data.seats.totalSeats || 0 }} 个座位</small></div><div><dt>待处理退款</dt><dd>{{ data.orders.refundPendingCount || 0 }}</dd><small>已退款 {{ data.orders.refundedCount || 0 }} 单</small></div></dl><div class="dashboard-grid"><section class="metric-panel"><h3><TicketCheck :size="19"/>订单状态</h3><div class="metric-bars"><div v-for="item in [{label:'已支付',value:data.orders.paidCount,color:'teal'},{label:'待支付',value:data.orders.pendingCount,color:'amber'},{label:'已关闭',value:data.orders.closedCount,color:'muted'},{label:'已退款',value:data.orders.refundedCount,color:'coral'}]" :key="item.label"><span>{{ item.label }}</span><div><i :class="item.color" :style="{width:`${Math.min(100, Number(item.value || 0) / Math.max(1, Number(data.orders.totalCount || 1)) * 100)}%`}"></i></div><b class="mono">{{ item.value || 0 }}</b></div></div></section><section class="metric-panel"><h3><CreditCard :size="19"/>支付渠道</h3><div class="channel-list"><div v-for="channel in data.payments.channels || []" :key="channel.channel"><span>{{ channel.channel === 'ALIPAY' ? '支付宝沙箱' : '微信模拟支付' }}</span><b>{{ channel.paymentCount }} 笔</b><strong class="mono">¥{{ money(channel.successAmount) }}</strong></div></div></section><section class="metric-panel wide"><h3><Armchair :size="19"/>热门演出</h3><div class="popular-list"><div v-for="(show,index) in data.orders.popularConcerts || []" :key="show.concertId"><span class="mono">{{ String(index+1).padStart(2,'0') }}</span><b>{{ show.title }}</b><small>{{ show.orderCount }} 个订单</small><strong class="mono">¥{{ money(show.paidAmount) }}</strong></div></div></section></div></template></section></template>
