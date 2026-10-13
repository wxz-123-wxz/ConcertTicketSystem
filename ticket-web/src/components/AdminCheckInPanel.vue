<script setup>
import { onMounted, ref } from 'vue'
import { CheckCircle2, ScanLine, Search } from '@lucide/vue'
import api from '../api'
import { useUiStore } from '../stores/ui'
import { formatDateTime } from '../utils/format'
const props=defineProps({initialToken:{type:String,default:''}}), ui=useUiStore(), token=ref(props.initialToken), ticket=ref(null), error=ref(''), loading=ref(false)
const lookup=async()=>{if(!token.value.trim())return;loading.value=true;error.value='';try{ticket.value=await api.get(`/orders/admin/tickets/${encodeURIComponent(token.value.trim())}`)}catch(e){error.value=e.message;ticket.value=null}finally{loading.value=false}}
const checkIn=async()=>{loading.value=true;try{ticket.value=await api.post(`/orders/admin/tickets/${encodeURIComponent(token.value.trim())}/check-in`);ui.notify('电子票核销成功')}catch(e){error.value=e.message;ui.notify(e.message,'error')}finally{loading.value=false}}
onMounted(()=>{if(props.initialToken)lookup()})
</script>
<template><section class="checkin-panel"><div><p class="eyebrow dark">ADMISSION CONTROL</p><h2><ScanLine :size="22"/>电子票核销</h2></div><form class="checkin-search" @submit.prevent="lookup"><label>电子票码<input v-model.trim="token" class="mono" placeholder="TKT..." required/></label><button class="button secondary" :disabled="loading"><Search :size="18"/>查询票码</button></form><div v-if="error" class="notice error">{{ error }}</div><article v-if="ticket" class="checkin-result"><div><span class="status" :class="ticket.status.toLowerCase()">{{ {VALID:'待核销',USED:'已核销',VOID:'已作废'}[ticket.status] }}</span><h3>{{ ticket.seatCode }}</h3><p class="mono">{{ ticket.ticketCode }}</p></div><dl><div><dt>订单号</dt><dd class="mono">{{ ticket.orderNo }}</dd></div><div><dt>核销时间</dt><dd>{{ ticket.checkedInAt ? formatDateTime(ticket.checkedInAt) : '尚未核销' }}</dd></div></dl><button v-if="ticket.status==='VALID'" class="button primary" type="button" :disabled="loading" @click="checkIn"><CheckCircle2 :size="19"/>确认核销</button></article></section></template>
