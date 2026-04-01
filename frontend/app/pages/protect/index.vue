<script setup lang="ts">
import { ShieldCheck, Upload, Image as ImageIcon, Loader2, Trash2 } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import { Skeleton } from '@/components/ui/skeleton'
import { Progress } from '@/components/ui/progress'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useImageUpload } from '@/composables/useImageUpload'

definePageMeta({
  layout: 'dashboard',
})

interface ImageItem {
  id: number
  name: string
  storagePath: string | null
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
const hasImages = computed(() => images.value.length > 0 || totalElements.value > 0)

// Upload
const isDragOver = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const { queue, isUploading, hasFiles, pendingCount, successCount, addFiles, uploadAll, clear } = useImageUpload()
const errorCount = computed(() => queue.value.filter(i => i.status === 'error').length)
const doneCount = computed(() => successCount.value + errorCount.value)
const uploadProgress = computed(() => {
  if (queue.value.length === 0) return 0
  return Math.round((doneCount.value / queue.value.length) * 100)
})

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
  if (successCount.value > 0) {
    toast.success(`${successCount.value}개 이미지 업로드 완료`)
    fetchImages()
  }
  if (errorCount.value > 0) {
    toast.error(`${errorCount.value}개 업로드 실패`)
  }
  clear()
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

// Delete
const showDeleteDialog = ref(false)
const deleteTarget = ref<ImageItem | null>(null)
const isDeleting = ref(false)

function confirmDelete(image: ImageItem) {
  deleteTarget.value = image
  showDeleteDialog.value = true
}

async function deleteImage() {
  if (!deleteTarget.value) return
  isDeleting.value = true
  const deletedId = deleteTarget.value.id
  try {
    await $fetch(`/api/images/${deletedId}`, { method: 'DELETE' })
    images.value = images.value.filter(img => img.id !== deletedId)
    totalElements.value = Math.max(0, totalElements.value - 1)
    showDeleteDialog.value = false
    deleteTarget.value = null
    toast.success('삭제되었습니다')
    try {
      const params: Record<string, string> = { page: String(currentPage.value), size: '20' }
      if (filterStatus.value) params.status = filterStatus.value
      const data = await $fetch<ImageListData>('/api/images', { params })
      images.value = data.images
      totalElements.value = data.totalElements
      totalPages.value = data.totalPages
    } catch {
      // 백그라운드 동기화 실패는 무시
    }
  } catch {
    toast.error('삭제에 실패했습니다')
  } finally {
    isDeleting.value = false
  }
}

function thumbnailUrl(image: ImageItem): string | null {
  if (!image.storagePath) return null
  return `/api/images/file/${image.storagePath}`
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
      <Button v-if="hasImages" class="gap-2" @click="fileInputRef?.click()">
        <Upload class="h-4 w-4" />
        이미지 업로드
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

    <!-- Inline Upload Status (업로드 진행 중 or 파일 선택됨) -->
    <div
        v-if="isUploading || hasFiles"
        class="mt-6 rounded-lg border border-border bg-card p-5"
    >
      <template v-if="isUploading || doneCount > 0">
        <div class="flex items-center gap-4">
          <div class="min-w-0 flex-1">
            <Progress :model-value="uploadProgress" class="h-2" />
          </div>
          <p class="shrink-0 text-sm text-muted-foreground">
            {{ doneCount }} / {{ queue.length }}
          </p>
        </div>
        <p v-if="errorCount > 0" class="mt-2 text-xs text-destructive">
          {{ errorCount }}개 실패
        </p>
      </template>
      <template v-else>
        <div class="flex items-center justify-between">
          <p class="text-sm text-muted-foreground">{{ pendingCount }}개 이미지 선택됨</p>
          <div class="flex gap-2">
            <Button variant="outline" size="sm" @click="clear">초기화</Button>
            <Button size="sm" @click="handleUploadAll">업로드 시작</Button>
          </div>
        </div>
      </template>
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
            <div class="h-14 w-14 shrink-0 overflow-hidden rounded-lg bg-muted">
              <img
                  v-if="thumbnailUrl(image)"
                  :src="thumbnailUrl(image)!"
                  :alt="image.name"
                  class="h-full w-full object-cover"
                  loading="lazy"
              >
              <div v-else class="flex h-full w-full items-center justify-center">
                <ImageIcon class="h-6 w-6 text-muted-foreground/50" />
              </div>
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

            <div class="flex items-center gap-2">
              <Badge
                  variant="secondary"
                  :class="statusLabels[image.status]?.class"
              >
                {{ statusLabels[image.status]?.label ?? image.status }}
              </Badge>
              <button
                  class="rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive"
                  @click.stop="confirmDelete(image)"
              >
                <Trash2 class="h-4 w-4" />
              </button>
            </div>
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

      <!-- Empty State — 드래그 & 드롭 통합 -->
      <template v-else>
        <Card
            class="mt-4 transition-colors"
            :class="isDragOver ? 'border-primary bg-primary/5' : ''"
            @dragover="onDragOver"
            @dragleave="onDragLeave"
            @drop="onDrop"
        >
          <CardContent class="flex flex-col items-center py-16">
            <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10">
              <ShieldCheck class="h-7 w-7 text-primary" />
            </div>
            <h3 class="mt-4 text-lg font-semibold">이미지를 업로드하세요</h3>
            <p class="mt-1.5 text-center text-sm text-muted-foreground">
              상품 이미지를 드래그하거나 아래 버튼으로 업로드하면<br>AI가 자동으로 비가시 워터마크를 삽입합니다
            </p>
            <div class="mt-6 flex gap-3">
              <Button class="gap-2" @click="fileInputRef?.click()">
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

    <!-- Delete Confirmation Dialog -->
    <Dialog v-model:open="showDeleteDialog">
      <DialogContent class="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>이미지 삭제</DialogTitle>
          <DialogDescription>
            "{{ deleteTarget?.name }}" 이미지를 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.
          </DialogDescription>
        </DialogHeader>
        <div class="flex justify-end gap-2 pt-4">
          <Button variant="outline" size="sm" @click="showDeleteDialog = false" :disabled="isDeleting">
            취소
          </Button>
          <Button variant="destructive" size="sm" @click="deleteImage" :disabled="isDeleting">
            <Loader2 v-if="isDeleting" class="mr-2 h-3 w-3 animate-spin" />
            삭제
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
