import { defineStore } from 'pinia'

let noticeId = 0

export const useUiStore = defineStore('ui', {
  state: () => ({ notices: [] }),
  actions: {
    notify(message, type = 'success') {
      const id = ++noticeId
      this.notices.push({ id, message, type })
      window.setTimeout(() => this.dismiss(id), 4000)
      return id
    },
    dismiss(id) {
      this.notices = this.notices.filter(notice => notice.id !== id)
    }
  }
})
