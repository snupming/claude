import { ref, computed, watch, nextTick, reactive, toRef, toRefs } from 'vue'

// Expose Vue APIs as globals (Nuxt auto-imports these)
Object.assign(globalThis, { ref, computed, watch, nextTick, reactive, toRef, toRefs })

// Mock Nuxt's useState globally
const stateStore = new Map<string, any>()

;(globalThis as any).useState = function useState<T>(key: string, init?: () => T) {
  if (!stateStore.has(key)) {
    stateStore.set(key, ref(init ? init() : undefined))
  }
  return stateStore.get(key)
}

// $fetch is mocked per-test via vi.stubGlobal

// Helper to reset state between tests
;(globalThis as any).__resetState = function() {
  stateStore.clear()
}
