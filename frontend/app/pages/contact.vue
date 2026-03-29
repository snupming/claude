<script setup lang="ts">
import { MessageSquare } from 'lucide-vue-next'
</script>

<template>
  <div class="mx-auto max-w-2xl px-6 py-20 text-center">
    <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10">
      <MessageSquare class="h-7 w-7 text-primary" />
    </div>
    <h1 class="mt-6 text-3xl font-bold tracking-tight">문의하기</h1>
    <p class="mt-4 text-muted-foreground">
      서비스 이용 중 궁금한 점이 있으시면 카카오톡 오픈채팅으로 문의해 주세요.
    </p>
    <div class="mt-8">
      <a
        href="https://open.kakao.com/o/swb05uki"
        target="_blank"
        rel="noopener noreferrer"
      >
        <Button size="lg" class="gap-2 rounded-lg px-6 py-2.5 text-sm font-medium">
          <MessageSquare class="h-4 w-4" />
          카카오톡 문의
        </Button>
      </a>
    </div>
    <p class="mt-4 text-sm text-muted-foreground">
      운영시간: 평일 10:00 ~ 18:00
    </p>
  </div>
</template>
