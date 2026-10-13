<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, Armchair, CalendarClock, CalendarDays, MapPin, SearchX, TicketCheck, Timer } from '@lucide/vue'
import api from '../api'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import { formatDate, money } from '../utils/format'

const shows = ref([])
const loading = ref(true)
const error = ref('')
const city = ref('全部城市')
const dateRange = ref('全部日期')
const priceRange = ref('全部票价')

const imageFor = show => show?.coverUrl || (show?.id === 2 ? '/assets/concert-night.webp' : '/assets/concert-main.webp')
const hideBrokenImage = event => { event.currentTarget.hidden = true }
const cities = computed(() => ['全部城市', ...new Set(shows.value.map(show => show.city).filter(Boolean))])
const featured = computed(() => shows.value.find(show => show.id === 1) || shows.value[0])
const nextShow = computed(() => [...shows.value].filter(show => show.nextSessionTime).sort((a, b) => new Date(a.nextSessionTime) - new Date(b.nextSessionTime))[0])
const filteredShows = computed(() => shows.value.filter(show => {
  const price = Number(show.minPrice || 0)
  const days = show.nextSessionTime ? (new Date(show.nextSessionTime) - Date.now()) / 86400000 : Infinity
  const cityMatch = city.value === '全部城市' || show.city === city.value
  const dateMatch = dateRange.value === '全部日期' || (dateRange.value === '30天内' ? days <= 30 : days > 30)
  const priceMatch = priceRange.value === '全部票价'
    || (priceRange.value === '600元以下' && price < 600)
    || (priceRange.value === '600–1000元' && price >= 600 && price <= 1000)
    || (priceRange.value === '1000元以上' && price > 1000)
  return cityMatch && dateMatch && priceMatch
}))

const clearFilters = () => { city.value = '全部城市'; dateRange.value = '全部日期'; priceRange.value = '全部票价' }
const load = async () => {
  loading.value = true
  error.value = ''
  try { shows.value = await api.get('/shows') }
  catch (exception) { error.value = exception.message }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <main id="main-content" tabindex="-1">
    <section class="hero">
      <img :src="imageFor(featured)" width="1280" height="720" alt="室内演唱会场馆与环形舞台现场" @error="hideBrokenImage" />
      <div class="hero-shade"></div>
      <div class="content-rail hero-copy">
        <p class="eyebrow">LIVE / 2026 TOUR</p>
        <span class="live-label"><i></i> 正在售票</span>
        <h1>{{ featured?.title || '在座位亮起之前，先抵达现场' }}</h1>
        <p>{{ featured?.artist || 'LiveSeat 演唱会选座购票终端' }}</p>
        <RouterLink v-if="featured" class="button primary" :to="`/shows/${featured.id}`">查看演出与场次 <ArrowRight :size="19" /></RouterLink>
      </div>
    </section>

    <section class="operation-strip" aria-label="售票运行信息">
      <div class="content-rail operation-grid">
        <div><TicketCheck :size="20" /><span><small>正在售票</small><b>{{ shows.length }} 场演出</b></span></div>
        <div><CalendarClock :size="20" /><span><small>最近开演</small><b>{{ nextShow ? `${formatDate(nextShow.nextSessionTime)} · ${nextShow.city}` : '等待场次发布' }}</b></span></div>
        <div><Timer :size="20" /><span><small>锁座规则</small><b>提交后保留 10 分钟</b></span></div>
      </div>
    </section>

    <section class="content-rail show-section" aria-labelledby="on-sale-title">
      <div class="section-heading">
        <div><p class="eyebrow dark">NOW BOOKING</p><h2 id="on-sale-title">正在售票</h2></div>
        <p>所有场次、票价与座位状态均来自实时服务。</p>
      </div>

      <form class="show-filters" aria-label="筛选演出" @submit.prevent>
        <label>城市<select v-model="city"><option v-for="option in cities" :key="option">{{ option }}</option></select></label>
        <label>日期<select v-model="dateRange"><option>全部日期</option><option>30天内</option><option>30天以后</option></select></label>
        <label>最低票价<select v-model="priceRange"><option>全部票价</option><option>600元以下</option><option>600–1000元</option><option>1000元以上</option></select></label>
        <button v-if="city !== '全部城市' || dateRange !== '全部日期' || priceRange !== '全部票价'" class="text-button" type="button" @click="clearFilters">清除筛选</button>
      </form>

      <LoadingSkeleton v-if="loading" :rows="2" />
      <div v-else-if="error" class="notice error" role="alert"><span>{{ error }}</span><button class="button secondary" type="button" @click="load">重新连接</button></div>
      <div v-else-if="!filteredShows.length" class="empty-state"><SearchX :size="36" /><h3>没有符合条件的演出</h3><p>调整城市、日期或票价范围后再试。</p><button class="button secondary" type="button" @click="clearFilters">查看全部演出</button></div>
      <div v-else class="editorial-list">
        <article v-for="(show, index) in filteredShows" :key="show.id" class="show-row">
          <div class="show-index mono">{{ String(index + 1).padStart(2, '0') }}</div>
          <div class="show-cover"><img :src="imageFor(show)" :alt="`${show.title} 演出主视觉`" width="1280" height="720" loading="lazy" @error="hideBrokenImage" /></div>
          <div class="show-info">
            <p class="artist">{{ show.artist }}</p><h3>{{ show.title }}</h3>
            <div class="meta-line"><span><CalendarDays :size="18" />{{ formatDate(show.nextSessionTime) }}</span><span><MapPin :size="18" />{{ show.city || '城市待定' }} · {{ show.venueName || '场馆待定' }}</span></div>
          </div>
          <div class="show-action"><span class="price"><small>最低票价</small>¥{{ money(show.minPrice) }}</span><RouterLink class="button secondary" :to="`/shows/${show.id}`">选择场次</RouterLink></div>
        </article>
      </div>
    </section>

    <section class="purchase-guide" aria-labelledby="guide-title">
      <div class="content-rail">
        <div class="section-heading inverse"><div><p class="eyebrow">BOOKING FLOW</p><h2 id="guide-title">三步抵达现场</h2></div><p>从场次到座位，价格与状态始终由服务端确认。</p></div>
        <ol class="guide-steps">
          <li><span class="mono">01</span><CalendarDays :size="24" /><div><h3>选择场次</h3><p>确认日期、城市、场馆和票档。</p></div></li>
          <li><span class="mono">02</span><Armchair :size="24" /><div><h3>实时选座</h3><p>绿色座位可选，单次最多选择 6 个。</p></div></li>
          <li><span class="mono">03</span><Timer :size="24" /><div><h3>限时支付</h3><p>锁座后 10 分钟内完成模拟支付。</p></div></li>
        </ol>
      </div>
    </section>
  </main>
</template>
