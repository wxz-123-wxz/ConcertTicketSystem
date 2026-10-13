import { defineStore } from 'pinia'
import api from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('liveseat-token') || '',
    user: JSON.parse(localStorage.getItem('liveseat-user') || 'null')
  }),
  getters: {
    loggedIn: state => Boolean(state.token),
    isAdmin: state => state.user?.role === 'ADMIN'
  },
  actions: {
    async login(form) {
      const result = await api.post('/auth/login', form)
      this.persist(result)
    },
    async register(form) {
      const result = await api.post('/auth/register', form)
      this.persist(result)
    },
    async logout() {
      try { await api.post('/auth/logout') } finally {
        this.token = ''; this.user = null
        localStorage.removeItem('liveseat-token'); localStorage.removeItem('liveseat-user')
      }
    },
    updateUser(user) {
      this.user = user
      localStorage.setItem('liveseat-user', JSON.stringify(user))
    },
    clear() {
      this.token = ''; this.user = null
      localStorage.removeItem('liveseat-token'); localStorage.removeItem('liveseat-user')
    },
    persist(result) {
      this.token = result.token; this.user = result.user
      localStorage.setItem('liveseat-token', result.token)
      localStorage.setItem('liveseat-user', JSON.stringify(result.user))
    }
  }
})
