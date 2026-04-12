type Theme = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'ownpic-theme'

export function useTheme() {
  const theme = useState<Theme>('theme', () => 'system')
  const resolved = useState<'light' | 'dark'>('resolved-theme', () => 'light')

  function getSystemTheme(): 'light' | 'dark' {
    if (import.meta.server) return 'light'
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }

  function applyTheme(t: Theme) {
    if (import.meta.server) return
    const effective = t === 'system' ? getSystemTheme() : t
    resolved.value = effective
    document.documentElement.setAttribute('data-theme', effective)
  }

  function setTheme(t: Theme) {
    theme.value = t
    if (import.meta.client) {
      localStorage.setItem(STORAGE_KEY, t)
    }
    applyTheme(t)
  }

  function toggleTheme() {
    // light → dark → system → light
    const order: Theme[] = ['light', 'dark', 'system']
    const idx = order.indexOf(theme.value)
    const next = order[(idx + 1) % order.length] ?? 'light'
    setTheme(next)
  }

  function initTheme() {
    if (import.meta.server) return
    const stored = localStorage.getItem(STORAGE_KEY) as Theme | null
    const initial = stored ?? 'system'
    theme.value = initial
    applyTheme(initial)

    // 시스템 테마 변경 감지 — onScopeDispose 로 해제 등록
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => {
      if (theme.value === 'system') {
        applyTheme('system')
      }
    }
    mq.addEventListener('change', onChange)
    onScopeDispose(() => mq.removeEventListener('change', onChange))
  }

  return { theme, resolved, setTheme, toggleTheme, initTheme }
}
