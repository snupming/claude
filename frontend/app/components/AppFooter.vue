<script setup lang="ts">
</script>

<template>
  <footer class="border-t bg-muted/30">
    <div class="mx-auto max-w-7xl px-6 py-12">
      <div class="flex flex-col gap-8 md:flex-row md:justify-between">
        <!-- Brand -->
        <div class="space-y-3">
          <span class="text-lg font-extrabold tracking-tight bg-gradient-to-r from-cyan-500 to-cyan-300 bg-clip-text text-transparent">
            Ownpic
          </span>
          <p class="max-w-xs text-sm text-muted-foreground">
            내 상품 이미지를 보호하고 도용을 자동으로 탐지하는 AI 기반 이미지 보호 서비스
          </p>
        </div>

        <!-- Links -->
        <div class="flex gap-16">
          <div class="space-y-3">
            <h4 class="text-sm font-semibold">서비스</h4>
            <ul class="space-y-2 text-sm text-muted-foreground">
              <li><NuxtLink to="/#features" class="hover:text-foreground transition-colors">가이드</NuxtLink></li>
              <li><NuxtLink to="/pricing" class="hover:text-foreground transition-colors">요금제</NuxtLink></li>
            </ul>
          </div>
          <div class="space-y-3">
            <h4 class="text-sm font-semibold">지원</h4>
            <ul class="space-y-2 text-sm text-muted-foreground">
              <li><NuxtLink to="/contact" class="hover:text-foreground transition-colors">문의하기</NuxtLink></li>
              <li><NuxtLink to="/terms" class="hover:text-foreground transition-colors">이용약관</NuxtLink></li>
              <li><NuxtLink to="/privacy" class="hover:text-foreground transition-colors">개인정보처리방침</NuxtLink></li>
            </ul>
          </div>
        </div>
      </div>

      <Separator class="my-8" />

      <p class="text-center text-xs text-muted-foreground">
        &copy; {{ new Date().getFullYear() }} Ownpic. All rights reserved.
      </p>
    </div>
  </footer>
</template>
