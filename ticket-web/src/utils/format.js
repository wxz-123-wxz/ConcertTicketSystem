export const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })

export const formatDate = value => value
  ? new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' }).format(new Date(value))
  : '时间待定'

export const formatDateTime = value => value
  ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
  : '时间待定'

export const orderLabels = {
  PENDING_PAYMENT: '待支付',
  PAID: '已支付',
  REFUND_PENDING: '退款审核中',
  REFUNDED: '已退款',
  CANCELLED: '已取消',
  CLOSED: '已超时'
}
