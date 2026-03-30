<script setup lang="ts">
import { ShieldCheck, Upload, Image as ImageIcon, CheckCircle2, XCircle, Loader2, File as FileIcon } from 'lucide-vue-next'
import { Skeleton } from '@/components/ui/skeleton'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet'
import { useImageUpload } from '@/composables/useImageUpload'

definePageMeta({
  layout: 'dashboard',
})

interface ImageItem {
  id: number
  name: string
  status: 'PENDING' | 'PROTECTED' | 'INDEXED' | 'FAILED'
  fileSize: number
  width: number
  height: number
  sourceType: string
  sourcePlatform: string | null
  sourceProductId: string | null
  sourceImageUrl: string | null
  createdAt: string
  indexedAt: string | null
}

interface ImageListData {
  images: ImageItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

const isLoading = ref(true)
const images = ref<ImageItem[]>([])
const totalElements = ref(0)
const totalPages = ref(0)
const currentPage = ref(0)
const filterStatus = ref<string | null>(null)

// Upload Sheet
const showUploadSheet = ref(false)
const isDragOver = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const { queue, isUploading, hasFiles, pendingCount, successCount, addFiles, removeFile, uploadAll, clear } = useImageUpload()

async function fetchImages() {
  isLoading.value = true
  try {
    const params: Record<string, string> = {
      page: String(currentPage.value),
      size: '20',
    }
    if (filterStatus.value) params.status = filterStatus.value

    const data = await $fetch<ImageListData>('/api/images', { params })
    images.value = data.images
    totalElements.value = data.totalElements
    totalPages.value = data.totalPages
  } catch {
    images.value = []
  } finally {
    isLoading.value = false
  }
}

function setFilter(status: string | null) {
  filterStatus.value = status
  currentPage.value = 0
  fetchImages()
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

function openUploadSheet() {
  showUploadSheet.value = true
}

function onSheetClose() {
  if (successCount.value > 0) {
    fetchImages()
  }
  clear()
}

function onDragOver(e: DragEvent) {
  e.preventDefault()
  isDragOver.value = true
}

function onDragLeave() {
  isDragOver.value = false
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  isDragOver.value = false
  if (e.dataTransfer?.files) {
    addFiles(e.dataTransfer.files)
  }
}

function onFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) {
    addFiles(input.files)
    input.value = ''
  }
}

async function handleUploadAll() {
  await uploadAll()
}

const statusLabels: Record<string, { label: string; class: string }> = {
  PENDING: { label: '대기', class: 'bg-muted text-muted-foreground' },
  PROTECTED: { label: '보호됨', class: 'bg-primary/10 text-primary' },
  INDEXED: { label: '인덱싱', class: 'bg-green-500/10 text-green-600' },
  FAILED: { label: '실패', class: 'bg-destructive/10 text-destructive' },
}

const sourceLabels: Record<string, string> = {
  UPLOAD: '직접 업로드',
  NAVER: '네이버 스마트스토어',
  COUPANG: '쿠팡',
  GMARKET: 'G마켓',
  ST11: '11번가',
}

onMounted(fetchImages)
</script>

<template>
  <div class="mx-auto max-w-4xl px-6 py-8">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold tracking-tight">이미지 보호</h1>
        <p class="mt-1 text-sm text-muted-foreground">
          상품 이미지에 비가시 워터마크를 삽입하여 보호합니다
        </p>
      </div>
      <Button class="gap-2" @click="openUploadSheet">
        <Upload class="h-4 w-4" />
        이미지 업로드
      </Button>
    </div>

    <!-- Filter Tabs -->
    <div class="mt-6 flex items-center gap-1 border-b border-border">
      <button
        class="border-b-2 px-4 py-2 text-sm font-medium transition-colors"
        :class="filterStatus === null ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
        @click="setFilter(null)"
      >
        전체
        <Badge v-if="totalElements > 0" variant="secondary" class="ml-1.5">{{ totalElements }}</Badge>
      </button>
      <button
        class="border-b-2 px-4 py-2 text-sm font-medium transition-colors"
        :class="filterStatus === 'PROTECTED' ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
        @click="setFilter('PROTECTED')"
      >
        보호됨
      </button>
      <button
        class="border-b-2 px-4 py-2 text-sm font-medium transition-colors"
        :class="filterStatus === 'INDEXED' ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
        @click="setFilter('INDEXED')"
      >
        인덱싱
      </button>
      <button
        class="border-b-2 px-4 py-2 text-sm font-medium transition-colors"
        :class="filterStatus === 'PENDING' ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
        @click="setFilter('PENDING')"
      >
        대기
      </button>
    </div>

    <!-- Image List -->
    <div class="mt-4">
      <!-- Skeleton Loading -->
      <template v-if="isLoading">
        <div class="space-y-3">
          <div v-for="i in 5" :key="i" class="flex items-center gap-4 rounded-lg border border-border p-4">
            <Skeleton class="h-14 w-14 rounded-lg" />
            <div class="flex-1 space-y-2">
              <Skeleton class="h-4 w-40" />
              <Skeleton class="h-3 w-56" />
            </div>
            <Skeleton class="h-6 w-16 rounded-full" />
          </div>
        </div>
      </template>

      <!-- Image Items -->
      <template v-else-if="images.length > 0">
        <div class="space-y-2">
          <div
            v-for="image in images"
            :key="image.id"
            class="flex items-center gap-4 rounded-lg border border-border p-4 transition-colors hover:bg-accent/30"
          >
            <div class="flex h-14 w-14 shrink-0 items-center justify-center rounded-lg bg-muted">
              <ImageIcon class="h-6 w-6 text-muted-foreground/50" />
            </div>

            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium">{{ image.name }}</p>
              <div class="mt-1 flex items-center gap-3 text-xs text-muted-foreground">
                <span>{{ image.width }}x{{ image.height }}</span>
                <span>{{ formatFileSize(image.fileSize) }}</span>
                <span>{{ sourceLabels[image.sourceType] ?? image.sourceType }}</span>
                <span>{{ formatDate(image.createdAt) }}</span>
              </div>
            </div>

            <Badge
              variant="secondary"
              :class="statusLabels[image.status]?.class"
            >
              {{ statusLabels[image.status]?.label ?? image.status }}
            </Badge>
          </div>
        </div>

        <!-- Pagination -->
        <div v-if="totalPages > 1" class="mt-6 flex items-center justify-center gap-2">
          <Button
            variant="outline"
            size="sm"
            :disabled="currentPage === 0"
            @click="currentPage--; fetchImages()"
          >
            이전
          </Button>
          <span class="text-sm text-muted-foreground">
            {{ currentPage + 1 }} / {{ totalPages }}
          </span>
          <Button
            variant="outline"
            size="sm"
            :disabled="currentPage >= totalPages - 1"
            @click="currentPage++; fetchImages()"
          >
            다음
          </Button>
        </div>
      </template>

      <!-- Empty State -->
      <template v-else>
        <Card class="mt-4">
          <CardContent class="flex flex-col items-center py-16">
            <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10">
              <ShieldCheck class="h-7 w-7 text-primary" />
            </div>
            <h3 class="mt-4 text-lg font-semibold">이미지를 업로드하세요</h3>
            <p class="mt-1.5 text-center text-sm text-muted-foreground">
              상품 이미지를 업로드하거나 스토어를 연동하면<br>AI가 자동으로 비가시 워터마크를 삽입합니다
            </p>
            <div class="mt-6 flex gap-3">
              <Button class="gap-2" @click="openUploadSheet">
                <Upload class="h-4 w-4" />
                이미지 업로드
              </Button>
              <NuxtLink to="/dashboard">
                <Button variant="outline" class="gap-2">
                  스토어 연동하기
                </Button>
              </NuxtLink>
            </div>
          </CardContent>
        </Card>
      </template>
    </div>

    <!-- Upload Sheet -->
    <Sheet v-model:open="showUploadSheet" @update:open="(v: boolean) => { if (!v) onSheetClose() }">
      <SheetContent side="right" class="flex flex-col overflow-y-auto">
        <SheetHeader>
          <SheetTitle>이미지 업로드</SheetTitle>
          <SheetDescription>
            JPEG, PNG, WebP 이미지를 업로드하세요. (최대 10MB)
          </SheetDescription>
        </SheetHeader>

        <!-- Drag & Drop Zone -->
        <div
          class="mt-4 flex flex-col items-center justify-center rounded-lg border-2 border-dashed p-8 transition-colors"
          :class="isDragOver ? 'border-primary bg-primary/5' : 'border-border'"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
        >
          <Upload class="h-8 w-8 text-muted-foreground/50" />
          <p class="mt-3 text-sm text-muted-foreground">
            이미지를 여기에 드래그하거나
          </p>
          <Button
            variant="outline"
            size="sm"
            class="mt-2"
            @click="fileInputRef?.click()"
          >
            파일 선택
          </Button>
          <input
            ref="fileInputRef"
            type="file"
            multiple
            accept="image/jpeg,image/png,image/webp"
            class="hidden"
            @change="onFileSelect"
          >
        </div>

        <!-- Upload Queue -->
        <div v-if="hasFiles" class="mt-4 flex-1 space-y-2 overflow-y-auto">
          <div
            v-for="item in queue"
            :key="item.id"
            class="flex items-center gap-3 rounded-lg border border-border p-3"
          >
            <FileIcon class="h-5 w-5 shrink-0 text-muted-foreground" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm">{{ item.file.name }}</p>
              <p class="text-xs text-muted-foreground">{{ formatFileSize(item.file.size) }}</p>
            </div>
            <div class="shrink-0">
              <Loader2 v-if="item.status === 'uploading'" class="h-4 w-4 animate-spin text-primary" />
              <CheckCircle2 v-else-if="item.status === 'success'" class="h-4 w-4 text-green-500" />
              <div v-else-if="item.status === 'error'" class="flex items-center gap-1">
                <XCircle class="h-4 w-4 text-destructive" />
              </div>
              <button
                v-else
                class="text-xs text-muted-foreground hover:text-foreground"
                @click="removeFile(item.id)"
              >
                삭제
              </button>
            </div>
          </div>
        </div>

        <!-- Error messages -->
        <div v-for="item in queue.filter(i => i.error)" :key="'err-' + item.id" class="mt-1">
          <p class="text-xs text-destructive">{{ item.file.name }}: {{ item.error }}</p>
        </div>

        <!-- Actions -->
        <div v-if="hasFiles" class="mt-4 flex items-center justify-between border-t border-border pt-4">
          <p class="text-sm text-muted-foreground">
            <template v-if="successCount > 0">{{ successCount }}개 완료</template>
            <template v-else>{{ pendingCount }}개 대기 중</template>
          </p>
          <div class="flex gap-2">
            <Button variant="outline" size="sm" @click="clear" :disabled="isUploading">
              초기화
            </Button>
            <Button size="sm" :disabled="isUploading || pendingCount === 0" @click="handleUploadAll">
              <Loader2 v-if="isUploading" class="mr-2 h-3 w-3 animate-spin" />
              업로드 시작
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  </div>
</template>
