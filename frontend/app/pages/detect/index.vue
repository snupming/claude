<script setup lang="ts">
import { Search, Globe, ScanLine, AlertTriangle, CheckCircle2, Loader2, Clock, ChevronRight, ExternalLink, FileDown, FileText } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
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

// 유사도 70% 이상만 필터 + 높은 순 정렬
const filteredInternetResults = computed(() => {
  if (!selectedDetail.value) return []
  return selectedDetail.value.internetResults
    .filter((r) => {
      const maxSim = Math.max(r.sscdSimilarity ?? 0, r.dinoSimilarity ?? 0)
      return maxSim >= 0.7
    })
    .sort((a, b) => {
      const simA = Math.max(a.sscdSimilarity ?? 0, a.dinoSimilarity ?? 0)
      const simB = Math.max(b.sscdSimilarity ?? 0, b.dinoSimilarity ?? 0)
      return simB - simA
    })
})

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

// 증거자료 / 내용증명
const showLetterForm = ref(false)
const letterScanId = ref<number>(0)
const letterForm = ref({
  senderName: '',
  senderAddress: '',
  senderPhone: '',
  recipientName: '',
  recipientAddress: '',
  workTitle: '',
  creationDate: '',
  firstPublicationInfo: '',
  copyrightRegNumber: '',
  damageAmount: '',
  bankName: '',
  accountNumber: '',
  accountHolder: '',
  complianceDays: 14,
})

async function downloadReport(scanId: number, format: 'pdf' | 'docx') {
  try {
    const blob = await $fetch<Blob>(`/api/evidence/${scanId}/report`, {
      params: { format },
      responseType: 'blob',
    })
    downloadBlob(blob, `evidence_report_${scanId}.${format}`)
    toast.success('증거자료 다운로드 완료')
  } catch {
    toast.error('증거자료 생성에 실패했습니다')
  }
}

async function downloadLetter(format: 'pdf' | 'docx') {
  try {
    const blob = await $fetch<Blob>(`/api/evidence/${letterScanId.value}/letter`, {
      method: 'POST',
      body: letterForm.value,
      params: { format },
      responseType: 'blob',
    })
    downloadBlob(blob, `certified_letter_${letterScanId.value}.${format}`)
    toast.success('내용증명 다운로드 완료')
    showLetterForm.value = false
  } catch {
    toast.error('내용증명 생성에 실패했습니다')
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
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
            <Button v-if="activeScan.matchesFound > 0" size="sm" @click="downloadReport(activeScan!.id, 'pdf')">
              증거 PDF
            </Button>
            <Button v-if="activeScan.matchesFound > 0" variant="outline" size="sm" @click="showLetterForm = true; letterScanId = activeScan!.id">
              내용증명
            </Button>
            <Button variant="ghost" size="sm" @click="clearActiveScan">
              닫기
            </Button>
          </div>
        </div>
        <!-- 도용 건 있을 때 로톡 안내 -->
        <div v-if="activeScan.matchesFound > 0" class="mt-4 flex items-center justify-between rounded-lg bg-blue-50 p-3 dark:bg-blue-950/30">
          <p class="text-sm text-muted-foreground">전문 변호사의 도움이 필요하신가요?</p>
          <a href="https://www.lawtalk.co.kr/lawyers?keyword=지식재산권&category=지식재산권%2F엔터" target="_blank" rel="noopener noreferrer" class="text-sm font-medium text-primary hover:underline">
            로톡에서 변호사 찾기 →
          </a>
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
      <DialogContent class="max-h-[85vh] overflow-y-auto sm:max-w-5xl">
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
            <div v-if="filteredInternetResults.length === 0" class="flex flex-col items-center py-8 text-center">
              <CheckCircle2 class="h-10 w-10 text-green-500" />
              <p class="mt-3 font-medium">도용이 감지되지 않았습니다</p>
              <p class="mt-1 text-sm text-muted-foreground">유사도 70% 이상인 결과가 없습니다</p>
            </div>

            <div v-else class="space-y-4">
              <p class="text-xs text-muted-foreground">
                유사도 70% 이상 {{ filteredInternetResults.length }}건 (전체 {{ selectedDetail.internetResults.length }}건)
              </p>
              <DetectionResultCard
                v-for="result in filteredInternetResults"
                :key="result.id"
                :result="result"
              />
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

    <!-- 내용증명 작성 폼 다이얼로그 -->
    <Dialog v-model:open="showLetterForm">
      <DialogContent class="max-h-[85vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>내용증명 작성</DialogTitle>
          <DialogDescription>필수 항목을 입력하면 나머지는 탐지 데이터로 자동 작성됩니다.</DialogDescription>
        </DialogHeader>

        <div class="mt-4 space-y-4">
          <h3 class="text-sm font-semibold">발신인 정보</h3>
          <div class="grid grid-cols-2 gap-3">
            <input v-model="letterForm.senderName" placeholder="성명 *" class="rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />
            <input v-model="letterForm.senderPhone" placeholder="연락처 *" class="rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />
          </div>
          <input v-model="letterForm.senderAddress" placeholder="주소 *" class="w-full rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />

          <h3 class="text-sm font-semibold">수신인 정보</h3>
          <input v-model="letterForm.recipientName" placeholder="성명/상호 *" class="w-full rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />
          <input v-model="letterForm.recipientAddress" placeholder="주소 *" class="w-full rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />

          <h3 class="text-sm font-semibold">저작물 정보</h3>
          <input v-model="letterForm.workTitle" placeholder="저작물명 *" class="w-full rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />
          <div class="grid grid-cols-2 gap-3">
            <input v-model="letterForm.creationDate" placeholder="창작일 (YYYY.MM.DD) *" class="rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />
            <input v-model="letterForm.firstPublicationInfo" placeholder="최초 공표 정보" class="rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />
          </div>
          <input v-model="letterForm.copyrightRegNumber" placeholder="저작권 등록번호 (선택)" class="w-full rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />

          <h3 class="text-sm font-semibold">손해배상</h3>
          <input v-model="letterForm.damageAmount" placeholder="청구금액 (원) *" class="w-full rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />
          <div class="grid grid-cols-3 gap-3">
            <input v-model="letterForm.bankName" placeholder="은행" class="rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />
            <input v-model="letterForm.accountNumber" placeholder="계좌번호" class="rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />
            <input v-model="letterForm.accountHolder" placeholder="예금주" class="rounded-md border border-border bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none" />
          </div>

          <div class="flex items-center gap-2">
            <span class="text-sm text-muted-foreground">이행기한:</span>
            <input v-model.number="letterForm.complianceDays" type="number" min="7" max="30" class="w-20 rounded-md border border-border bg-transparent px-3 py-2 text-sm focus:border-primary focus:outline-none" />
            <span class="text-sm text-muted-foreground">일</span>
          </div>
        </div>

        <div class="mt-6 flex justify-end gap-2">
          <Button variant="outline" size="sm" @click="showLetterForm = false">취소</Button>
          <Button size="sm" @click="downloadLetter('pdf')">
            <FileDown class="mr-1 h-3 w-3" /> PDF
          </Button>
          <Button variant="outline" size="sm" @click="downloadLetter('docx')">
            <FileText class="mr-1 h-3 w-3" /> DOCX
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
