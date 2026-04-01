<script setup lang="ts">
import { Toaster as Sonner } from '@/components/ui/sonner'

const { initTheme } = useTheme()

onMounted(() => {
  initTheme()
})
</script>

<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
  <Sonner position="top-right" :duration="3000" />
</template>
