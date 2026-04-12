export interface ScanInfo {
  id: number
  status: string
  scanType: 'INTERNAL' | 'INTERNET'
  totalImages: number
  scannedImages: number
  matchesFound: number
  progress: number
  createdAt: string
  completedAt: string | null
}

export interface DetectionResult {
  id: number
  sourceImageId: number
  matchedImageId: number
  matchedUserId: string
  sscdSimilarity: number | null
  dinoSimilarity: number | null
  judgment: string
  createdAt: string
}

export interface InternetDetectionResult {
  id: number
  sourceImageId: number
  foundImageUrl: string
  sourcePageUrl: string | null
  sourcePageTitle: string | null
  sscdSimilarity: number | null
  dinoSimilarity: number | null
  judgment: string
  searchEngine: string
  createdAt: string
  platformType: string | null
  sellerName: string | null
  businessRegNumber: string | null
  representativeName: string | null
  businessAddress: string | null
  contactPhone: string | null
  contactEmail: string | null
  storeUrl: string | null
  mailOrderNumber: string | null
  matchCount: number
  bestGuessLabel: string | null
  detectedEntity: string | null
}

export interface ScanDetail {
  scan: ScanInfo
  results: DetectionResult[]
  internetResults: InternetDetectionResult[]
}

export interface ScansPage {
  content: ScanInfo[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

const POLL_INTERVAL = 2000

// 클라이언트 전용 싱글톤 — 앱 전체에서 하나의 폴링 타이머만 운용.
// (CLAUDE.md 예외: 클라이언트 전용 런타임 싱글톤은 모듈 레벨 허용)
let pollTimer: ReturnType<typeof setInterval> | null = null
let pollInFlight = false

export function useDetection() {
  const activeScan = useState<ScanInfo | null>('active-scan', () => null)
  const isScanning = computed(() => activeScan.value?.status === 'SCANNING' || activeScan.value?.status === 'PENDING')

  async function startScan(): Promise<ScanInfo> {
    const scan = await $fetch<ScanInfo>('/api/detections/scan', { method: 'POST' })
    activeScan.value = scan
    startPolling(scan.id)
    return scan
  }

  async function startInternetScan(): Promise<ScanInfo> {
    const scan = await $fetch<ScanInfo>('/api/detections/internet-scan', { method: 'POST' })
    activeScan.value = scan
    startPolling(scan.id)
    return scan
  }

  async function fetchScans(page = 0, size = 10): Promise<ScansPage> {
    return await $fetch<ScansPage>('/api/detections/scans', {
      params: { page, size },
    })
  }

  async function fetchScanDetail(scanId: number): Promise<ScanDetail> {
    return await $fetch<ScanDetail>(`/api/detections/scans/${scanId}`)
  }

  function startPolling(scanId: number) {
    stopPolling()
    pollTimer = setInterval(() => {
      if (pollInFlight) return // 이전 요청 완료 대기 — 레이스 방지
      pollInFlight = true
      fetchScanDetail(scanId)
        .then((detail) => {
          activeScan.value = detail.scan
          if (detail.scan.status === 'COMPLETED' || detail.scan.status === 'FAILED') {
            stopPolling()
          }
        })
        .catch((err: unknown) => {
          console.warn('[useDetection] poll failed:', err)
          stopPolling()
        })
        .finally(() => {
          pollInFlight = false
        })
    }, POLL_INTERVAL)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    pollInFlight = false
  }

  async function resumeIfActive() {
    try {
      const page = await fetchScans(0, 1)
      if (page.content.length > 0) {
        const latest = page.content[0]
        if (latest && (latest.status === 'SCANNING' || latest.status === 'PENDING')) {
          activeScan.value = latest
          startPolling(latest.id)
        } else if (latest && (activeScan.value?.status === 'SCANNING' || activeScan.value?.status === 'PENDING')) {
          // SPA 복귀: stale SCANNING → 실제 COMPLETED/FAILED로 동기화
          activeScan.value = latest
        } else if (!activeScan.value && latest?.completedAt) {
          // 새로고침 복귀: 최근 완료 스캔이면 배너 표시 (5분 이내)
          const completedAt = new Date(latest.completedAt).getTime()
          if (Date.now() - completedAt < 5 * 60 * 1000) {
            activeScan.value = latest
          }
        }
      } else if (activeScan.value) {
        // 스캔 없으면 stale state 정리
        activeScan.value = null
      }
    } catch (err: unknown) {
      console.warn('[useDetection] resumeIfActive failed:', err)
    }
  }

  function clearActiveScan() {
    stopPolling()
    activeScan.value = null
  }

  return {
    activeScan,
    isScanning,
    startScan,
    startInternetScan,
    fetchScans,
    fetchScanDetail,
    resumeIfActive,
    clearActiveScan,
    stopPolling,
  }
}
