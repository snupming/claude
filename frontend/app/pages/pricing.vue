<script setup lang="ts">
definePageMeta({
  layout: 'default',
})
</script>

<template>
  <div class="flex min-h-[50vh] items-center justify-center">
    <p class="text-muted-foreground">
      요금제는
      <NuxtLink to="/#pricing" class="font-medium text-primary hover:underline">메인 페이지</NuxtLink>에서
      확인하세요.
    </p>
  </div>
</template>
