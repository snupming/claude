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
    const { data, error } = await useFetch('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    })

    if (error.value) {
      throw createError({
        statusCode: error.value.statusCode,
        data: error.value.data,
      })
    }

    if (data.value?.user) {
      user.value = data.value.user
    }

    return data.value
  }

  async function signup(name: string, email: string, password: string) {
    const { data, error } = await useFetch('/api/auth/signup', {
      method: 'POST',
      body: { name, email, password },
    })

    if (error.value) {
      throw createError({
        statusCode: error.value.statusCode,
        data: error.value.data,
      })
    }

    if (data.value?.user) {
      user.value = data.value.user
    }

    return data.value
  }

  async function logout() {
    try {
      await $fetch('/api/auth/logout', { method: 'POST' })
    }
    catch {
      // Ignore logout errors
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
      const data = await $fetch('/api/auth/refresh', { method: 'POST' })
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
