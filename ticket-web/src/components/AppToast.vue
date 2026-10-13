<script setup>
import { CheckCircle2, CircleAlert, Info, X } from '@lucide/vue'
import { useUiStore } from '../stores/ui'

const ui = useUiStore()
const icons = { success: CheckCircle2, error: CircleAlert, info: Info }
</script>

<template>
  <div class="toast-stack" aria-live="polite" aria-atomic="true">
    <div v-for="notice in ui.notices" :key="notice.id" class="toast" :class="`toast-${notice.type}`" role="status">
      <component :is="icons[notice.type] || Info" :size="20" aria-hidden="true" />
      <span>{{ notice.message }}</span>
      <button type="button" :aria-label="`关闭提示：${notice.message}`" @click="ui.dismiss(notice.id)"><X :size="18" /></button>
    </div>
  </div>
</template>
