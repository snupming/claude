<script setup lang="ts">
import { Search, Globe, ScanLine, AlertTriangle, CheckCircle2, Loader2, Clock, ChevronRight, ExternalLink } from 'lucide-vue-next'
import { Skeleton } from '@/components/ui/skeleton'
import { Progress } from '@/components/ui/progress'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useDetection } from '@/composables/useDetection'
import type { ScanInfo, ScanDetail } from '@/composables/useDetection'

definePageMeta({
  layout: 'dashboard',
})

const {
  activeScan,
  isScanning,
  startInternetScan,
  fetchScans,
  fetchScanDetail,
  resumeIfActive,
  clearActiveScan,
  stopPolling,
} = useDetection()

const isLoading = ref(true)
const isStarting = ref(false)
const scanHistory = ref<ScanInfo[]>([])
const selectedDetail = ref<ScanDetail | null>(null)
const showDetail = ref(false)

async function loadData() {
  isLoading.value = true
  try {
    await resumeIfActive()
    const page = await fetchScans()
    scanHistory.value = page.content
  } catch {
    // ignore
  } finally {
    isLoading.value = false
  }
}

async function handleStartScan() {
  isStarting.value = true
  try {
    await startInternetScan()
  } catch {
    // ignore
  } finally {
    isStarting.value = false
  }
}

async function viewDetail(scan: ScanInfo) {
  try {
    selectedDetail.value = await fetchScanDetail(scan.id)
    showDetail.value = true
  } catch {
    // ignore
  }
}

watch(activeScan, (scan) => {
  if (scan && (scan.status === 'COMPLETED' || scan.status === 'FAILED')) {
    fetchScans().then(page => {
      scanHistory.value = page.content
    })
  }
})

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function statusInfo(status: string) {
  switch (status) {
    case 'COMPLETED': return { label: '완료', class: 'bg-green-500/10 text-green-600' }
    case 'FAILED': return { label: '실패', class: 'bg-destructive/10 text-destructive' }
    case 'SCANNING': return { label: '스캔 중', class: 'bg-primary/10 text-primary' }
    case 'PENDING': return { label: '대기', class: 'bg-muted text-muted-foreground' }
    default: return { label: status, class: 'bg-muted text-muted-foreground' }
  }
}

function scanTypeLabel(scanType: string) {
  return scanType === 'INTERNET' ? '인터넷 탐지' : 'DB 탐지'
}

function similarityPercent(value: number | null) {
  if (value == null) return '-'
  return (value * 100).toFixed(1) + '%'
}

function domainFromUrl(url: string | null) {
  if (!url) return ''
  try {
    return new URL(url).hostname
  } catch {
    return url
  }
}

onMounted(loadData)
onUnmounted(stopPolling)
</script>

<template>
  <div class="mx-auto max-w-4xl px-6 py-8">
    <!-- Header -->
    <template v-if="isLoading">
      <Skeleton class="h-7 w-28" />
      <Skeleton class="mt-2 h-4 w-72" />
    </template>
    <template v-else>
      <h1 class="text-2xl font-bold tracking-tight">도용 탐지</h1>
      <p class="mt-1 text-sm text-muted-foreground">
        인덱싱된 이미지를 인터넷에서 검색하여 도용 여부를 탐지합니다
      </p>
    </template>

    <!-- Active Scan Progress -->
    <Card v-if="isScanning && activeScan" class="mt-8">
      <CardContent class="py-8">
        <div class="flex flex-col items-center text-center">
          <Loader2 class="h-10 w-10 animate-spin text-primary" />
          <h3 class="mt-4 text-lg font-semibold">인터넷 스캔 진행 중...</h3>
          <p class="mt-1 text-sm text-muted-foreground">
            {{ activeScan.scannedImages }} / {{ activeScan.totalImages }} 이미지 검사 완료
          </p>
          <div class="mt-4 w-full max-w-sm">
            <Progress :model-value="activeScan.progress" class="h-2" />
          </div>
          <p class="mt-2 text-xs text-muted-foreground">{{ activeScan.progress }}%</p>
        </div>
      </CardContent>
    </Card>

    <!-- Completed Scan Result Banner -->
    <Card v-else-if="activeScan?.status === 'COMPLETED'" class="mt-8 border-green-500/30">
      <CardContent class="py-6">
        <div class="flex items-center gap-4">
          <div class="flex h-12 w-12 items-center justify-center rounded-2xl bg-green-500/10">
            <CheckCircle2 class="h-6 w-6 text-green-500" />
          </div>
          <div class="flex-1">
            <h3 class="font-semibold">스캔 완료</h3>
            <p class="text-sm text-muted-foreground">
              {{ activeScan.totalImages }}개 이미지 검사,
              <span :class="activeScan.matchesFound > 0 ? 'text-destructive font-medium' : ''">
                {{ activeScan.matchesFound }}건 도용 의심
              </span>
            </p>
          </div>
          <div class="flex gap-2">
            <Button variant="outline" size="sm" @click="viewDetail(activeScan!)">
              상세 보기
            </Button>
            <Button variant="ghost" size="sm" @click="clearActiveScan">
              닫기
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Failed Scan Banner -->
    <Card v-else-if="activeScan?.status === 'FAILED'" class="mt-8 border-destructive/30">
      <CardContent class="py-6">
        <div class="flex items-center gap-4">
          <div class="flex h-12 w-12 items-center justify-center rounded-2xl bg-destructive/10">
            <AlertTriangle class="h-6 w-6 text-destructive" />
          </div>
          <div class="flex-1">
            <h3 class="font-semibold">스캔 실패</h3>
            <p class="text-sm text-muted-foreground">문제가 발생했습니다. 다시 시도해주세요.</p>
          </div>
          <Button variant="ghost" size="sm" @click="clearActiveScan">
            닫기
          </Button>
        </div>
      </CardContent>
    </Card>

    <!-- Loading Skeleton (새로고침 시 상태 복원 전) -->
    <Card v-if="isLoading && !activeScan" class="mt-8">
      <CardContent class="flex flex-col items-center py-16">
        <Skeleton class="h-14 w-14 rounded-2xl" />
        <Skeleton class="mt-4 h-5 w-48" />
        <Skeleton class="mt-2 h-4 w-72" />
        <Skeleton class="mt-6 h-10 w-28 rounded-lg" />
      </CardContent>
    </Card>

    <!-- Start Scan -->
    <Card v-if="!isLoading && !isScanning && !activeScan" class="mt-8">
      <CardContent class="flex flex-col items-center py-16">
        <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10">
          <Globe class="h-7 w-7 text-primary" />
        </div>
        <h3 class="mt-4 text-lg font-semibold">인터넷 도용 탐지</h3>
        <p class="mt-1.5 max-w-md text-center text-sm text-muted-foreground">
          보호된 이미지의 키워드로 네이버를 검색하고, 발견된 이미지와 SSCD/DINOv2로 유사도를 비교합니다
        </p>
        <Button class="mt-6 gap-2" :disabled="isStarting" @click="handleStartScan">
          <Loader2 v-if="isStarting" class="h-4 w-4 animate-spin" />
          <Search v-else class="h-4 w-4" />
          인터넷 탐지 시작
        </Button>
      </CardContent>
    </Card>

    <!-- Scan History -->
    <div v-if="!isLoading && scanHistory.length > 0" class="mt-8">
      <h2 class="text-lg font-semibold">스캔 이력</h2>
      <div class="mt-4 space-y-2">
        <button
          v-for="scan in scanHistory"
          :key="scan.id"
          class="flex w-full items-center gap-4 rounded-lg border border-border p-4 text-left transition-colors hover:bg-accent/30"
          @click="viewDetail(scan)"
        >
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-muted">
            <Globe v-if="scan.scanType === 'INTERNET'" class="h-5 w-5 text-muted-foreground" />
            <Clock v-else class="h-5 w-5 text-muted-foreground" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <p class="text-sm font-medium">스캔 #{{ scan.id }}</p>
              <Badge variant="outline" class="text-xs">{{ scanTypeLabel(scan.scanType) }}</Badge>
              <Badge variant="secondary" :class="statusInfo(scan.status).class">
                {{ statusInfo(scan.status).label }}
              </Badge>
            </div>
            <div class="mt-1 flex items-center gap-3 text-xs text-muted-foreground">
              <span>{{ scan.totalImages }}개 이미지</span>
              <span v-if="scan.matchesFound > 0" class="text-destructive">{{ scan.matchesFound }}건 탐지</span>
              <span>{{ formatDate(scan.createdAt) }}</span>
            </div>
          </div>
          <ChevronRight class="h-4 w-4 shrink-0 text-muted-foreground" />
        </button>
      </div>
    </div>

    <!-- Scan Detail Dialog -->
    <Dialog v-model:open="showDetail">
      <DialogContent class="max-h-[80vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>스캔 상세 결과</DialogTitle>
          <DialogDescription v-if="selectedDetail">
            {{ selectedDetail.scan.totalImages }}개 이미지 스캔
            <template v-if="selectedDetail.scan.scanType === 'INTERNET'">
              (인터넷 탐지) — {{ selectedDetail.internetResults.length }}건 도용 의심
            </template>
            <template v-else>
              — {{ selectedDetail.results.length }}건 도용 의심
            </template>
          </DialogDescription>
        </DialogHeader>

        <div v-if="selectedDetail" class="mt-4">
          <!-- Internet Results -->
          <template v-if="selectedDetail.scan.scanType === 'INTERNET'">
            <div v-if="selectedDetail.internetResults.length === 0" class="flex flex-col items-center py-8 text-center">
              <CheckCircle2 class="h-10 w-10 text-green-500" />
              <p class="mt-3 font-medium">도용이 감지되지 않았습니다</p>
              <p class="mt-1 text-sm text-muted-foreground">인터넷에서 유사 이미지를 찾지 못했습니다</p>
            </div>

            <div v-else class="space-y-3">
              <div
                v-for="result in selectedDetail.internetResults"
                :key="result.id"
                class="rounded-lg border border-border p-4"
              >
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <AlertTriangle class="h-4 w-4 text-destructive" />
                    <span class="text-sm font-medium">도용 의심</span>
                    <Badge variant="outline" class="text-xs">{{ result.searchEngine }}</Badge>
                  </div>
                  <Badge variant="destructive">{{ result.judgment }}</Badge>
                </div>

                <div class="mt-3 space-y-2">
                  <div class="flex items-start gap-2 text-sm">
                    <img
                      :src="result.foundImageUrl"
                      :alt="result.sourcePageTitle || '발견된 이미지'"
                      class="h-16 w-16 shrink-0 rounded-md border object-cover"
                      loading="lazy"
                      @error="($event.target as HTMLImageElement).style.display = 'none'"
                    />
                    <div class="min-w-0 flex-1">
                      <p v-if="result.sourcePageTitle" class="truncate text-sm font-medium">
                        {{ result.sourcePageTitle }}
                      </p>
                      <p v-if="result.sourcePageUrl" class="truncate text-xs text-muted-foreground">
                        {{ domainFromUrl(result.sourcePageUrl) }}
                      </p>
                      <a
                        v-if="result.sourcePageUrl"
                        :href="result.sourcePageUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="mt-1 inline-flex items-center gap-1 text-xs text-primary hover:underline"
                      >
                        출처 페이지 열기 <ExternalLink class="h-3 w-3" />
                      </a>
                    </div>
                  </div>
                </div>

                <div class="mt-2 flex gap-4 text-xs text-muted-foreground">
                  <span>SSCD: {{ similarityPercent(result.sscdSimilarity) }}</span>
                  <span>DINO: {{ similarityPercent(result.dinoSimilarity) }}</span>
                </div>
              </div>
            </div>
          </template>

          <!-- Internal Results (legacy) -->
          <template v-else>
            <div v-if="selectedDetail.results.length === 0" class="flex flex-col items-center py-8 text-center">
              <CheckCircle2 class="h-10 w-10 text-green-500" />
              <p class="mt-3 font-medium">도용이 감지되지 않았습니다</p>
              <p class="mt-1 text-sm text-muted-foreground">모든 이미지가 안전합니다</p>
            </div>

            <div v-else class="space-y-3">
              <div
                v-for="result in selectedDetail.results"
                :key="result.id"
                class="rounded-lg border border-border p-4"
              >
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <AlertTriangle class="h-4 w-4 text-destructive" />
                    <span class="text-sm font-medium">도용 의심</span>
                  </div>
                  <Badge variant="destructive">{{ result.judgment }}</Badge>
                </div>
                <div class="mt-3 grid grid-cols-2 gap-4 text-xs text-muted-foreground">
                  <div>
                    <p class="font-medium text-foreground">내 이미지</p>
                    <p>ID: {{ result.sourceImageId }}</p>
                  </div>
                  <div>
                    <p class="font-medium text-foreground">매칭 이미지</p>
                    <p>ID: {{ result.matchedImageId }}</p>
                  </div>
                </div>
                <div class="mt-2 flex gap-4 text-xs text-muted-foreground">
                  <span>SSCD: {{ similarityPercent(result.sscdSimilarity) }}</span>
                  <span>DINO: {{ similarityPercent(result.dinoSimilarity) }}</span>
                </div>
              </div>
            </div>
          </template>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
