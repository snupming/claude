<script setup lang="ts">
</script>

<template>
  <!-- 프로덕션 동일: border-t, py-10, px-8 -->
  <footer class="border-t border-border py-10 px-8 max-md:px-4">
    <div class="flex items-start justify-between max-md:flex-col max-md:gap-6 max-md:text-center">
      <!-- Brand -->
      <div>
        <div class="mb-1.5 bg-gradient-to-r from-primary to-cyan-300 bg-clip-text text-lg font-extrabold text-transparent">
          Ownpic
        </div>
        <div class="text-xs text-muted-foreground">
          이커머스 셀러를 위한 이미지 보호 플랫폼
        </div>
      </div>

      <!-- Links — 프로덕션 동일 -->
      <div class="flex gap-12 max-md:flex-col max-md:gap-6 max-md:items-center">
        <div class="flex flex-col gap-2">
          <div class="text-xs font-semibold text-foreground mb-1">서비스</div>
          <NuxtLink to="/blog?category=guide" class="text-xs text-muted-foreground hover:text-foreground transition-colors">
            가이드
          </NuxtLink>
          <NuxtLink to="/blog" class="text-xs text-muted-foreground hover:text-foreground transition-colors">
            블로그
          </NuxtLink>
        </div>
        <div class="flex flex-col gap-2">
          <div class="text-xs font-semibold text-foreground mb-1">지원</div>
          <a
            href="https://open.kakao.com/o/swb05uki"
            target="_blank"
            rel="noopener noreferrer"
            class="text-xs text-muted-foreground hover:text-foreground transition-colors"
          >
            문의하기
          </a>
          <NuxtLink to="/terms" class="text-xs text-muted-foreground hover:text-foreground transition-colors">
            이용약관
          </NuxtLink>
          <NuxtLink to="/privacy" class="text-xs text-muted-foreground hover:text-foreground transition-colors">
            개인정보처리방침
          </NuxtLink>
        </div>
      </div>
    </div>

    <div class="mt-8 pt-6 border-t border-border text-center text-xs text-muted-foreground">
      &copy; {{ new Date().getFullYear() }} Ownpic. All rights reserved.
    </div>
  </footer>
</template>
