<script setup lang="ts">
import { AlertTriangle, ChevronDown, ChevronUp, ExternalLink, Globe, User, Building2, ImageIcon } from 'lucide-vue-next'
import type { InternetDetectionResult } from '@/composables/useDetection'
import { Skeleton } from '@/components/ui/skeleton'

const props = defineProps<{
  result: InternetDetectionResult
}>()

// 원본 이미지 로드
const originalImageUrl = ref<string | null>(null)
const isLoadingOriginal = ref(true)

onMounted(async () => {
  try {
    const imageData = await $fetch<{ storagePath: string | null }>(`/api/images/${props.result.sourceImageId}`)
    if (imageData.storagePath) {
      originalImageUrl.value = `/api/images/file/${imageData.storagePath}`
    }
  } catch {
    // placeholder
  } finally {
    isLoadingOriginal.value = false
  }
})

// 상세 보기 토글
const expanded = ref(false)

// 심각도 뱃지
const severity = computed(() => {
  const s = props.result.sscdSimilarity ?? 0
  const d = props.result.dinoSimilarity ?? 0
  if (s >= 0.7 || d >= 0.9) return { label: '도용 확실', class: 'bg-red-100 text-red-800 border-red-200 dark:bg-red-900/30 dark:text-red-400 dark:border-red-800', level: 'high' }
  if (s >= 0.5 || d >= 0.7) return { label: '도용 의심', class: 'bg-orange-100 text-orange-800 border-orange-200 dark:bg-orange-900/30 dark:text-orange-400 dark:border-orange-800', level: 'medium' }
  if (s >= 0.3 || d >= 0.5) return { label: '유사', class: 'bg-yellow-100 text-yellow-800 border-yellow-200 dark:bg-yellow-900/30 dark:text-yellow-400 dark:border-yellow-800', level: 'low' }
  return { label: '낮은 유사도', class: 'bg-gray-100 text-gray-600 border-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:border-gray-700', level: 'none' }
})

// 유사도 퍼센트 (큰 값)
const similarityPercent = computed(() => {
  const s = (props.result.sscdSimilarity ?? 0) * 100
  const d = (props.result.dinoSimilarity ?? 0) * 100
  return Math.max(s, d).toFixed(0)
})

// 검색엔진 한글
const searchEngineLabel = computed(() => {
  switch (props.result.searchEngine) {
    case 'NAVER': return '네이버'
    case 'GOOGLE': return '구글'
    default: return props.result.searchEngine
  }
})

// 플랫폼 카테고리 라벨
const platformLabel = computed(() => {
  const pt = props.result.platformType
  if (!pt) return null
  if (pt.startsWith('SNS_')) return '개인 계정'
  if (pt.startsWith('OVERSEAS_')) return '해외 판매자'
  if (pt.startsWith('MARKET_') || pt.startsWith('NAVER_')) return '오픈마켓'
  if (pt.startsWith('VERTICAL_')) return '전문몰'
  if (pt.startsWith('SOCIAL_')) return '소셜 쇼핑'
  return null
})

// 도메인 추출
function domainFromUrl(url: string | null) {
  if (!url) return ''
  try { return new URL(url).hostname.replace(/^www\./, '') } catch { return url }
}

// 이미지 로드 에러 핸들링
function handleImageError(e: Event) {
  (e.target as HTMLImageElement).style.display = 'none'
}
</script>

<template>
  <div class="rounded-xl border border-border bg-card shadow-sm transition-shadow hover:shadow-md">
    <!-- 헤더: 심각도 뱃지 + 검색엔진 -->
    <div class="flex items-center justify-between border-b border-border/50 px-4 py-3">
      <span :class="['inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-semibold', severity.class]">
        <AlertTriangle class="h-3.5 w-3.5" />
        {{ severity.label }}
      </span>
      <div class="flex items-center gap-2">
        <span v-if="platformLabel" class="rounded-md bg-muted px-2 py-0.5 text-xs text-muted-foreground">{{ platformLabel }}</span>
        <span class="rounded-md bg-muted px-2 py-0.5 text-xs text-muted-foreground">{{ searchEngineLabel }}</span>
      </div>
    </div>

    <!-- 이미지 비교 -->
    <div class="p-4">
      <div class="grid grid-cols-2 gap-4">
        <!-- 원본 이미지 -->
        <div class="text-center">
          <div :class="['relative mx-auto overflow-hidden rounded-lg border-2 border-green-300 bg-muted', expanded ? 'h-64 w-64' : 'h-36 w-36']">
            <Skeleton v-if="isLoadingOriginal" class="h-full w-full" />
            <img
              v-else-if="originalImageUrl"
              :src="originalImageUrl"
              alt="원본 이미지"
              class="h-full w-full object-cover"
              loading="lazy"
            />
            <div v-else class="flex h-full w-full items-center justify-center">
              <ImageIcon class="h-8 w-8 text-muted-foreground/50" />
            </div>
          </div>
          <p class="mt-2 text-xs font-medium text-green-700 dark:text-green-400">내 이미지</p>
        </div>

        <!-- 발견된 이미지 -->
        <div class="text-center">
          <div :class="['relative mx-auto overflow-hidden rounded-lg border-2 border-red-300 bg-muted', expanded ? 'h-64 w-64' : 'h-36 w-36']">
            <img
              :src="result.foundImageUrl"
              :alt="result.sourcePageTitle || '발견된 이미지'"
              class="h-full w-full object-cover"
              loading="lazy"
              @error="handleImageError"
            />
            <div class="absolute inset-0 flex items-center justify-center" style="display: none">
              <ImageIcon class="h-8 w-8 text-muted-foreground/50" />
            </div>
          </div>
          <p class="mt-2 text-xs font-medium text-red-700 dark:text-red-400">발견된 이미지</p>
        </div>
      </div>

      <!-- 유사도 바 -->
      <div class="mt-4">
        <div class="flex items-center justify-between text-sm">
          <span class="text-muted-foreground">유사도</span>
          <span class="font-semibold">{{ similarityPercent }}%</span>
        </div>
        <div class="mt-1 h-2 w-full overflow-hidden rounded-full bg-muted">
          <div
            class="h-full rounded-full transition-all"
            :class="{
              'bg-red-500': severity.level === 'high',
              'bg-orange-500': severity.level === 'medium',
              'bg-yellow-500': severity.level === 'low',
              'bg-gray-400': severity.level === 'none',
            }"
            :style="{ width: similarityPercent + '%' }"
          />
        </div>
      </div>

      <!-- 침해자 정보 요약 -->
      <div class="mt-4 flex items-start gap-2">
        <Globe class="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
        <div class="min-w-0 flex-1">
          <p class="text-sm font-semibold">{{ result.sellerName || domainFromUrl(result.sourcePageUrl) || '출처 불명' }}</p>
          <p v-if="result.sourcePageTitle" class="truncate text-xs text-muted-foreground">{{ result.sourcePageTitle }}</p>
        </div>
      </div>

      <!-- 상세 보기 버튼 -->
      <button
        class="mt-3 flex w-full items-center justify-center gap-1 rounded-lg py-2 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        @click="expanded = !expanded"
      >
        {{ expanded ? '접기' : '상세 보기' }}
        <ChevronUp v-if="expanded" class="h-3.5 w-3.5" />
        <ChevronDown v-else class="h-3.5 w-3.5" />
      </button>
    </div>

    <!-- 펼침: 상세 정보 -->
    <div v-if="expanded" class="border-t border-border/50 px-4 pb-4 pt-3">
      <!-- 침해자 사업자 정보 -->
      <div v-if="result.businessRegNumber || result.representativeName || result.sellerName" class="mb-4 rounded-lg bg-muted/50 p-3">
        <div class="mb-2 flex items-center gap-1.5 text-xs font-semibold text-muted-foreground">
          <Building2 class="h-3.5 w-3.5" />
          침해자 정보
        </div>
        <div class="grid gap-1.5 text-sm">
          <div v-if="result.sellerName" class="flex gap-2">
            <span class="w-20 shrink-0 text-xs text-muted-foreground">상호</span>
            <span class="font-medium">{{ result.sellerName }}</span>
          </div>
          <div v-if="result.representativeName" class="flex gap-2">
            <span class="w-20 shrink-0 text-xs text-muted-foreground">대표자</span>
            <span>{{ result.representativeName }}</span>
          </div>
          <div v-if="result.businessRegNumber" class="flex gap-2">
            <span class="w-20 shrink-0 text-xs text-muted-foreground">사업자번호</span>
            <span class="font-mono text-xs">{{ result.businessRegNumber }}</span>
          </div>
          <div v-if="result.businessAddress" class="flex gap-2">
            <span class="w-20 shrink-0 text-xs text-muted-foreground">주소</span>
            <span class="text-xs">{{ result.businessAddress }}</span>
          </div>
          <div v-if="result.contactPhone" class="flex gap-2">
            <span class="w-20 shrink-0 text-xs text-muted-foreground">연락처</span>
            <span>{{ result.contactPhone }}</span>
          </div>
          <div v-if="result.contactEmail" class="flex gap-2">
            <span class="w-20 shrink-0 text-xs text-muted-foreground">이메일</span>
            <span>{{ result.contactEmail }}</span>
          </div>
        </div>
      </div>

      <!-- SNS 개인 계정 -->
      <div v-else-if="result.platformType?.startsWith('SNS_')" class="mb-4 rounded-lg bg-muted/50 p-3">
        <div class="mb-2 flex items-center gap-1.5 text-xs font-semibold text-muted-foreground">
          <User class="h-3.5 w-3.5" />
          개인 계정
        </div>
        <p class="text-sm font-medium">{{ result.sellerName || '계정 정보 없음' }}</p>
      </div>

      <!-- 판매자 정보 없음 -->
      <div v-else class="mb-4 rounded-lg bg-muted/50 p-3">
        <p class="text-xs text-muted-foreground">판매자 정보를 확인할 수 없습니다</p>
      </div>

      <!-- 출처 URL -->
      <div class="space-y-2 text-sm">
        <div v-if="result.sourcePageUrl" class="flex items-start gap-2">
          <span class="w-16 shrink-0 text-xs text-muted-foreground">출처 URL</span>
          <a :href="result.sourcePageUrl" target="_blank" rel="noopener noreferrer"
             class="min-w-0 flex-1 break-all text-xs text-primary hover:underline">
            {{ result.sourcePageUrl }}
            <ExternalLink class="ml-1 inline h-3 w-3" />
          </a>
        </div>
        <div class="flex gap-2">
          <span class="w-16 shrink-0 text-xs text-muted-foreground">탐지 일시</span>
          <span class="text-xs">{{ new Date(result.createdAt).toLocaleString('ko-KR') }}</span>
        </div>
        <div class="flex gap-2">
          <span class="w-16 shrink-0 text-xs text-muted-foreground">검색 엔진</span>
          <span class="text-xs">{{ searchEngineLabel }}</span>
        </div>
      </div>

      <!-- 유사도 상세 -->
      <details class="mt-3">
        <summary class="cursor-pointer text-xs text-muted-foreground hover:text-foreground">유사도 상세</summary>
        <div class="mt-2 grid grid-cols-2 gap-3 text-xs">
          <div>
            <span class="text-muted-foreground">SSCD</span>
            <span class="ml-2 font-mono">{{ result.sscdSimilarity != null ? (result.sscdSimilarity * 100).toFixed(1) + '%' : '-' }}</span>
          </div>
          <div>
            <span class="text-muted-foreground">DINOv2</span>
            <span class="ml-2 font-mono">{{ result.dinoSimilarity != null ? (result.dinoSimilarity * 100).toFixed(1) + '%' : '-' }}</span>
          </div>
        </div>
      </details>
    </div>
  </div>
</template>
