<script setup lang="ts">
import { Shield, Mail, Lock, Eye, EyeOff } from 'lucide-vue-next'

definePageMeta({
  layout: 'default',
})

const email = ref('')
const password = ref('')
const showPassword = ref(false)
const isLoading = ref(false)
const errorMessage = ref('')

async function handleLogin() {
  errorMessage.value = ''
  isLoading.value = true

  try {
    const { data, error } = await useFetch('/api/auth/login', {
      method: 'POST',
      body: { email: email.value, password: password.value },
    })

    if (error.value) {
      errorMessage.value = error.value.data?.detail ?? '로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.'
      return
    }

    await navigateTo('/dashboard')
  }
  catch {
    errorMessage.value = '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.'
  }
  finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-[calc(100vh-8rem)] items-center justify-center px-4 py-12">
    <Card class="w-full max-w-md">
      <CardHeader class="space-y-2 text-center">
        <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-lg bg-primary/10">
          <Shield class="h-6 w-6 text-primary" />
        </div>
        <CardTitle class="text-2xl">로그인</CardTitle>
        <CardDescription>
          Ownpic 계정으로 로그인하세요
        </CardDescription>
      </CardHeader>

      <CardContent class="space-y-6">
        <!-- Social login buttons -->
        <div class="grid grid-cols-2 gap-3">
          <Button
            variant="outline"
            class="gap-2"
            disabled
          >
            <svg class="h-4 w-4" viewBox="0 0 24 24" fill="#03C75A">
              <path d="M16.273 12.845 7.376 0H0v24h7.726V11.156L16.624 24H24V0h-7.727v12.845Z" />
            </svg>
            네이버
          </Button>
          <Button
            variant="outline"
            class="gap-2"
            disabled
          >
            <svg class="h-4 w-4" viewBox="0 0 24 24" fill="#191919">
              <path d="M12 3C6.477 3 2 6.477 2 10.5c0 2.607 1.74 4.896 4.36 6.188-.192.72-.696 2.607-.797 3.013-.124.504.185.497.388.361.16-.107 2.543-1.727 3.568-2.428.478.067.97.1 1.481.1 5.523 0 10-3.029 10-6.734S17.523 3 12 3Z" />
            </svg>
            카카오
          </Button>
        </div>

        <p class="text-center text-xs text-muted-foreground">
          소셜 로그인은 곧 지원 예정입니다
        </p>

        <Separator />

        <!-- Email login form -->
        <form class="space-y-4" @submit.prevent="handleLogin">
          <div v-if="errorMessage" class="rounded-md border border-destructive/20 bg-destructive/5 p-3 text-sm text-destructive">
            {{ errorMessage }}
          </div>

          <div class="space-y-2">
            <Label for="email">이메일</Label>
            <div class="relative">
              <Mail class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="email"
                v-model="email"
                type="email"
                placeholder="hello@example.com"
                class="pl-10"
                required
                autocomplete="email"
              />
            </div>
          </div>

          <div class="space-y-2">
            <Label for="password">비밀번호</Label>
            <div class="relative">
              <Lock class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="password"
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="비밀번호를 입력하세요"
                class="pl-10 pr-10"
                required
                autocomplete="current-password"
              />
              <button
                type="button"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                @click="showPassword = !showPassword"
              >
                <EyeOff v-if="showPassword" class="h-4 w-4" />
                <Eye v-else class="h-4 w-4" />
              </button>
            </div>
          </div>

          <Button type="submit" class="w-full" :disabled="isLoading">
            <template v-if="isLoading">
              로그인 중...
            </template>
            <template v-else>
              로그인
            </template>
          </Button>
        </form>
      </CardContent>

      <CardFooter class="justify-center">
        <p class="text-sm text-muted-foreground">
          계정이 없으신가요?
          <NuxtLink to="/signup" class="font-medium text-primary hover:underline">
            회원가입
          </NuxtLink>
        </p>
      </CardFooter>
    </Card>
  </div>
</template>
