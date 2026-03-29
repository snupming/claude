<script setup lang="ts">
import { Menu, X } from 'lucide-vue-next'

const { isLoggedIn, logout } = useAuth()
const mobileOpen = ref(false)
</script>

<template>
  <header class="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
    <div class="mx-auto flex h-16 max-w-7xl items-center justify-between px-6">
      <!-- Logo -->
      <NuxtLink to="/" class="flex items-center gap-1.5">
        <span class="text-2xl font-extrabold tracking-tight bg-gradient-to-r from-cyan-500 to-cyan-300 bg-clip-text text-transparent">
          Ownpic
        </span>
      </NuxtLink>

      <!-- Desktop Nav -->
      <nav class="hidden items-center gap-6 md:flex">
        <NuxtLink
          to="/#features"
          class="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          가이드
        </NuxtLink>
        <NuxtLink
          to="/pricing"
          class="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          요금제
        </NuxtLink>
        <NuxtLink
          to="/contact"
          class="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          문의
        </NuxtLink>
      </nav>

      <!-- Desktop Auth -->
      <div class="hidden items-center gap-3 md:flex">
        <template v-if="isLoggedIn">
          <NuxtLink to="/dashboard">
            <Button variant="ghost" size="sm" class="text-sm font-medium">
              대시보드
            </Button>
          </NuxtLink>
          <Button variant="outline" size="sm" class="text-sm font-medium" @click="logout">
            로그아웃
          </Button>
        </template>
        <template v-else>
          <NuxtLink to="/signup">
            <Button size="sm" class="rounded-lg bg-primary px-5 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90">
              무료 체험
            </Button>
          </NuxtLink>
          <NuxtLink to="/login">
            <Button variant="ghost" size="sm" class="text-sm font-medium">
              로그인
            </Button>
          </NuxtLink>
        </template>
      </div>

      <!-- Mobile menu button -->
      <button
        class="inline-flex items-center justify-center rounded-md p-2 text-muted-foreground md:hidden"
        @click="mobileOpen = !mobileOpen"
      >
        <X v-if="mobileOpen" class="h-5 w-5" />
        <Menu v-else class="h-5 w-5" />
      </button>
    </div>

    <!-- Mobile Nav -->
    <div v-if="mobileOpen" class="border-t md:hidden">
      <div class="space-y-1 px-6 py-4">
        <NuxtLink
          to="/#features"
          class="block rounded-md px-3 py-2 text-sm font-medium text-muted-foreground hover:bg-accent hover:text-foreground"
          @click="mobileOpen = false"
        >
          가이드
        </NuxtLink>
        <NuxtLink
          to="/pricing"
          class="block rounded-md px-3 py-2 text-sm font-medium text-muted-foreground hover:bg-accent hover:text-foreground"
          @click="mobileOpen = false"
        >
          요금제
        </NuxtLink>
        <NuxtLink
          to="/contact"
          class="block rounded-md px-3 py-2 text-sm font-medium text-muted-foreground hover:bg-accent hover:text-foreground"
          @click="mobileOpen = false"
        >
          문의
        </NuxtLink>

        <Separator class="my-2" />

        <template v-if="isLoggedIn">
          <NuxtLink
            to="/dashboard"
            class="block rounded-md px-3 py-2 text-sm font-medium text-foreground"
            @click="mobileOpen = false"
          >
            대시보드
          </NuxtLink>
          <button
            class="block w-full rounded-md px-3 py-2 text-left text-sm font-medium text-muted-foreground hover:bg-accent hover:text-foreground"
            @click="logout(); mobileOpen = false"
          >
            로그아웃
          </button>
        </template>
        <template v-else>
          <NuxtLink
            to="/signup"
            class="block rounded-md bg-primary px-3 py-2 text-center text-sm font-medium text-primary-foreground"
            @click="mobileOpen = false"
          >
            무료 체험
          </NuxtLink>
          <NuxtLink
            to="/login"
            class="block rounded-md px-3 py-2 text-center text-sm font-medium text-muted-foreground"
            @click="mobileOpen = false"
          >
            로그인
          </NuxtLink>
        </template>
      </div>
    </div>
  </header>
</template>
