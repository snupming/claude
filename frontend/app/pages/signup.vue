<script setup lang="ts">
import { Shield, Mail, Lock, Eye, EyeOff, User } from 'lucide-vue-next'

definePageMeta({
  layout: 'default',
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

async function handleSignup() {
  if (!canSubmit.value) return

  errorMessage.value = ''
  isLoading.value = true

  try {
    const { data, error } = await useFetch('/api/auth/signup', {
      method: 'POST',
      body: {
        name: name.value,
        email: email.value,
        password: password.value,
      },
    })

    if (error.value) {
      const detail = error.value.data?.detail
      if (error.value.statusCode === 409) {
        errorMessage.value = '이미 가입된 이메일입니다. 로그인해주세요.'
      }
      else {
        errorMessage.value = detail ?? '회원가입에 실패했습니다. 다시 시도해주세요.'
      }
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
        <CardTitle class="text-2xl">회원가입</CardTitle>
        <CardDescription>
          50장까지 무료로 이미지를 보호하세요
        </CardDescription>
      </CardHeader>

      <CardContent class="space-y-6">
        <!-- Social signup buttons -->
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

        <!-- Signup form -->
        <form class="space-y-4" @submit.prevent="handleSignup">
          <div v-if="errorMessage" class="rounded-md border border-destructive/20 bg-destructive/5 p-3 text-sm text-destructive">
            {{ errorMessage }}
          </div>

          <div class="space-y-2">
            <Label for="name">이름</Label>
            <div class="relative">
              <User class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="name"
                v-model="name"
                type="text"
                placeholder="이름을 입력하세요"
                class="pl-10"
                required
                autocomplete="name"
              />
            </div>
          </div>

          <div class="space-y-2">
            <Label for="signup-email">이메일</Label>
            <div class="relative">
              <Mail class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="signup-email"
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
            <Label for="signup-password">비밀번호</Label>
            <div class="relative">
              <Lock class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="signup-password"
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="8자 이상 입력하세요"
                class="pl-10 pr-10"
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
            <Label for="password-confirm">비밀번호 확인</Label>
            <div class="relative">
              <Lock class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="password-confirm"
                v-model="passwordConfirm"
                :type="showPassword ? 'text' : 'password'"
                placeholder="비밀번호를 다시 입력하세요"
                class="pl-10"
                :class="{ 'border-destructive': passwordMismatch }"
                required
                autocomplete="new-password"
              />
            </div>
            <p v-if="passwordMismatch" class="text-xs text-destructive">
              비밀번호가 일치하지 않습니다
            </p>
          </div>

          <div class="flex items-start gap-2">
            <input
              id="terms"
              v-model="agreedToTerms"
              type="checkbox"
              class="mt-1 h-4 w-4 rounded border-input accent-primary"
            >
            <Label for="terms" class="text-sm font-normal leading-snug text-muted-foreground">
              <NuxtLink to="/terms" class="underline hover:text-foreground">이용약관</NuxtLink>
              및
              <NuxtLink to="/privacy" class="underline hover:text-foreground">개인정보 처리방침</NuxtLink>에 동의합니다
            </Label>
          </div>

          <Button type="submit" class="w-full" :disabled="!canSubmit">
            <template v-if="isLoading">
              가입 중...
            </template>
            <template v-else>
              회원가입
            </template>
          </Button>
        </form>
      </CardContent>

      <CardFooter class="justify-center">
        <p class="text-sm text-muted-foreground">
          이미 계정이 있으신가요?
          <NuxtLink to="/login" class="font-medium text-primary hover:underline">
            로그인
          </NuxtLink>
        </p>
      </CardFooter>
    </Card>
  </div>
</template>
