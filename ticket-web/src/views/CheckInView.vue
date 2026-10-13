<script setup>
import { useRoute } from 'vue-router'
import AdminCheckInPanel from '../components/AdminCheckInPanel.vue'
const route=useRoute()
</script>
<template><main id="main-content" class="page" tabindex="-1"><div class="content-rail narrow"><AdminCheckInPanel :initial-token="String(route.params.ticketCode)"/></div></main></template>
