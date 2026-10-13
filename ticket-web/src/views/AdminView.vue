<script setup>
import { computed, onMounted, ref } from 'vue'
import { Activity, CalendarRange, CreditCard, FileClock, Image, ListChecks, Pencil, Plus, Radio, RefreshCw, RotateCcw, ScanLine, Search, Ticket, Users } from '@lucide/vue'
import api from '../api'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import { useUiStore } from '../stores/ui'
import { formatDateTime, money, orderLabels } from '../utils/format'
import AdminDashboardPanel from '../components/AdminDashboardPanel.vue'
import AdminRefundsPanel from '../components/AdminRefundsPanel.vue'
import AdminCheckInPanel from '../components/AdminCheckInPanel.vue'
import AdminAuditPanel from '../components/AdminAuditPanel.vue'
import AdminUserDetail from '../components/AdminUserDetail.vue'

const ui = useUiStore()
const activeTab = ref('dashboard'), loading = ref(true), submitting = ref(false), error = ref(''), message = ref('')
const shows = ref([]), publicShows = ref([]), venues = ref([]), sessions = ref([]), ordersResult = ref({ items:[], total:0, page:1, size:20 }), paymentsResult = ref({ items:[], total:0, page:1, size:20 }), usersResult = ref({ items:[], total:0, page:1, size:20 })
const imageFailed = ref(false)
const editingShowId = ref(null), selectedUserId = ref(null)
const showForm = ref({ title:'', artist:'', description:'', coverUrl:'/assets/concert-main.webp', saleStartTime:'', saleEndTime:'' })
const freshSessionForm = () => ({ concertId:'', venueId:'', startTime:'', endTime:'', status:'ON_SALE', categories:[
  { name:'A区票', areaName:'A区', price:1280, color:'#E4493D', rowCount:3, seatsPerRow:10 },
  { name:'B区票', areaName:'B区', price:680, color:'#17867A', rowCount:5, seatsPerRow:10 }
] })
const sessionForm = ref(freshSessionForm())
const orderFilter = ref({ status:'', keyword:'', page:1 }), paymentFilter = ref({ status:'', channel:'', keyword:'', page:1 }), userFilter = ref({ role:'', status:'', keyword:'', page:1 })
const tabs = [
  { value:'dashboard', label:'运营概览', icon:Activity },
  { value:'shows', label:'演出管理', icon:Radio },
  { value:'sessions', label:'场次配置', icon:CalendarRange },
  { value:'orders', label:'订单管理', icon:ListChecks },
  { value:'payments', label:'支付流水', icon:CreditCard },
  { value:'users', label:'用户账号', icon:Users },
  { value:'refunds', label:'退款售后', icon:RotateCcw },
  { value:'checkin', label:'电子验票', icon:ScanLine },
  { value:'audits', label:'操作审计', icon:FileClock }
]
const nextShow = computed(() => [...publicShows.value].filter(item => item.nextSessionTime).sort((a, b) => new Date(a.nextSessionTime) - new Date(b.nextSessionTime))[0])
const localDateTime = value => value?.length === 16 ? `${value}:00` : value
const dateTimeInput = value => value ? String(value).slice(0, 16) : ''
const statusText = status => ({ DRAFT:'草稿', ON_SALE:'售票中', OFF_SALE:'已下架', PROCESSING:'处理中', SUCCESS:'成功', FAILED:'失败', ACTIVE:'正常', DISABLED:'已禁用' })[status] || orderLabels[status] || status
const channelText = channel => ({ ALIPAY:'支付宝沙箱', WECHAT:'微信模拟支付' })[channel] || channel
const roleText = role => ({ ADMIN:'管理员', USER:'普通用户' })[role] || role

const loadBase = async () => {
  const [catalog, venuesData, published] = await Promise.all([api.get('/admin/shows'), api.get('/admin/venues'), api.get('/shows')])
  shows.value = catalog; venues.value = venuesData; publicShows.value = published
}
const loadSessions = async () => { sessions.value = await api.get('/admin/sessions') }
const loadOrders = async () => {
  const params = new URLSearchParams({ page:String(orderFilter.value.page), size:'20' })
  if (orderFilter.value.status) params.set('status', orderFilter.value.status)
  if (orderFilter.value.keyword.trim()) params.set('keyword', orderFilter.value.keyword.trim())
  ordersResult.value = await api.get(`/orders/admin/list?${params}`)
}
const loadPayments = async () => {
  const params = new URLSearchParams({ page:String(paymentFilter.value.page), size:'20' })
  if (paymentFilter.value.status) params.set('status', paymentFilter.value.status)
  if (paymentFilter.value.channel) params.set('channel', paymentFilter.value.channel)
  if (paymentFilter.value.keyword.trim()) params.set('keyword', paymentFilter.value.keyword.trim())
  paymentsResult.value = await api.get(`/payments/admin/list?${params}`)
}
const loadUsers = async () => {
  const params = new URLSearchParams({ page:String(userFilter.value.page), size:'20' })
  if (userFilter.value.role) params.set('userRole', userFilter.value.role)
  if (userFilter.value.status) params.set('status', userFilter.value.status)
  if (userFilter.value.keyword.trim()) params.set('keyword', userFilter.value.keyword.trim())
  usersResult.value = await api.get(`/admin/users?${params}`)
}
const loadActive = async () => {
  loading.value = true; error.value = ''
  try {
    if (!shows.value.length || !venues.value.length) await loadBase()
    if (activeTab.value === 'sessions') await loadSessions()
    if (activeTab.value === 'orders') await loadOrders()
    if (activeTab.value === 'payments') await loadPayments()
    if (activeTab.value === 'users') await loadUsers()
  } catch (exception) { error.value = exception.message }
  finally { loading.value = false }
}
const switchTab = async value => { activeTab.value = value; message.value = ''; await loadActive() }
const createShow = async () => {
  submitting.value = true; error.value = ''; message.value = ''
  try {
    const payload = { ...showForm.value, saleStartTime:localDateTime(showForm.value.saleStartTime), saleEndTime:localDateTime(showForm.value.saleEndTime) }
    const saved = editingShowId.value ? await api.put(`/admin/shows/${editingShowId.value}`, payload) : await api.post('/admin/shows', payload)
    message.value = editingShowId.value ? '演出资料已更新' : `草稿已创建，演出 ID ${saved.id}`; ui.notify(message.value)
    resetShowForm(); await loadBase()
  } catch (exception) { error.value = exception.message; ui.notify(exception.message, 'error') }
  finally { submitting.value = false }
}
const editShow = show => {
  editingShowId.value = show.id; imageFailed.value = false
  showForm.value = { title:show.title, artist:show.artist, description:show.description, coverUrl:show.coverUrl, saleStartTime:dateTimeInput(show.saleStartTime), saleEndTime:dateTimeInput(show.saleEndTime) }
  window.scrollTo({ top:document.querySelector('.admin-form')?.offsetTop || 0, behavior:'smooth' })
}
const resetShowForm = () => { editingShowId.value = null; showForm.value = { title:'', artist:'', description:'', coverUrl:'/assets/concert-main.webp', saleStartTime:'', saleEndTime:'' } }
const changeShowStatus = async show => {
  const next = show.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  if (!window.confirm(`确认将“${show.title}”设为${next === 'ON_SALE' ? '公开售票' : '下架'}？`)) return
  try { await api.patch(`/admin/shows/${show.id}/status`, { status:next }); ui.notify('演出状态已更新'); await loadBase() }
  catch (exception) { error.value = exception.message; ui.notify(exception.message, 'error') }
}
const addCategory = () => sessionForm.value.categories.push({ name:'新票档', areaName:'C区', price:380, color:'#626A70', rowCount:2, seatsPerRow:10 })
const removeCategory = index => { if (sessionForm.value.categories.length > 1) sessionForm.value.categories.splice(index, 1) }
const createSession = async () => {
  submitting.value = true; error.value = ''; message.value = ''
  try {
    const payload = { ...sessionForm.value, concertId:Number(sessionForm.value.concertId), venueId:Number(sessionForm.value.venueId), startTime:localDateTime(sessionForm.value.startTime), endTime:localDateTime(sessionForm.value.endTime), categories:sessionForm.value.categories.map(item => ({ ...item, price:Number(item.price), rowCount:Number(item.rowCount), seatsPerRow:Number(item.seatsPerRow) })) }
    const created = await api.post('/admin/sessions', payload)
    message.value = `场次 ${created.id} 已创建并生成座位`; ui.notify(message.value)
    sessionForm.value = freshSessionForm(); await Promise.all([loadSessions(), loadBase()])
  } catch (exception) { error.value = exception.message; ui.notify(exception.message, 'error') }
  finally { submitting.value = false }
}
const toggleSession = async session => {
  const status = session.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  if (!window.confirm(`确认将该场次设为${status === 'ON_SALE' ? '售票中' : '停售'}？`)) return
  try {
    await api.put(`/admin/sessions/${session.id}`, { venueId:session.venueId, startTime:session.startTime, endTime:session.endTime, status, categories:(session.categories || []).map(item => ({ id:item.id, name:item.name, price:item.price, color:item.color })) })
    ui.notify('场次状态已更新'); await loadSessions()
  } catch (exception) { error.value = exception.message; ui.notify(exception.message, 'error') }
}
const changeUserStatus = async user => {
  const status = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const action = status === 'DISABLED' ? '禁用' : '启用'
  if (!window.confirm(`确认${action}用户“${user.nickname || user.username}”？${status === 'DISABLED' ? '该用户当前登录会话也会失效。' : ''}`)) return
  try {
    await api.patch(`/admin/users/${user.id}/status`, { status })
    ui.notify(`账号已${action}`); await loadUsers()
  } catch (exception) { error.value = exception.message; ui.notify(exception.message, 'error') }
}
const changePage = async (kind, delta) => {
  const filter = kind === 'orders' ? orderFilter : kind === 'payments' ? paymentFilter : userFilter
  const result = kind === 'orders' ? ordersResult : kind === 'payments' ? paymentsResult : usersResult
  const next = filter.value.page + delta
  if (next < 1 || (delta > 0 && next > Math.ceil(result.value.total / result.value.size))) return
  filter.value.page = next; await (kind === 'orders' ? loadOrders() : kind === 'payments' ? loadPayments() : loadUsers())
}
onMounted(loadActive)
</script>

<template>
  <main id="main-content" class="page admin-page" tabindex="-1"><div class="content-rail">
    <div class="admin-heading"><div><p class="eyebrow dark">CONTROL ROOM / 运营控制台</p><h1>场馆运营管理</h1><p>统一维护演出场次、订单状态和支付流水，所有数据来自真实微服务。</p></div><div class="admin-live"><i></i><span>服务已连接</span></div></div>
    <nav class="admin-nav" aria-label="管理功能"><button v-for="tab in tabs" :key="tab.value" :class="{ active:activeTab === tab.value }" type="button" @click="switchTab(tab.value)"><component :is="tab.icon" :size="19"/>{{ tab.label }}</button></nav>
    <dl class="admin-summary"><div><dt>全部演出</dt><dd>{{ shows.length }}</dd><small>包含草稿与已下架演出</small></div><div><dt>最近开演</dt><dd>{{ nextShow ? formatDateTime(nextShow.nextSessionTime) : '暂无场次' }}</dd><small>{{ nextShow?.venueName || '等待发布' }}</small></div><div><dt>管理权限</dt><dd>ADMIN</dd><small>由 Gateway 可信用户头确认</small></div></dl>
    <div v-if="error" class="notice error" role="alert"><span>{{ error }}</span><button class="button secondary" type="button" @click="loadActive"><RefreshCw :size="18"/>重试</button></div><div v-if="message" class="notice success" role="status">{{ message }}</div>
    <LoadingSkeleton v-if="loading" :rows="3" compact />

    <AdminDashboardPanel v-else-if="activeTab === 'dashboard'" />

    <div v-else-if="activeTab === 'shows'" class="admin-layout">
      <section class="admin-shows" aria-labelledby="admin-show-title"><div class="admin-section-title"><div><p class="eyebrow dark">EVENT CATALOG</p><h2 id="admin-show-title"><Radio :size="21"/>全部演出</h2></div><button class="icon-button light" type="button" aria-label="刷新演出列表" @click="loadBase"><RefreshCw :size="18"/></button></div>
        <div v-if="!shows.length" class="empty-state compact"><Ticket :size="34"/><h3>暂无演出</h3><p>从右侧表单创建第一场演出。</p></div>
        <div v-else class="admin-list"><div class="admin-list-head"><span>演出</span><span>最近场次</span><span>状态</span><span>操作</span></div><article v-for="show in shows" :key="show.id"><div class="admin-show-name"><b>{{ show.title }}</b><span>{{ show.artist }} · ID {{ show.id }}</span></div><div><b>{{ show.nextSessionTime ? formatDateTime(show.nextSessionTime) : '尚未配置' }}</b><span>{{ show.sessionCount || 0 }} 个场次</span></div><strong :class="`status ${String(show.status).toLowerCase()}`">{{ statusText(show.status) }}</strong><div class="table-actions"><button class="icon-button light" type="button" aria-label="编辑演出资料" title="编辑演出资料" @click="editShow(show)"><Pencil :size="17"/></button><button class="button ghost" type="button" @click="changeShowStatus(show)">{{ show.status === 'ON_SALE' ? '下架' : '上架' }}</button></div></article></div>
      </section>
      <form class="admin-form" @submit.prevent="createShow"><div><p class="eyebrow dark">{{ editingShowId ? 'EDIT EVENT' : 'NEW EVENT' }}</p><h2><Pencil v-if="editingShowId" :size="21"/><Plus v-else :size="21"/>{{ editingShowId ? '编辑演出资料' : '新增演出草稿' }}</h2><p>{{ editingShowId ? `正在编辑演出 ID ${editingShowId}` : '保存后前往“场次配置”生成票档与座位。' }}</p></div><fieldset><legend>基本信息</legend><label for="show-title">演出名称 <span class="required">必填</span><input id="show-title" v-model="showForm.title" required/></label><label for="show-artist">艺人 / 乐队 <span class="required">必填</span><input id="show-artist" v-model="showForm.artist" required/></label><label for="show-description">演出简介 <span class="required">必填</span><textarea id="show-description" v-model="showForm.description" required rows="4"></textarea></label></fieldset><fieldset><legend>销售与主视觉</legend><label for="show-cover">本地主视觉路径 <span class="required">必填</span><input id="show-cover" v-model="showForm.coverUrl" required @input="imageFailed = false"/><small>仅使用 `ticket-web/public` 下的本地资源。</small></label><div class="cover-preview" :class="{ 'image-fallback':imageFailed }"><img v-if="!imageFailed" :src="showForm.coverUrl" alt="演出主视觉预览" @error="imageFailed = true"/><span v-else><Image :size="24"/>当前路径无法预览。</span></div><div class="two-fields"><label for="sale-start">开售时间 <span class="required">必填</span><input id="sale-start" v-model="showForm.saleStartTime" required type="datetime-local"/></label><label for="sale-end">停售时间 <span class="required">必填</span><input id="sale-end" v-model="showForm.saleEndTime" required type="datetime-local"/></label></div></fieldset><div v-if="editingShowId" class="dialog-actions"><button class="button ghost" type="button" @click="resetShowForm">取消编辑</button><button class="button primary" :disabled="submitting">{{ submitting ? '正在保存…' : '保存修改' }}</button></div><button v-else class="button primary full" :disabled="submitting">{{ submitting ? '正在保存草稿…' : '保存为草稿' }}</button></form>
    </div>

    <div v-else-if="activeTab === 'sessions'" class="admin-data-layout">
      <section class="admin-table-panel"><div class="admin-section-title"><div><p class="eyebrow dark">SESSION INVENTORY</p><h2><CalendarRange :size="21"/>场次与票档</h2></div><button class="icon-button light" type="button" aria-label="刷新场次" @click="loadSessions"><RefreshCw :size="18"/></button></div><div class="table-scroll"><table class="control-table"><thead><tr><th>演出 / 时间</th><th>场馆</th><th>票档</th><th>座位</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="session in sessions" :key="session.id"><td><b>{{ session.concertTitle }}</b><small>ID {{ session.id }} · {{ formatDateTime(session.startTime) }}</small></td><td>{{ session.venueName }}<small>{{ session.city }}</small></td><td><span v-for="item in session.categories" :key="item.id" class="category-chip">{{ item.areaName }} ¥{{ money(item.price) }}</span></td><td class="mono">{{ session.availableCount || 0 }}/{{ session.seatCount || 0 }} 可选</td><td><span :class="`status ${String(session.status).toLowerCase()}`">{{ statusText(session.status) }}</span></td><td><button class="button ghost" type="button" @click="toggleSession(session)">{{ session.status === 'ON_SALE' ? '停止售票' : '恢复售票' }}</button></td></tr></tbody></table></div><div v-if="!sessions.length" class="empty-state compact"><CalendarRange :size="34"/><h3>暂无场次</h3><p>使用右侧表单创建场次并自动生成座位。</p></div></section>
      <form class="admin-form session-form" @submit.prevent="createSession"><div><p class="eyebrow dark">NEW SESSION</p><h2><Plus :size="21"/>新增场次</h2><p>票档保存后会一次性生成对应座位。</p></div><fieldset><legend>场次信息</legend><label>所属演出 <span class="required">必填</span><select v-model="sessionForm.concertId" required><option value="" disabled>选择演出</option><option v-for="show in shows" :key="show.id" :value="show.id">{{ show.title }}（{{ statusText(show.status) }}）</option></select></label><label>演出场馆 <span class="required">必填</span><select v-model="sessionForm.venueId" required><option value="" disabled>选择场馆</option><option v-for="venue in venues" :key="venue.id" :value="venue.id">{{ venue.city }} · {{ venue.name }}</option></select></label><div class="two-fields"><label>开场时间 <span class="required">必填</span><input v-model="sessionForm.startTime" required type="datetime-local"/></label><label>结束时间 <span class="required">必填</span><input v-model="sessionForm.endTime" required type="datetime-local"/></label></div></fieldset><fieldset><legend>票档与座位</legend><div v-for="(category, index) in sessionForm.categories" :key="index" class="category-builder"><div><b>票档 {{ index + 1 }}</b><button class="text-button danger" type="button" :disabled="sessionForm.categories.length === 1" @click="removeCategory(index)">移除</button></div><div class="two-fields"><label>票档名称<input v-model="category.name" required/></label><label>区域名称<input v-model="category.areaName" required/></label><label>票价<input v-model.number="category.price" min="0.01" step="0.01" required type="number"/></label><label>标识颜色<input v-model="category.color" required/></label><label>排数<input v-model.number="category.rowCount" min="1" max="20" required type="number"/></label><label>每排座位<input v-model.number="category.seatsPerRow" min="1" max="30" required type="number"/></label></div></div><button class="button secondary full" type="button" @click="addCategory"><Plus :size="18"/>增加票档</button></fieldset><button class="button primary full" :disabled="submitting">{{ submitting ? '正在生成场次与座位…' : '创建场次并生成座位' }}</button><p class="form-hint"><Users :size="16"/>单场最多生成 600 个座位，已售座位不会被票价修改覆盖。</p></form>
    </div>

    <AdminRefundsPanel v-else-if="activeTab === 'refunds'" />
    <AdminCheckInPanel v-else-if="activeTab === 'checkin'" />
    <AdminAuditPanel v-else-if="activeTab === 'audits'" />

    <section v-else-if="activeTab === 'orders'" class="admin-table-panel"><div class="admin-section-title"><div><p class="eyebrow dark">ORDER OPERATIONS</p><h2><ListChecks :size="21"/>全站订单</h2></div><span class="mono">{{ ordersResult.total }} 条记录</span></div><form class="admin-toolbar" @submit.prevent="orderFilter.page = 1; loadOrders()"><label>订单状态<select v-model="orderFilter.status"><option value="">全部状态</option><option value="PENDING_PAYMENT">待支付</option><option value="PAID">已支付</option><option value="CANCELLED">已取消</option><option value="CLOSED">已超时</option></select></label><label>搜索订单或用户<input v-model="orderFilter.keyword" placeholder="订单号 / 用户名"/></label><button class="button secondary" type="submit"><Search :size="18"/>查询</button></form><div class="table-scroll"><table class="control-table"><thead><tr><th>订单号</th><th>用户</th><th>演出 / 座位</th><th>金额</th><th>创建时间</th><th>状态</th></tr></thead><tbody><tr v-for="order in ordersResult.items" :key="order.orderNo"><td class="mono">{{ order.orderNo }}</td><td>{{ order.nickname || order.username }}<small>ID {{ order.userId }}</small></td><td>{{ order.concertTitle || '演出信息缺失' }}<small>{{ order.items?.map(item => item.seatCode).join(' · ') }}</small></td><td class="mono">¥{{ money(order.totalAmount) }}</td><td>{{ formatDateTime(order.createdAt) }}</td><td><span :class="`status ${String(order.status).toLowerCase()}`">{{ statusText(order.status) }}</span></td></tr></tbody></table></div><div class="table-pagination"><button class="button ghost" type="button" :disabled="ordersResult.page <= 1" @click="changePage('orders', -1)">上一页</button><span>第 {{ ordersResult.page }} / {{ Math.max(1, Math.ceil(ordersResult.total / ordersResult.size)) }} 页</span><button class="button ghost" type="button" :disabled="ordersResult.page >= Math.ceil(ordersResult.total / ordersResult.size)" @click="changePage('orders', 1)">下一页</button></div></section>

    <section v-else-if="activeTab === 'payments'" class="admin-table-panel"><div class="admin-section-title"><div><p class="eyebrow dark">PAYMENT LEDGER</p><h2><CreditCard :size="21"/>支付流水</h2></div><span class="mono">{{ paymentsResult.total }} 条记录</span></div><form class="admin-toolbar" @submit.prevent="paymentFilter.page = 1; loadPayments()"><label>支付状态<select v-model="paymentFilter.status"><option value="">全部状态</option><option value="PROCESSING">处理中</option><option value="SUCCESS">成功</option><option value="REFUNDED">已退款</option><option value="FAILED">失败</option></select></label><label>支付渠道<select v-model="paymentFilter.channel"><option value="">全部渠道</option><option value="ALIPAY">支付宝沙箱</option><option value="WECHAT">微信模拟支付</option></select></label><label>搜索流水<input v-model="paymentFilter.keyword" placeholder="流水号 / 订单号 / 用户名"/></label><button class="button secondary" type="submit"><Search :size="18"/>查询</button></form><div class="table-scroll"><table class="control-table"><thead><tr><th>支付流水</th><th>关联订单</th><th>用户</th><th>渠道</th><th>金额</th><th>状态</th><th>支付时间</th></tr></thead><tbody><tr v-for="payment in paymentsResult.items" :key="payment.paymentNo"><td class="mono">{{ payment.paymentNo }}</td><td class="mono">{{ payment.orderNo }}<small>订单 {{ statusText(payment.orderStatus) }}</small></td><td>{{ payment.nickname || payment.username }}</td><td>{{ channelText(payment.channel) }}</td><td class="mono">¥{{ money(payment.amount) }}</td><td><span :class="`status ${String(payment.status).toLowerCase()}`">{{ statusText(payment.status) }}</span></td><td>{{ payment.paidAt ? formatDateTime(payment.paidAt) : '尚未完成' }}</td></tr></tbody></table></div><div class="table-pagination"><button class="button ghost" type="button" :disabled="paymentsResult.page <= 1" @click="changePage('payments', -1)">上一页</button><span>第 {{ paymentsResult.page }} / {{ Math.max(1, Math.ceil(paymentsResult.total / paymentsResult.size)) }} 页</span><button class="button ghost" type="button" :disabled="paymentsResult.page >= Math.ceil(paymentsResult.total / paymentsResult.size)" @click="changePage('payments', 1)">下一页</button></div></section>

    <section v-else class="admin-table-panel"><div class="admin-section-title"><div><p class="eyebrow dark">ACCOUNT DIRECTORY</p><h2><Users :size="21"/>用户账号</h2><p class="section-note">查看注册用户并管理普通账号状态；管理员账号在此页面受保护。</p></div><span class="mono">{{ usersResult.total }} 个账号</span></div><form class="admin-toolbar" @submit.prevent="userFilter.page = 1; loadUsers()"><label>账号角色<select v-model="userFilter.role"><option value="">全部角色</option><option value="USER">普通用户</option><option value="ADMIN">管理员</option></select></label><label>账号状态<select v-model="userFilter.status"><option value="">全部状态</option><option value="ACTIVE">正常</option><option value="DISABLED">已禁用</option></select></label><label>搜索用户<input v-model="userFilter.keyword" placeholder="用户名 / 昵称 / 手机号"/></label><button class="button secondary" type="submit"><Search :size="18"/>查询</button></form><div class="table-scroll"><table class="control-table user-table"><thead><tr><th>用户</th><th>联系方式</th><th>角色</th><th>状态</th><th>注册时间</th><th>操作</th></tr></thead><tbody><tr v-for="user in usersResult.items" :key="user.id"><td><b>{{ user.nickname || user.username }}</b><small>@{{ user.username }} · ID {{ user.id }}</small></td><td class="mono">{{ user.phone }}</td><td>{{ roleText(user.role) }}</td><td><span :class="`status ${String(user.status).toLowerCase()}`">{{ statusText(user.status) }}</span></td><td>{{ formatDateTime(user.createdAt) }}</td><td><div class="table-actions"><button class="button ghost" type="button" @click="selectedUserId = user.id">查看详情</button><button v-if="user.role === 'USER'" class="button ghost" :class="{ danger:user.status === 'ACTIVE' }" type="button" @click="changeUserStatus(user)">{{ user.status === 'ACTIVE' ? '禁用' : '恢复' }}</button><span v-else class="protected-account">受保护</span></div></td></tr></tbody></table></div><div v-if="!usersResult.items.length" class="empty-state compact"><Users :size="34"/><h3>没有符合条件的用户</h3><p>调整角色、状态或关键词后再试。</p></div><div class="table-pagination"><button class="button ghost" type="button" :disabled="usersResult.page <= 1" @click="changePage('users', -1)">上一页</button><span>第 {{ usersResult.page }} / {{ Math.max(1, Math.ceil(usersResult.total / usersResult.size)) }} 页</span><button class="button ghost" type="button" :disabled="usersResult.page >= Math.ceil(usersResult.total / usersResult.size)" @click="changePage('users', 1)">下一页</button></div></section>
    <AdminUserDetail v-if="selectedUserId" :user-id="selectedUserId" @close="selectedUserId = null"/>
  </div></main>
</template>
