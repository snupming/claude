interface UserInfo {
  id: string
  name?: string
  email: string
  role: string
  imageQuota?: number
  imagesUsed?: number
}

const useAuthState = () => {
  const user = useState<UserInfo | null>('auth-user', () => null)
  const isLoggedIn = computed(() => !!user.value)
  return { user, isLoggedIn }
}

export function useAuth() {
  const { user, isLoggedIn } = useAuthState()

  async function login(email: string, password: string) {
    const data = await $fetch<{ user: UserInfo }>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    })

    if (data?.user) {
      user.value = data.user
    }

    return data
  }

  async function signup(name: string, email: string, password: string) {
    const data = await $fetch<{ user: UserInfo }>('/api/auth/signup', {
      method: 'POST',
      body: { name, email, password },
    })

    if (data?.user) {
      user.value = data.user
    }

    return data
  }

  async function logout() {
    try {
      await $fetch('/api/auth/logout', { method: 'POST' })
    }
    catch (err: unknown) {
      console.warn('[logout] backend call failed, clearing client state:', err)
    }
    finally {
      user.value = null
      await navigateTo('/login')
    }
  }

  async function fetchUser() {
    try {
      const data = await $fetch<{ user: UserInfo }>('/api/auth/me')
      if (data?.user) {
        user.value = data.user
      }
    }
    catch {
      user.value = null
    }
  }

  async function refreshToken() {
    try {
      const data = await $fetch<{ user: UserInfo }>('/api/auth/refresh', { method: 'POST' })
      if (data?.user) {
        user.value = data.user
      }
      return true
    }
    catch {
      user.value = null
      return false
    }
  }

  return {
    user: readonly(user),
    isLoggedIn,
    login,
    signup,
    logout,
    fetchUser,
    refreshToken,
  }
}
