<script setup lang="ts">
import { Shield } from 'lucide-vue-next'

const { isLoggedIn, logout } = useAuth()
</script>

<template>
  <header class="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
    <div class="container mx-auto flex h-16 items-center justify-between px-4 sm:px-6 lg:px-8">
      <!-- Logo -->
      <NuxtLink to="/" class="flex items-center gap-2">
        <Shield class="h-7 w-7 text-primary" />
        <span class="text-xl font-bold tracking-tight">Ownpic</span>
      </NuxtLink>

      <!-- Navigation -->
      <nav class="hidden items-center gap-8 md:flex">
        <NuxtLink
          to="/#features"
          class="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          기능
        </NuxtLink>
        <NuxtLink
          to="/pricing"
          class="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          요금제
        </NuxtLink>
      </nav>

      <!-- Auth buttons -->
      <div class="flex items-center gap-3">
        <template v-if="isLoggedIn">
          <NuxtLink to="/dashboard">
            <Button variant="ghost" size="sm">
              대시보드
            </Button>
          </NuxtLink>
          <Button variant="outline" size="sm" @click="logout">
            로그아웃
          </Button>
        </template>
        <template v-else>
          <NuxtLink to="/login">
            <Button variant="ghost" size="sm">
              로그인
            </Button>
          </NuxtLink>
          <NuxtLink to="/signup">
            <Button size="sm">
              무료로 시작하기
            </Button>
          </NuxtLink>
        </template>
      </div>
    </div>
  </header>
</template>
