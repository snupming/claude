<script setup lang="ts">
import { Shield } from 'lucide-vue-next'
</script>

<template>
  <footer class="border-t bg-muted/30">
    <div class="container mx-auto px-4 py-12 sm:px-6 lg:px-8">
      <div class="grid grid-cols-1 gap-8 md:grid-cols-4">
        <!-- Brand -->
        <div class="space-y-3">
          <div class="flex items-center gap-2">
            <Shield class="h-6 w-6 text-primary" />
            <span class="text-lg font-bold">Ownpic</span>
          </div>
          <p class="text-sm text-muted-foreground">
            내 상품 이미지를 보호하고<br>도용을 자동으로 탐지합니다.
          </p>
        </div>

        <!-- Product -->
        <div class="space-y-3">
          <h4 class="text-sm font-semibold">서비스</h4>
          <ul class="space-y-2 text-sm text-muted-foreground">
            <li><NuxtLink to="/#features" class="hover:text-foreground">기능 소개</NuxtLink></li>
            <li><NuxtLink to="/pricing" class="hover:text-foreground">요금제</NuxtLink></li>
          </ul>
        </div>

        <!-- Legal -->
        <div class="space-y-3">
          <h4 class="text-sm font-semibold">법적 고지</h4>
          <ul class="space-y-2 text-sm text-muted-foreground">
            <li><NuxtLink to="/privacy" class="hover:text-foreground">개인정보 처리방침</NuxtLink></li>
            <li><NuxtLink to="/terms" class="hover:text-foreground">이용약관</NuxtLink></li>
          </ul>
        </div>

        <!-- Contact -->
        <div class="space-y-3">
          <h4 class="text-sm font-semibold">문의</h4>
          <ul class="space-y-2 text-sm text-muted-foreground">
            <li>support@ownpic.com</li>
          </ul>
        </div>
      </div>

      <Separator class="my-8" />

      <p class="text-center text-xs text-muted-foreground">
        &copy; {{ new Date().getFullYear() }} Ownpic. All rights reserved.
      </p>
    </div>
  </footer>
</template>
