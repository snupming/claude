# Ownpic ML 모델 설계서

> **문서 버전**: v3.0 | **작성일**: 2026.03.28
> **통합 문서**: ml-poc-results v1.5, r3-trustmark-ui-guide v1.0, PoC README 3종 통합

---

## 1. 문서 이력

| 버전 | 일자 | 변경 내용 |
|------|------|---------|
| v1.0 | 2026-03-21 | 초안 — SigLIP2/SSCD 비교, 모델 확정 |
| v1.1 | 2026-03-21 | SSCD Bottom 5, 네거티브 테스트 추가 |
| v1.2 | 2026-03-21 | 감리 승인, R1~R7 잔여과제 정의 |
| v1.3 | 2026-03-22 | R1 대규모 재검증(300원본+540neg), threshold 0.30 확정, bg_swap MVP 제외 |
| v1.4 | 2026-03-23 | TrustMark Java ONNX 통합, Cloud Run 2Gi, SSCD file path API, ml-engine Python 제거 |
| v1.5 | 2026-03-27 | R2 재검증 — ISC2021 19종 + 네이버 스마트스토어 600장 + 듀얼 파이프라인 확정 |
| v3.0 | 2026-03-28 | R3 TrustMark 내구성 검증 통합, PoC 3종 통합, 단일 문서화 |

---

## 2. 모델 현황 요약

| 모델 | 파일 | 크기 | 차원 | 용도 | 라이선스 |
|------|------|------|------|------|---------|
| SSCD (sscd_disc_mixup) | sscd.onnx | 93MB | 512 | 이미지 복제 탐지 | MIT |
| DINOv2 (vits14_cls) | dinov2_vits14_cls.onnx | 85MB | 384 | 듀얼 파이프라인 보조 | Apache 2.0 |
| TrustMark Encoder (variant Q) | encoder_Q.onnx | 16.5MB | — | 워터마크 삽입 | MIT (ICCV 2025) |
| TrustMark Decoder (variant Q) | decoder_Q.onnx | 45.2MB | — | 워터마크 추출 | MIT (ICCV 2025) |

**합산**: ~240MB (Docker 이미지 레이어로 포함)

---

## 3. SSCD 복제 탐지

### 3.1 모델 스펙

| 항목 | 값 |
|------|---|
| 모델 | SSCD sscd_disc_mixup (ResNet50) |
| 출력 | 512차원 L2 정규화 임베딩 |
| 라이선스 | MIT |
| 추론 속도 (GPU RTX2060) | ~18ms/image (56 img/s) |
| 추론 속도 (CPU) | ~60ms/image (16.7 img/s) |

### 3.2 전처리 파이프라인

| 단계 | 처리 | 설명 |
|------|------|------|
| 0 | SHA256 해시 | 완전 동일 파일 즉시 확정 |
| 1 | 패딩 트리밍 | 단색 여백 자동 제거 |
| 2 | 320×320 square resize | SSCD 입력 크기 (aspect ratio 무시) |
| 3 | ImageNet 정규화 | mean=[0.485,0.456,0.406], std=[0.229,0.224,0.225] |
| 4 | SSCD 추론 | 512차원 L2 정규화 임베딩 생성 |
| 5 | pgvector 검색 | 코사인 유사도 ≥ 0.30 → 도용 후보 |
| 6 | 셀러 대시보드 | 사람 검증 |

**패딩 트리밍 상세** (필수 — 트리밍 없이 SSCD 실행 시 padding_black 유사도 0.29, gap 0.01로 운영 불가):

- 4개 코너 픽셀의 최빈색을 보더 색상으로 결정
- 상하좌우 각 변에서 보더 색상과 RGB 거리 30 이내인 행/열 스캔
- 최소 콘텐츠 영역 10% 보장 (과도 트리밍 방지)
- 5% 미만 트리밍이면 원본 그대로 반환

### 3.3 Threshold 0.30 확정 근거

- R1(300원본+540neg): FPR 0.0%, bg_swap 제외 Recall 96.3%
- R2(네이버 600장): Recall 100.0%, FPR 0.2% (허용)
- bg_swap(배경 교체)은 SSCD 구조적 한계 → MVP 제외, threshold 분리 불가

### 3.4 R1 검증 결과 (Ownpic 자체 이미지)

| 항목 | 수치 |
|------|------|
| 데이터 | Ownpic 상품 이미지 300원본 + 540neg |
| 변형 수 | 18종 (임의 정의) |
| Recall | 99.7% |
| FPR | 0.0% |
| Safe Gap | ~0.09 |
| Neg max | 0.2830 |

R1 기준 gap 0.09는 기준 0.10 미달이나, bg_swap 제외 18개 변형에서 정답 min ~0.37로 **조건부 통과**.

### 3.5 R2 재검증 — ISC2021 19종 변형 (네이버 스마트스토어 600장)

**ISC2021 기준 19종 변형 정의:**

| # | 변형명 | ISC2021 분류 | 내용 | 기존 대비 |
|---|--------|------------|------|----------|
| 1 | jpeg_q30 | Pixel / JPEG | JPEG quality=30 | 동일 |
| 2 | blur_gaussian | Pixel / blur | Gaussian blur radius=1.5% | **신규** |
| 3 | sharpen | Pixel / edge enhance | UnsharpMask percent=180 | **신규** |
| 4 | gamma_dark | Pixel / gamma | Gamma=1.6 | **신규** |
| 5 | gamma_bright | Pixel / gamma | Gamma=0.6 | **신규** |
| 6 | grayscale | Pixel / color palette | 그레이스케일 | **신규** |
| 7 | noise | Pixel / shuffling | Gaussian noise std=15 | **신규** |
| 8 | crop_tight | Spatial / crop | Center crop 60% (LANCZOS) | 개선 |
| 9 | crop_loose | Spatial / crop | Center crop 80% (LANCZOS) | 개선 |
| 10 | crop_random | Spatial / crop | 랜덤 오프셋 70~90% | **수정** |
| 11 | flip_h | Spatial / flip | 수평 반전 | 동일 |
| 12 | rotate_90 | Spatial / rotation | 90° 회전 | 동일 |
| 13 | rotate_180 | Spatial / rotation | 180° 회전 | 동일 |
| 14 | rotate_small | Spatial / rotation | ±5~10° (테두리대표색 fill) | **수정** |
| 15 | perspective | Spatial / perspective | 원근 왜곡 7% (BILINEAR) | **신규** |
| 16 | aspect_ratio | Spatial / aspect ratio | 가로 130% (LANCZOS) | 동일 |
| 17 | text_overlay | Overlay | RGBA alpha_composite 반투명 | **수정** |
| 18 | logo_overlay | Overlay | 비율 기반 15% (RGBA) | **수정** |
| 19 | screenshot | Overlay / background | 브라우저 시뮬레이션 85% | 동일 |

**폐기 변형**: brightness, color_warm, padding_black, padding_white, combo (ISC2021 미해당)

**변형별 SSCD 결과** (300원본 × 19변형 = 5,700쌍 + Negative 92,072쌍):

| 변형 | ISC2021 분류 | SSCD mean | 탐지(@0.30) |
|------|------------|-----------|------------|
| aspect_ratio | Spatial | 0.9995 | ✅ |
| blur_gaussian | Pixel | 0.7799 | ✅ |
| crop_loose | Spatial | 0.9032 | ✅ |
| crop_random | Spatial | 0.9005 | ✅ |
| crop_tight | Spatial | 0.8216 | ✅ |
| flip_h | Spatial | 0.9481 | ✅ |
| gamma_bright | Pixel | 0.9535 | ✅ |
| gamma_dark | Pixel | 0.9534 | ✅ |
| grayscale | Pixel | 0.9225 | ✅ |
| jpeg_q30 | Pixel | 0.9419 | ✅ |
| logo_overlay | Overlay | 0.9824 | ✅ |
| noise | Pixel | 0.8763 | ✅ |
| perspective | Spatial | 0.8986 | ✅ |
| rotate_180 | Spatial | 0.7627 | ✅ |
| rotate_90 | Spatial | 0.7177 | ✅ |
| rotate_small | Spatial | 0.8257 | ✅ |
| screenshot | Overlay | 0.8712 | ✅ |
| sharpen | Pixel | 0.9365 | ✅ |
| text_overlay | Overlay | 0.9969 | ✅ |

**SSCD 단독: 19종 전부 탐지 성공. Recall 99.9%, FPR=0% 기준 threshold=0.52.**

### 3.6 R1 vs R2 비교

| 항목 | R1 (Ownpic 자체) | R2 (네이버 도메인) |
|------|-----------------|------------------|
| 데이터 | Ownpic 상품 이미지 | 네이버 스마트스토어 600장 |
| 변형 수 | 18종 (임의 정의) | 19종 (ISC2021 표준) |
| Recall | 99.7% | **100.0%** |
| FPR | 0.0% | **0.2%** (허용) |
| Safe Gap (p5-p99) | ~0.09 | **0.4896** |
| Pos mean | — | 0.8943 |
| Neg mean | — | 0.0460 |
| Neg max | 0.2830 | 0.5161 |

---

## 4. DINOv2 듀얼 파이프라인

### 4.1 판정 로직

```
SSCD ≥ 0.30 OR DINOv2 ≥ 0.70 → 도용 의심
```

### 4.2 상호 보완

| 항목 | 수치 |
|------|------|
| Recall | **100.0%** |
| FPR | **0.2%** (허용, 셀러 대시보드 사람 검증) |
| DINOv2 rotate_90 | 0.641 (0.70 미달 → **SSCD 0.718로 커버**) |
| DINOv2 rotate_180 | 0.668 (0.70 미달 → **SSCD 0.763으로 커버**) |
| DINOv2 blur_gaussian | 0.676 (0.70 미달 → **SSCD 0.780으로 커버**) |

### 4.3 FPR=0% 최적 threshold 대안

```
SSCD ≥ 0.55 OR DINOv2 ≥ 0.90 → Recall 99.9%, FPR 0%
```

현재 threshold 유지 근거: **Recall 100% > FPR 0.2% 허용**, 보수적 운영 선호.

---

## 5. TrustMark 워터마킹

### 5.1 모델 스펙

| 항목 | 수치 |
|------|------|
| 모델 | TrustMark variant Q (encoder 16.5MB + decoder 45.2MB = 61.7MB) |
| 비트 정확도 | 100/100 (100.0% 라운드트립) |
| PSNR | 43.20 dB (스펙 43~45dB) |
| 인코딩 지연 (Java CPU) | 399ms |
| 디코딩 지연 (Java CPU) | 116ms |
| 라이선스 | MIT (ICCV 2025) |

### 5.2 100비트 페이로드 구조

```
userId(32bit) + imageId(32bit) + timestamp(24bit) + checksum(12bit) = 100bit
```

도용 이미지에서 디코딩 성공 시 페이로드에서 userId·imageId를 추출하여 출처 자동 증명.

### 5.3 디코더 입출력

- 입력: 이미지 바이트 (256×256 → 전처리: [-1,1] NCHW)
- 출력: 100비트 페이로드 (float[100] → threshold 0.5 → "0"/"1" 문자열)

### 5.4 Python → Java ONNX 전환

| | 이전 | 확정 |
|---|---|---|
| 런타임 | Python PyTorch (Cloud Run Jobs) | Java ONNX Runtime (백엔드 내장) |
| 처리 방식 | 배치 (Cloud Scheduler) | 동기 (이미지 업로드 시 인라인) |
| 인프라 | Cloud Run Jobs + cloudbuild-ml.yaml | 불필요 (백엔드 통합) |

### 5.5 결함 수정

| 수정 | 효과 |
|------|------|
| SscdEmbeddingAdapter: readAllBytes → file path API | 660MB → 601MB (-59MB) |
| TrustMark 리사이즈: Graphics2D → progressive downscale | PSNR 42.27 → 43.20 dB |

---

## 6. R3 TrustMark 내구성 검증

### 6.1 네이버 플랫폼 경유별 CRC-12 통과율

| 경로 | 이미지 크기 | Bit Accuracy | CRC 통과율 | 증거 강도 |
|------|------------|-------------|-----------|---------|
| 네이버 스마트스토어 썸네일 | 300×300px | 99.6% | **78%** | CONCLUSIVE 가능 |
| **네이버 쇼핑 피드** | **160×160px** | **96.5%** | **16%** | **CIRCUMSTANTIAL 고정** |
| 네이버 크롤러 경유 | 최대 600px | 99.8% | **83%** | CONCLUSIVE 가능 |

### 6.2 변환 강도별 CRC 통과율

| 변환 | CRC 통과율 | UI 표시 |
|------|-----------|--------|
| JPEG q≥70 | 57~88% | 정상 |
| resize / crop | 96~100% | 정상 |
| JPEG q50 | 17% | 경고 |
| **JPEG q30** | **2%** | **CIRCUMSTANTIAL** |
| crop80 + jpeg50 | 2% | CIRCUMSTANTIAL |
| resize50 + jpeg30 | 0% | CIRCUMSTANTIAL |
| **rotate_90/180** | **0%** | **CIRCUMSTANTIAL** |

### 6.3 증거 강도 판정 로직

**기존 판정:**

```
TrustMark CRC 통과 + SSCD ≥ 0.30 → CONCLUSIVE
TrustMark CRC 실패 + SSCD ≥ 0.30 → CIRCUMSTANTIAL
```

**R3 추가 규칙** — 아래 조건 해당 시 CRC 통과 여부 무관하게 CIRCUMSTANTIAL 고정:

| 조건 | 내용 | 근거 |
|------|------|------|
| A: 저해상도 | 도용 이미지 단변 < 200px | 160px 피드 CRC 16% 이하 |
| B: 회전 | EXIF 또는 SSCD 90°/180° 감지 | rotate CRC 0% |
| C: 고압축 | JPEG quality 추정 < 50 | q30 CRC 2% |

### 6.4 변환별 예상 증거 강도 매핑

| 시나리오 | 예상 강도 | CRC 통과율 |
|--------|----------|-----------|
| 원본 그대로 업로드 | CONCLUSIVE | ~100% |
| JPEG 재저장 (q≥70) | CONCLUSIVE | 57~88% |
| 네이버 스마트스토어 경유 | CONCLUSIVE | 78% |
| 네이버 크롤러 경유 | CONCLUSIVE | 83% |
| 크롭 또는 리사이즈 | CONCLUSIVE | 96~100% |
| **네이버 피드 썸네일 (160px)** | **CIRCUMSTANTIAL** | **16%** |
| 심한 JPEG 압축 (q<50) | CIRCUMSTANTIAL | 2~17% |
| **회전 (90°/180°)** | **CIRCUMSTANTIAL** | **0%** |
| 복합 변형 (리사이즈+압축) | CIRCUMSTANTIAL | 0~2% |

### 6.5 UI 필수 구현 사항

**증거 강도 뱃지:**

```typescript
export const EVIDENCE_GRADE_CONFIG = {
  CONCLUSIVE: {
    label: '결정적 증거',
    variant: 'destructive',
    icon: 'ShieldCheck',
    tooltip: 'TrustMark 워터마크가 확인되었습니다. 법적 효력이 있는 증거입니다.',
  },
  CIRCUMSTANTIAL: {
    label: '정황 증거',
    variant: 'warning',
    icon: 'ShieldAlert',
    tooltip: '이미지 유사도로만 확인된 증거입니다. 워터마크 검증이 불가했습니다.',
  },
} as const
```

**CIRCUMSTANTIAL 원인 안내:**

| 원인 코드 | UI 안내 문구 |
|---------|------------|
| `LOW_RESOLUTION` | "도용된 이미지 해상도가 낮아(160px 이하) 워터마크를 정확히 읽을 수 없습니다." |
| `ROTATION_DETECTED` | "도용된 이미지가 회전 변형되어 워터마크 검증이 불가합니다." |
| `HIGH_COMPRESSION` | "도용된 이미지가 심하게 압축되어(JPEG q<50) 워터마크 신호가 손상되었습니다." |
| `DECODE_FAILED` | "워터마크를 읽을 수 없습니다. 이미지가 크게 변형되었을 수 있습니다." |

**버튼 비활성화:** 도용 이미지 단변 < 200px → "정황 증거로 생성하기" 링크 제공

**탐지 결과 카드:**

```
┌─────────────────────────────────────┐
│ [원본 이미지]    [도용 이미지]        │
│ 유사도: 0.87  ████████████░░  87%   │
│ 증거 강도:  🔴 결정적 증거     ℹ️    │
│ 또는       🟡 정황 증거       ℹ️    │
│ [증거 생성]  [이미지 비교]           │
└─────────────────────────────────────┘
```

**PDF 출력:**

- CONCLUSIVE: "워터마크 검증 결과: 확인됨" + 소유자 ID + 이미지 ID + 등록 시각
- CIRCUMSTANTIAL: "워터마크 검증 결과: 검증 불가 ({원인})" + SSCD 유사도 + 비고

**셀러 교육 (업로드 시):**

- 고해상도(800px 이상) 이미지 등록 권장
- 모든 촬영 각도 이미지 각각 등록
- 회전 이미지 도용 시 워터마크 검증 어려울 수 있음 안내

### 6.6 관련 파일 수정 목록

| 파일 | 수정 내용 |
|------|---------|
| `EvidenceService.java` | `watermark_grade` 판정 로직에 조건 A/B/C 추가 |
| `constants/colors.ts` | `EVIDENCE_GRADE_CONFIG` 추가 |
| `DetectionResultCard.vue` | 증거 강도 뱃지 + 원인 Tooltip |
| `ImageUpload.vue` | 등록 가이드 안내 문구 |
| `EvidencePdfGenerator.java` | TrustMark 섹션 조건부 출력 |
## 7. 아키텍처 결정

v1.4~v1.5 확정 과정에서 4건의 근본 변경이 이루어졌다.

### 7.1 3계층 -> 2계층

| | 이전 (Part 3) | 확정 |
|---|---|---|
| 계층 수 | 3계층 (프론트 / 백엔드 / ML엔진) | 2계층 (프론트 / 백엔드) |
| ML 처리 | Python Cloud Run Jobs (배치) | Java ONNX Runtime (백엔드 내장) |
| 언어 | TypeScript + Java + Python | TypeScript + Java |

Python ML엔진을 완전 제거하고 Java ONNX Runtime으로 백엔드에 통합함으로써 인프라 복잡도와 콜드스타트를 대폭 줄였다.

### 7.2 SigLIP2 -> SSCD

| | 이전 | 확정 |
|---|---|---|
| 모델 | SigLIP2 (google/siglip2-base-patch16-224) | SSCD (sscd_disc_mixup, ResNet50) |
| 목적 | 범용 시각-언어 유사성 | 이미지 복제 탐지 전용 |
| Threshold | 0.80 (FPR 15.9%) | 0.30 (FPR 0.0%) |

SigLIP2는 Hard Negative에서 FPR 15.9%로 실서비스 불가 판정. SSCD는 동일 threshold에서 FPR 0%를 달성하여 확정.

### 7.3 TrustMark: Python -> Java

| | 이전 | 확정 |
|---|---|---|
| 런타임 | Python PyTorch (Cloud Run Jobs) | Java ONNX Runtime (백엔드 내장) |
| 처리 방식 | 배치 (Cloud Scheduler) | 동기 (이미지 업로드 시 인라인) |
| 인프라 | Cloud Run Jobs + cloudbuild-ml.yaml | 불필요 (백엔드에 통합) |

워터마크 인코딩 399ms, 디코딩 116ms로 동기 처리가 가능하므로 배치 아키텍처를 폐기했다.

### 7.4 ONNX 모델 배포: GCS -> Docker 레이어

| 항목 | 결정 | 근거 |
|---|---|---|
| 저장 위치 | GCS 버킷 | Git에 바이너리 미포함 |
| Docker 빌드 | Cloud Build에서 GCS -> Docker 이미지 레이어 복사 | 콜드스타트 시 별도 다운로드 없음 |
| 모델 버전 관리 | 이미지 태그 = 모델 버전 | 롤백 명확 |
| 모델 업데이트 | GCS 파일 교체 -> 새 빌드 | 모델 변경 빈도 낮음 |
| 합산 모델 크기 | SSCD ~98MB + TrustMark ~62MB = ~160MB | Docker 이미지 +160MB |

Git LFS 방식 대비 Docker 레이어 방식이 콜드스타트 제로, 버전 롤백 명확성에서 우위.

---

## 8. 메모리 프로파일링

Cloud Run 인스턴스의 메모리 예산을 확정하기 위해 단계별 RSS를 실측했다.

| 구성 | RSS |
|---|---|
| Spring Boot + DB + Flyway + SSCD (file path API) | 601 MB |
| + TrustMark 추가 delta (JUnit bare) | +310 MB |
| **예상 합산 피크** | **~911 MB** |

**Cloud Run 메모리: 2Gi (2,048MB) 확정** -- 여유 약 1.1GB.

### 주요 결함 수정

| 수정 | 효과 |
|---|---|
| SscdEmbeddingAdapter: `readAllBytes` -> file path API | 660MB -> 601MB (-59MB) |
| TrustMark 리사이즈: Graphics2D -> progressive downscale | PSNR 42.27 -> 43.20 dB |

초기에는 `readAllBytes`로 전체 이미지를 메모리에 적재하여 660MB까지 치솟았으나, file path API 전환으로 59MB를 절감했다. TrustMark의 경우 Graphics2D 단일 단계 리사이즈가 aliasing을 일으켜 PSNR이 스펙 미달이었으며, progressive downscale로 교정했다.

---

## 9. bg_swap 한계

### 9.1 원인

SSCD는 ResNet50 백본의 **글로벌 피처(GAP)** 모델이다. 배경 픽셀이 임베딩의 상당 부분을 차지하여, rembg로 실제 누끼를 적용한 뒤에도 유사도가 **0.10~0.27** 수준으로 negative 영역과 겹친다.

| 변형 | SSCD 유사도 | 판정 |
|---|---|---|
| bg_swap_white | ~0.20 | MVP 제외 |
| bg_swap_gradient | ~0.20 | MVP 제외 |

threshold 0.30 기준으로 배경 교체 변형은 구조적으로 탐지 불가하며, threshold를 낮추면 FPR이 급등하므로 분리 불가.

### 9.2 향후 대응 (MVP 이후)

1. **BiRefNet 전경 추출** -- 듀얼 임베딩 (원본 + 전경 only)으로 배경 영향 제거
2. **로컬 피처 매칭 (SuperPoint/SuperGlue)** -- 글로벌 피처 한계를 국소 키포인트 매칭으로 보완
3. **Object Detection 전처리 (YOLO/DETR)** -- 상품 영역만 크롭하여 SSCD 입력으로 사용

---

## 10. 폐기 항목

| # | 항목 | 사유 |
|---|---|---|
| D1 | SigLIP2 임베딩 | Hard Neg FPR 15.9% @0.80 -- 실서비스 불가 |
| D2 | pHash/aHash/dHash 앙상블 | Top-1 63% (SSCD 100%) -- 정확도 부족 |
| D3 | Python ML 엔진 (ml-engine/) | Java ONNX Runtime으로 대체 -- 2계층 확정 |
| D4 | DINOHash | SSCD 단독 확정으로 불필요 |
| D5 | bg_swap MVP 탐지 | SSCD 구조적 한계 -- MVP 이후 검토 |

---

## 11. PoC 실험 가이드

### 11.1 descriptor-postprocess (SSCD 디스크립터 후처리 Gap 개선)

#### 배경

SSCD 공식 README 권장 후처리:

> "For best results, we recommend additional descriptor processing when sample images from the target distribution are available. Centering (subtracting the mean) followed by L2 normalization, or whitening followed by L2 normalization, can improve accuracy."

현재 온픽은 Raw SSCD embedding을 그대로 사용 중이며, R1 기준 gap이 0.09로 기준(0.10) 미달이었다. 이 PoC는 후처리 적용 시 gap 변화를 측정한다.

#### 비교 대상 4종

| # | 전략 | 설명 |
|---|---|---|
| 1 | **Raw (현재)** | SSCD 출력 그대로 (L2 normalized) |
| 2 | **Center+L2** | 배경 분포 평균 빼기 -> L2 재정규화 |
| 3 | **Whiten+L2** | PCA 화이트닝 -> L2 재정규화 (FAISS `PCAW512,L2norm,Flat` 동일) |
| 4 | **SSCD Large** | ResNeXt101, 1024-dim (`--include-large` 옵션) |

#### 실행 명령 (PowerShell)

```powershell
cd poc/descriptor-postprocess
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt

# picsum 랜덤 이미지로 빠른 테스트
python poc_descriptor_postprocess.py --num-images 30

# 실제 상품 이미지
python poc_descriptor_postprocess.py --real-dir E:\dev\product-photos

# 네거티브 이미지 별도 지정
python poc_descriptor_postprocess.py --real-dir E:\dev\product-photos --neg-dir E:\dev\negatives

# SSCD Large 포함
python poc_descriptor_postprocess.py --real-dir E:\dev\product-photos --include-large

# Whitening 차원 축소 (512 -> 256)
python poc_descriptor_postprocess.py --real-dir E:\dev\product-photos --whiten-dim 256
```

#### 출력 4종

| 파일 | 설명 |
|---|---|
| `results/gap_summary.csv` | 후처리별 gap 요약 |
| `results/gap_detail_by_transform.csv` | 변형별 상세 gap |
| `results/gap_comparison.png` | 바 차트 |
| `results/gap_heatmap.png` | 변형 x 후처리 히트맵 |

#### 온픽 적용 시 변경점 4단계

1. **배경 분포 벡터 수집**: 서비스 이미지 N장의 SSCD 임베딩 평균/공분산 사전 계산
2. **SscdEmbeddingAdapter 수정**: `generateEmbedding()` 리턴 전 centering + L2 적용
3. **pgvector 인덱스 재생성**: 기존 임베딩도 후처리 적용 후 업데이트
4. **DinoEmbeddingAdapter에도 동일 적용 검토**

#### 추가 비용

- 런타임: 벡터 뺄셈 + 노름 계산 -- **무시 가능** (~0.01ms)
- 배경 분포: 오프라인 1회 계산, 평균 벡터 1개만 저장 (512 floats = 2KB)
- 화이트닝: 512x512 행렬 저장 필요 (1MB) -- 기동 시 로드

#### 한계

- picsum 랜덤 이미지는 실제 상품 이미지와 분포가 다름
- **실제 상품 이미지로 반드시 재검증 필요**
- 화이트닝은 배경 분포 품질에 민감 -- 서비스 데이터가 충분해야 효과적

---

### 11.2 bg-swap-detection (전체 변형 교차 검증)

#### 변형 20종

| 카테고리 | 변형 |
|---|---|
| pixel | crop_60, crop_80, crop_random, resize_50, jpeg_30, flip_h, rotate_90, rotate_180, rotate_small, aspect_ratio |
| color | brightness, color_warm |
| overlay | text_overlay, logo_overlay, padding_black, padding_white, screenshot |
| combo | combo (crop+rotate+jpeg+brightness) |
| bg_swap | bg_swap_white, bg_swap_gradient, bg_swap_indoor, bg_swap_market |

#### 실행 명령 (PowerShell)

```powershell
cd E:\dev\ownpic\poc\bg-swap-detection

# 실제 상품 이미지 (권장)
python poc_bg_swap.py --real-dir E:\dev\product-photos

# picsum 랜덤 이미지
python poc_bg_swap.py --num-products 50
```

#### 출력 구성 5가지

| # | 출력 | 설명 |
|---|---|---|
| 1 | 변형별 상세 | 20종 각각 SSCD_min, DINO_min, 승자 |
| 2 | 네거티브 분리도 | 각 모델의 neg_max, neg_p95 |
| 3 | 카테고리별 요약 | pixel/color/overlay/combo/bg_swap 그룹별 추천 모델 |
| 4 | 듀얼 파이프라인 시뮬레이션 | threshold별 recall/FPR |
| 5 | 최적 판정 로직 추천 | `SSCD>=0.30 OR DINOv2>=T` |

#### 예상 결과

- **SSCD 강점**: crop, rotate, jpeg, padding 등 pixel-level 변형
- **DINOv2 강점**: bg_swap (배경 교체)
- 듀얼 파이프라인으로 20종 전부 커버 가능

#### v3 대비 변경

- bg_swap 4종만 -> **20종 전체 변형** 생성 + 비교
- SSCD vs DINOv2 **변형별 승자 판정** 추가
- **카테고리별 gap** 분석 추가
- **듀얼 threshold 시뮬레이션** (T_dino = 0.70~0.85)
- 최종 **파이프라인 설계 자동 추천** 기능

---

### 11.3 product-matching (상품 매칭 모델 비교)

#### 모델 3종 비교

| 모델 | 특성 | 예상 |
|---|---|---|
| SigLIP2 | 시각-언어 의미 매칭 | 같은 상품 = 높은 유사도 |
| DINOv2 | 시각적 구조/텍스처 | 다른 각도 = 낮은 유사도 |
| SSCD | 복제 탐지 (baseline) | 다른 각도 = 매우 낮음 |

같은 상품의 다른 각도 이미지를 구분할 수 있는지 모델별로 비교하는 PoC이다. SigLIP2는 의미적 유사성을 포착하므로 같은 상품 다른 각도에서도 높은 유사도가 예상되며, SSCD는 복제 탐지 전용이므로 낮은 유사도가 예상된다.

#### SOP 데이터셋 다운로드

1. https://cvgl.stanford.edu/projects/lifted_struct/ 방문
2. Stanford Online Products 다운로드 (~2.9GB)
3. `cache/stanford_online_products/` 에 압축 해제

#### 실행 명령 (PowerShell)

```powershell
cd E:\dev\ownpic\poc\product-matching
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
pip install -r requirements.txt

# SOP 데이터셋
python poc_product_matching.py --source sop --num-classes 50

# 네이버 쇼핑 API
$env:NAVER_CLIENT_ID = "키"
$env:NAVER_CLIENT_SECRET = "키"
python poc_product_matching.py --source naver --naver-queries "가죽 가방" "수제 귀걸이"

# 둘 다
python poc_product_matching.py --source both --num-classes 50
```

#### 출력

| 파일 | 설명 |
|---|---|
| `results/product_matching_results.csv` | 모델별 수치 결과 |
| `results/product_matching_distributions.png` | 유사도 분포 히스토그램 |

---

## 12. 잔여 과제

| # | 과제 | 상태 | 비고 |
|---|---|---|---|
| R1 | SSCD threshold 대규모 재검증 | ✅ 완료 | 300원본+540neg, 조건부 통과 |
| R2 | 실서비스 변형 검증 + bg_swap 판단 | ✅ 완료 | v1.5 -- ISC2021 19종 + 네이버 도메인 재검증, 듀얼 파이프라인 수치 확정 |
| R3 | TrustMark 워터마크 내구성 테스트 | ✅ 완료 | 플랫폼 변환 후 디코딩 + 네이버 썸네일 경유 |
| R4 | pgvector 유사도 검색 성능 테스트 | 미착수 | MVP Phase 1 |
| R5 | Cloud Run 메모리 프로파일링 | ✅ 완료 | 2Gi 확정, 피크 ~911MB |
| R6 | Part 1~3 + Final 문서 본문 수정 | 진행중 | -- |
| R7 | Git LFS -> Docker 레이어 전략 전환 | ✅ 완료 | -- |
| R8 | bg_swap 탐지 -- BiRefNet/로컬 피처 검토 | 미착수 | MVP 이후 |
| R9 | 법무 검토: 네이버 API 약관 + 도용 이미지 저장 | 미착수 | MVP 착수 전 필수 |
