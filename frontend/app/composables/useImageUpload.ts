import { extractErrorMessage } from '@/lib/utils'

export interface UploadItem {
  id: string
  file: File
  status: 'pending' | 'uploading' | 'success' | 'error'
  progress: number
  error?: string
}

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB
const MAX_CONCURRENT = 3

export function useImageUpload() {
  const queue = useState<UploadItem[]>('upload-queue', () => [])
  const keywords = useState<string>('upload-keywords', () => '')
  const isUploading = computed(() => queue.value.some(item => item.status === 'uploading'))

  function addFiles(files: FileList | File[]) {
    for (const file of Array.from(files)) {
      if (!ALLOWED_TYPES.includes(file.type)) {
        queue.value.push({
          id: crypto.randomUUID(),
          file,
          status: 'error',
          progress: 0,
          error: '지원하지 않는 형식입니다. (JPEG, PNG, WebP)',
        })
        continue
      }
      if (file.size > MAX_FILE_SIZE) {
        queue.value.push({
          id: crypto.randomUUID(),
          file,
          status: 'error',
          progress: 0,
          error: '파일 크기는 10MB 이하여야 합니다.',
        })
        continue
      }
      queue.value.push({
        id: crypto.randomUUID(),
        file,
        status: 'pending',
        progress: 0,
      })
    }
  }

  function removeFile(id: string) {
    queue.value = queue.value.filter(item => item.id !== id)
  }

  async function uploadOne(item: UploadItem) {
    item.status = 'uploading'
    item.progress = 0

    const formData = new FormData()
    formData.append('file', item.file)
    if (keywords.value.trim()) {
      formData.append('keywords', keywords.value.trim())
    }

    try {
      await $fetch('/api/images/upload', {
        method: 'POST',
        body: formData,
      })
      item.status = 'success'
      item.progress = 100
    } catch (err: unknown) {
      item.status = 'error'
      item.error = extractErrorMessage(err) ?? '업로드에 실패했습니다.'
    }
  }

  async function uploadAll() {
    const pending = queue.value.filter(item => item.status === 'pending')
    // Process in chunks of MAX_CONCURRENT
    for (let i = 0; i < pending.length; i += MAX_CONCURRENT) {
      const chunk = pending.slice(i, i + MAX_CONCURRENT)
      await Promise.all(chunk.map(uploadOne))
    }
  }

  function clear() {
    queue.value = []
    keywords.value = ''
  }

  const hasFiles = computed(() => queue.value.length > 0)
  const pendingCount = computed(() => queue.value.filter(i => i.status === 'pending').length)
  const successCount = computed(() => queue.value.filter(i => i.status === 'success').length)

  return {
    queue,
    keywords,
    isUploading,
    hasFiles,
    pendingCount,
    successCount,
    addFiles,
    removeFile,
    uploadAll,
    clear,
  }
}
