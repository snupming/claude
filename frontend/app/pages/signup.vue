<script setup lang="ts">
import { Mail, Lock, Eye, EyeOff, User } from 'lucide-vue-next'
import { extractErrorMessage } from '@/lib/utils'

definePageMeta({
  layout: false,
})

const name = ref('')
const email = ref('')
const password = ref('')
const passwordConfirm = ref('')
const showPassword = ref(false)
const agreedToTerms = ref(false)
const isLoading = ref(false)
const errorMessage = ref('')

const passwordMismatch = computed(() =>
  passwordConfirm.value.length > 0 && password.value !== passwordConfirm.value,
)

const canSubmit = computed(() =>
  name.value.trim().length > 0
  && email.value.trim().length > 0
  && password.value.length >= 8
  && password.value === passwordConfirm.value
  && agreedToTerms.value
  && !isLoading.value,
)

const { signup } = useAuth()

async function handleSignup() {
  if (!canSubmit.value) return

  errorMessage.value = ''
  isLoading.value = true

  try {
    await signup(name.value, email.value, password.value)
    await navigateTo('/dashboard')
  }
  catch (err: unknown) {
    const statusCode = (err as { statusCode?: number, data?: { statusCode?: number } }).data?.statusCode
      ?? (err as { statusCode?: number }).statusCode

    if (statusCode === 409) {
      errorMessage.value = '이미 가입된 이메일입니다. 로그인해주세요.'
    }
    else {
      errorMessage.value = extractErrorMessage(err) ?? '회원가입에 실패했습니다. 다시 시도해주세요.'
    }
  }
  finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-background p-6">
    <div class="flex w-full max-w-[420px] flex-col items-center gap-8">
      <!-- Header: Logo -->
      <div class="flex w-full items-center justify-between">
        <NuxtLink to="/" class="text-2xl font-extrabold tracking-tight bg-gradient-to-r from-cyan-500 to-cyan-300 bg-clip-text text-transparent">
          Ownpic
        </NuxtLink>
      </div>

      <!-- Title -->
      <div class="w-full space-y-2">
        <h1 class="text-2xl font-bold tracking-tight">회원가입</h1>
        <p class="text-sm text-muted-foreground">
          무료로 시작하고, 내 상품 이미지를 보호하세요
        </p>
      </div>

      <!-- Social Login -->
      <div class="w-full space-y-3">
        <Button
          variant="outline"
          class="w-full gap-2.5 rounded-lg py-5"
          disabled
        >
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="#03C75A">
            <path d="M16.273 12.845 7.376 0H0v24h7.726V11.156L16.624 24H24V0h-7.727v12.845Z" />
          </svg>
          네이버로 계속하기
        </Button>
        <Button
          variant="outline"
          class="w-full gap-2.5 rounded-lg py-5"
          disabled
        >
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="#191919">
            <path d="M12 3C6.477 3 2 6.477 2 10.5c0 2.607 1.74 4.896 4.36 6.188-.192.72-.696 2.607-.797 3.013-.124.504.185.497.388.361.16-.107 2.543-1.727 3.568-2.428.478.067.97.1 1.481.1 5.523 0 10-3.029 10-6.734S17.523 3 12 3Z" />
          </svg>
          카카오로 계속하기
        </Button>
        <p class="text-center text-xs text-muted-foreground">
          소셜 로그인은 곧 지원 예정입니다
        </p>
      </div>

      <!-- Divider -->
      <div class="flex w-full items-center gap-4">
        <Separator class="flex-1" />
        <span class="text-xs text-muted-foreground">또는</span>
        <Separator class="flex-1" />
      </div>

      <!-- Signup Form -->
      <form class="w-full space-y-4" @submit.prevent="handleSignup">
        <div v-if="errorMessage" class="rounded-lg border border-destructive/20 bg-destructive/5 p-3 text-sm text-destructive">
          {{ errorMessage }}
        </div>

        <div class="space-y-2">
          <Label for="name" class="text-sm font-medium">이름</Label>
          <div class="relative">
            <User class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="name"
              v-model="name"
              type="text"
              placeholder="이름을 입력하세요"
              class="rounded-lg pl-10 py-5"
              required
              autocomplete="name"
            />
          </div>
        </div>

        <div class="space-y-2">
          <Label for="signup-email" class="text-sm font-medium">이메일</Label>
          <div class="relative">
            <Mail class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="signup-email"
              v-model="email"
              type="email"
              placeholder="hello@example.com"
              class="rounded-lg pl-10 py-5"
              required
              autocomplete="email"
            />
          </div>
        </div>

        <div class="space-y-2">
          <Label for="signup-password" class="text-sm font-medium">비밀번호</Label>
          <div class="relative">
            <Lock class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="signup-password"
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="8자 이상 입력하세요"
              class="rounded-lg pl-10 pr-10 py-5"
              required
              minlength="8"
              autocomplete="new-password"
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

        <div class="space-y-2">
          <Label for="password-confirm" class="text-sm font-medium">비밀번호 확인</Label>
          <div class="relative">
            <Lock class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="password-confirm"
              v-model="passwordConfirm"
              :type="showPassword ? 'text' : 'password'"
              placeholder="비밀번호를 다시 입력하세요"
              class="rounded-lg pl-10 py-5"
              :class="{ 'border-destructive': passwordMismatch }"
              required
              autocomplete="new-password"
            />
          </div>
          <p v-if="passwordMismatch" class="text-xs text-destructive">
            비밀번호가 일치하지 않습니다
          </p>
        </div>

        <div class="flex items-start gap-2.5 pt-1">
          <input
            id="terms"
            v-model="agreedToTerms"
            type="checkbox"
            class="mt-0.5 h-4 w-4 rounded border-input accent-primary"
          >
          <Label for="terms" class="text-sm font-normal leading-snug text-muted-foreground">
            <NuxtLink to="/terms" class="underline hover:text-foreground">이용약관</NuxtLink>
            및
            <NuxtLink to="/privacy" class="underline hover:text-foreground">개인정보 처리방침</NuxtLink>에 동의합니다
          </Label>
        </div>

        <Button type="submit" class="w-full rounded-lg py-5 text-sm font-medium" :disabled="!canSubmit">
          <template v-if="isLoading">
            가입 중...
          </template>
          <template v-else>
            회원가입
          </template>
        </Button>
      </form>

      <!-- Footer link -->
      <p class="text-sm text-muted-foreground">
        이미 계정이 있으신가요?
        <NuxtLink to="/login" class="font-medium text-primary hover:underline">
          로그인
        </NuxtLink>
      </p>
    </div>
  </div>
</template>
