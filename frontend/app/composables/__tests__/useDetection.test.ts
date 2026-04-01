import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// Mock $fetch globally
const mockFetch = vi.fn()
vi.stubGlobal('$fetch', mockFetch)

// Import after mocking
import { useDetection } from '../useDetection'
import type { ScanInfo, ScansPage, ScanDetail } from '../useDetection'

declare const __resetState: () => void

describe('useDetection', () => {
  beforeEach(() => {
    __resetState()
    mockFetch.mockReset()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const mockScan: ScanInfo = {
    id: 1,
    status: 'SCANNING',
    totalImages: 10,
    scannedImages: 0,
    matchesFound: 0,
    progress: 0,
    createdAt: '2026-01-01T00:00:00Z',
    completedAt: null,
  }

  const mockScansPage: ScansPage = {
    content: [mockScan],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 10,
  }

  it('startScan calls POST and sets activeScan', async () => {
    mockFetch.mockResolvedValueOnce(mockScan)

    const { startScan, activeScan } = useDetection()
    const result = await startScan()

    expect(mockFetch).toHaveBeenCalledWith('/api/detections/scan', { method: 'POST' })
    expect(result.id).toBe(1)
    expect(activeScan.value?.status).toBe('SCANNING')
  })

  it('fetchScans returns paginated data', async () => {
    mockFetch.mockResolvedValueOnce(mockScansPage)

    const { fetchScans } = useDetection()
    const result = await fetchScans(0, 10)

    expect(mockFetch).toHaveBeenCalledWith('/api/detections/scans', {
      params: { page: 0, size: 10 },
    })
    expect(result.content).toHaveLength(1)
    expect(result.totalElements).toBe(1)
  })

  it('fetchScanDetail returns scan + results', async () => {
    const mockDetail: ScanDetail = {
      scan: { ...mockScan, status: 'COMPLETED', scannedImages: 10, progress: 100 },
      results: [
        {
          id: 1,
          sourceImageId: 1,
          matchedImageId: 99,
          matchedUserId: 'other-user-uuid',
          sscdSimilarity: 0.5,
          dinoSimilarity: 0.8,
          judgment: 'INFRINGEMENT',
          createdAt: '2026-01-01T00:00:00Z',
        },
      ],
    }
    mockFetch.mockResolvedValueOnce(mockDetail)

    const { fetchScanDetail } = useDetection()
    const result = await fetchScanDetail(1)

    expect(mockFetch).toHaveBeenCalledWith('/api/detections/scans/1')
    expect(result.scan.status).toBe('COMPLETED')
    expect(result.results).toHaveLength(1)
    expect(result.results[0].judgment).toBe('INFRINGEMENT')
  })

  it('isScanning reflects SCANNING status', async () => {
    mockFetch.mockResolvedValueOnce(mockScan)

    const { startScan, isScanning } = useDetection()
    expect(isScanning.value).toBe(false)

    await startScan()
    expect(isScanning.value).toBe(true)
  })

  it('isScanning reflects PENDING status', () => {
    const { activeScan, isScanning } = useDetection()
    activeScan.value = { ...mockScan, status: 'PENDING' }
    expect(isScanning.value).toBe(true)
  })

  it('isScanning is false for COMPLETED', () => {
    const { activeScan, isScanning } = useDetection()
    activeScan.value = { ...mockScan, status: 'COMPLETED' }
    expect(isScanning.value).toBe(false)
  })

  it('clearActiveScan resets state', async () => {
    mockFetch.mockResolvedValueOnce(mockScan)

    const { startScan, activeScan, clearActiveScan } = useDetection()
    await startScan()
    expect(activeScan.value).not.toBeNull()

    clearActiveScan()
    expect(activeScan.value).toBeNull()
  })

  it('resumeIfActive starts polling for active scan', async () => {
    const activeScanData = { ...mockScan, status: 'SCANNING' }
    mockFetch.mockResolvedValueOnce({
      content: [activeScanData],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 1,
    } as ScansPage)

    const { resumeIfActive, activeScan } = useDetection()
    await resumeIfActive()

    expect(activeScan.value?.status).toBe('SCANNING')
  })

  it('resumeIfActive syncs stale SCANNING to COMPLETED on SPA return', async () => {
    const { resumeIfActive, activeScan } = useDetection()
    activeScan.value = { ...mockScan, status: 'SCANNING' }

    mockFetch.mockResolvedValueOnce({
      content: [{ ...mockScan, status: 'COMPLETED', scannedImages: 10, progress: 100, completedAt: new Date().toISOString() }],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 1,
    } as ScansPage)

    await resumeIfActive()

    expect(activeScan.value?.status).toBe('COMPLETED')
  })

  it('resumeIfActive syncs stale PENDING to FAILED on SPA return', async () => {
    const { resumeIfActive, activeScan } = useDetection()
    activeScan.value = { ...mockScan, status: 'PENDING' }

    mockFetch.mockResolvedValueOnce({
      content: [{ ...mockScan, status: 'FAILED', completedAt: new Date().toISOString() }],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 1,
    } as ScansPage)

    await resumeIfActive()

    expect(activeScan.value?.status).toBe('FAILED')
  })

  it('resumeIfActive shows recently completed scan on fresh load', async () => {
    const { resumeIfActive, activeScan } = useDetection()

    mockFetch.mockResolvedValueOnce({
      content: [{ ...mockScan, status: 'COMPLETED', scannedImages: 10, progress: 100, completedAt: new Date().toISOString() }],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 1,
    } as ScansPage)

    await resumeIfActive()

    expect(activeScan.value?.status).toBe('COMPLETED')
  })

  it('resumeIfActive ignores old completed scan on fresh load', async () => {
    const { resumeIfActive, activeScan } = useDetection()

    mockFetch.mockResolvedValueOnce({
      content: [{ ...mockScan, status: 'COMPLETED', completedAt: '2025-01-01T00:00:00Z' }],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 1,
    } as ScansPage)

    await resumeIfActive()

    expect(activeScan.value).toBeNull()
  })

  it('resumeIfActive clears stale state when no scans exist', async () => {
    const { resumeIfActive, activeScan } = useDetection()
    activeScan.value = { ...mockScan, status: 'SCANNING' }

    mockFetch.mockResolvedValueOnce({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 1,
    } as ScansPage)

    await resumeIfActive()

    expect(activeScan.value).toBeNull()
  })
})
