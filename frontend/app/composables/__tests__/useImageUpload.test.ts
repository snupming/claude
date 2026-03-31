import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock $fetch globally
const mockFetch = vi.fn()
vi.stubGlobal('$fetch', mockFetch)

import { useImageUpload } from '../useImageUpload'

declare const __resetState: () => void

function createMockFile(name: string, size: number, type: string): File {
  const buffer = new ArrayBuffer(size)
  return new File([buffer], name, { type })
}

describe('useImageUpload', () => {
  beforeEach(() => {
    __resetState()
    mockFetch.mockReset()
  })

  it('addFiles accepts valid JPEG images', () => {
    const { addFiles, queue, hasFiles } = useImageUpload()
    const file = createMockFile('test.jpg', 1024, 'image/jpeg')

    addFiles([file])

    expect(hasFiles.value).toBe(true)
    expect(queue.value).toHaveLength(1)
    expect(queue.value[0].status).toBe('pending')
    expect(queue.value[0].file.name).toBe('test.jpg')
  })

  it('addFiles accepts valid PNG images', () => {
    const { addFiles, queue } = useImageUpload()
    const file = createMockFile('test.png', 1024, 'image/png')

    addFiles([file])

    expect(queue.value).toHaveLength(1)
    expect(queue.value[0].status).toBe('pending')
  })

  it('addFiles accepts valid WebP images', () => {
    const { addFiles, queue } = useImageUpload()
    const file = createMockFile('test.webp', 1024, 'image/webp')

    addFiles([file])

    expect(queue.value).toHaveLength(1)
    expect(queue.value[0].status).toBe('pending')
  })

  it('addFiles rejects invalid types', () => {
    const { addFiles, queue } = useImageUpload()
    const file = createMockFile('doc.pdf', 1024, 'application/pdf')

    addFiles([file])

    expect(queue.value).toHaveLength(1)
    expect(queue.value[0].status).toBe('error')
    expect(queue.value[0].error).toContain('지원하지 않는 형식')
  })

  it('addFiles rejects oversized files (>10MB)', () => {
    const { addFiles, queue } = useImageUpload()
    const file = createMockFile('huge.jpg', 11 * 1024 * 1024, 'image/jpeg')

    addFiles([file])

    expect(queue.value).toHaveLength(1)
    expect(queue.value[0].status).toBe('error')
    expect(queue.value[0].error).toContain('10MB')
  })

  it('addFiles allows files exactly 10MB', () => {
    const { addFiles, queue } = useImageUpload()
    const file = createMockFile('exact.jpg', 10 * 1024 * 1024, 'image/jpeg')

    addFiles([file])

    expect(queue.value).toHaveLength(1)
    expect(queue.value[0].status).toBe('pending')
  })

  it('addFiles handles mixed valid and invalid files', () => {
    const { addFiles, queue, pendingCount } = useImageUpload()
    const valid = createMockFile('ok.jpg', 1024, 'image/jpeg')
    const invalid = createMockFile('bad.gif', 1024, 'image/gif')

    addFiles([valid, invalid])

    expect(queue.value).toHaveLength(2)
    expect(pendingCount.value).toBe(1)
  })

  it('removeFile removes from queue', () => {
    const { addFiles, removeFile, queue } = useImageUpload()
    const file = createMockFile('test.jpg', 1024, 'image/jpeg')

    addFiles([file])
    const id = queue.value[0].id

    removeFile(id)

    expect(queue.value).toHaveLength(0)
  })

  it('clear empties queue', () => {
    const { addFiles, clear, queue, hasFiles } = useImageUpload()
    const file1 = createMockFile('a.jpg', 1024, 'image/jpeg')
    const file2 = createMockFile('b.png', 1024, 'image/png')

    addFiles([file1, file2])
    expect(queue.value).toHaveLength(2)

    clear()
    expect(queue.value).toHaveLength(0)
    expect(hasFiles.value).toBe(false)
  })

  it('uploadAll processes uploads and updates status', async () => {
    mockFetch.mockResolvedValue({ id: 1, name: 'test.jpg' })

    const { addFiles, uploadAll, queue, successCount } = useImageUpload()
    addFiles([
      createMockFile('a.jpg', 1024, 'image/jpeg'),
      createMockFile('b.jpg', 1024, 'image/jpeg'),
    ])

    await uploadAll()

    expect(successCount.value).toBe(2)
    expect(queue.value.every(i => i.status === 'success')).toBe(true)
  })

  it('uploadAll handles upload errors gracefully', async () => {
    mockFetch.mockRejectedValue({ data: { statusMessage: '서버 오류' } })

    const { addFiles, uploadAll, queue } = useImageUpload()
    addFiles([createMockFile('fail.jpg', 1024, 'image/jpeg')])

    await uploadAll()

    expect(queue.value[0].status).toBe('error')
    expect(queue.value[0].error).toBeDefined()
  })

  it('pendingCount and successCount are computed correctly', () => {
    const { addFiles, queue, pendingCount, successCount } = useImageUpload()
    addFiles([
      createMockFile('a.jpg', 1024, 'image/jpeg'),
      createMockFile('b.jpg', 1024, 'image/jpeg'),
      createMockFile('c.pdf', 1024, 'application/pdf'),
    ])

    expect(pendingCount.value).toBe(2)
    expect(successCount.value).toBe(0)
  })

  it('isUploading is true during upload', async () => {
    let resolveUpload: (v: any) => void
    mockFetch.mockReturnValue(new Promise(r => { resolveUpload = r }))

    const { addFiles, uploadAll, isUploading } = useImageUpload()
    addFiles([createMockFile('test.jpg', 1024, 'image/jpeg')])

    expect(isUploading.value).toBe(false)

    const uploadPromise = uploadAll()

    // During upload, isUploading should be true
    expect(isUploading.value).toBe(true)

    resolveUpload!({ id: 1 })
    await uploadPromise

    expect(isUploading.value).toBe(false)
  })
})
