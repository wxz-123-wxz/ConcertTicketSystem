import { createRouter, createWebHistory } from 'vue-router'
import HomeView from './views/HomeView.vue'

const ShowView = () => import('./views/ShowView.vue')
const SeatView = () => import('./views/SeatView.vue')
const OrdersView = () => import('./views/OrdersView.vue')
const PaymentView = () => import('./views/PaymentView.vue')
const WechatMockPayView = () => import('./views/WechatMockPayView.vue')
const AuthView = () => import('./views/AuthView.vue')
const AdminView = () => import('./views/AdminView.vue')
const AccountView = () => import('./views/AccountView.vue')
const CheckInView = () => import('./views/CheckInView.vue')

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'home', component: HomeView, meta: { title: '演出' } },
    { path: '/shows/:id', name: 'show', component: ShowView, meta: { title: '演出详情' } },
    { path: '/seats/:sessionId', name: 'seats', component: SeatView, meta: { title: '选择座位' } },
    { path: '/orders', name: 'orders', component: OrdersView, meta: { auth: true, title: '我的订单' } },
    { path: '/pay/:orderNo', name: 'payment', component: PaymentView, meta: { auth: true, title: '确认支付' } },
    { path: '/wechat-pay/:token', name: 'wechat-mock-pay', component: WechatMockPayView, meta: { title: '微信模拟支付', paymentOnly: true } },
    { path: '/auth', name: 'auth', component: AuthView, meta: { title: '登录注册' } },
    { path: '/account', name: 'account', component: AccountView, meta: { auth: true, title: '我的账户' } },
    { path: '/admin', name: 'admin', component: AdminView, meta: { auth: true, admin: true, title: '运营管理' } },
    { path: '/check-in/:ticketCode', name: 'check-in', component: CheckInView, meta: { auth: true, admin: true, title: '电子票核销' } }
  ]
})

router.beforeEach(to => {
  const token = localStorage.getItem('liveseat-token')
  const user = JSON.parse(localStorage.getItem('liveseat-user') || 'null')
  if (to.meta.auth && !token) return { path: '/auth', query: { redirect: to.fullPath } }
  if (to.meta.admin && user?.role !== 'ADMIN') return '/'
})

router.afterEach(to => {
  document.title = `${to.meta.title || '演唱会选座'} · LiveSeat`
  requestAnimationFrame(() => document.querySelector('main')?.focus())
})

export default router
