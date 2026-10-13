<script setup>
defineProps({ rows: { type: Number, default: 3 }, compact: Boolean })
</script>

<template>
  <div class="skeleton-list" :class="{ compact }" aria-label="内容加载中" aria-busy="true">
    <div v-for="row in rows" :key="row" class="skeleton-row">
      <span class="skeleton-media"></span>
      <span class="skeleton-copy"><i></i><i></i><i></i></span>
    </div>
  </div>
</template>
