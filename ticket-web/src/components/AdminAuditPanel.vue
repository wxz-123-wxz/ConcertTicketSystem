<script setup>
import { onMounted, ref } from 'vue'
import { FileClock, RefreshCw, Search } from '@lucide/vue'
import api from '../api'
import LoadingSkeleton from './LoadingSkeleton.vue'
import { formatDateTime } from '../utils/format'
const loading=ref(true),error=ref(''),result=ref({items:[],total:0}),filter=ref({action:'',keyword:''})
const load=async()=>{loading.value=true;error.value='';try{const p=new URLSearchParams(filter.value);result.value=await api.get(`/admin/users/audits/list?${p}`)}catch(e){error.value=e.message}finally{loading.value=false}}
onMounted(load)
</script>
<template><section class="admin-table-panel"><div class="admin-section-title"><div><p class="eyebrow dark">AUDIT TRAIL</p><h2><FileClock :size="21"/>操作审计</h2></div><span class="mono">{{ result.total }} 条记录</span></div><form class="admin-toolbar" @submit.prevent="load"><label>操作类型<select v-model="filter.action"><option value="">全部操作</option><option value="SHOW_CREATE">创建演出</option><option value="SHOW_UPDATE">编辑演出</option><option value="SHOW_STATUS">演出上下架</option><option value="SESSION_CREATE">创建场次</option><option value="SESSION_UPDATE">修改场次</option><option value="USER_STATUS">用户状态</option><option value="REFUND_APPROVE">批准退款</option><option value="REFUND_REJECT">驳回退款</option><option value="TICKET_CHECK_IN">电子票核销</option></select></label><label>搜索<input v-model.trim="filter.keyword" placeholder="管理员 / 目标 / 说明"/></label><button class="button secondary"><Search :size="18"/>查询</button><button class="icon-button light" type="button" aria-label="刷新审计日志" @click="load"><RefreshCw :size="18"/></button></form><div v-if="error" class="notice error">{{ error }}</div><LoadingSkeleton v-if="loading" :rows="3" compact/><div v-else class="audit-list"><article v-for="item in result.items" :key="item.id"><FileClock :size="18"/><div><b>{{ item.action }}</b><span>{{ item.detail }}</span><small>{{ item.operatorName }} · {{ item.targetType }} {{ item.targetId }}</small></div><time>{{ formatDateTime(item.createdAt) }}</time></article></div></section></template>
