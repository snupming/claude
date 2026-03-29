/**
 * 스크롤 기반 fade-in 애니메이션 composable
 *
 * SSR 하이드레이션 이슈 방지:
 * - SSR 시점에는 .fade-in 요소가 opacity: 1 (정상 보임)
 * - 클라이언트 하이드레이션 후 [data-fade-ready] 속성이 추가되면서
 *   뷰포트 밖의 요소만 opacity: 0으로 전환, 스크롤 시 fade-in
 * - 이미 뷰포트 안에 있는 요소는 즉시 .visible 클래스 부여
 */
export function useFadeIn() {
  onMounted(() => {
    nextTick(() => {
      const fadeEls = document.querySelectorAll('.fade-in')

      // 뷰포트 안에 있는 요소는 즉시 visible 처리
      fadeEls.forEach((el) => {
        const rect = el.getBoundingClientRect()
        if (rect.top < window.innerHeight + 50) {
          el.classList.add('visible')
        }
      })

      // fade-ready 활성화 → 뷰포트 밖 요소만 opacity: 0 적용
      document.documentElement.setAttribute('data-fade-ready', '')

      // 뷰포트 밖 요소를 위한 IntersectionObserver
      const observer = new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            if (entry.isIntersecting) {
              entry.target.classList.add('visible')
              observer.unobserve(entry.target)
            }
          }
        },
        { threshold: 0.05, rootMargin: '50px' },
      )

      fadeEls.forEach((el) => {
        if (!el.classList.contains('visible')) {
          observer.observe(el)
        }
      })
    })
  })
}
