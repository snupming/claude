import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

// Re-export Vue APIs that Nuxt auto-imports
export { ref, computed, onMounted, onUnmounted, watch }

// Mock useState — behaves like ref but with key-based global state
const stateStore = new Map<string, any>()

export function useState<T>(key: string, init?: () => T) {
  if (!stateStore.has(key)) {
    stateStore.set(key, ref(init ? init() : undefined))
  }
  return stateStore.get(key)
}

// Reset state between tests
export function __resetState() {
  stateStore.clear()
}

// $fetch is mocked per test via vi.stubGlobal
export const $fetch = globalThis.$fetch
