<script setup lang="ts">
import { Shield } from 'lucide-vue-next'

definePageMeta({
  layout: 'dashboard',
})

const { user } = useAuth()
</script>

<template>
  <div class="container mx-auto px-4 py-12 sm:px-6 lg:px-8">
    <div class="mx-auto max-w-4xl space-y-8">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">대시보드</h1>
        <p class="mt-1 text-muted-foreground">
          안녕하세요, {{ user?.name ?? user?.email }}님
        </p>
      </div>

      <Card>
        <CardHeader>
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
              <Shield class="h-5 w-5 text-primary" />
            </div>
            <div>
              <CardTitle>이미지 보호</CardTitle>
              <CardDescription>보호된 이미지를 관리합니다</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <p class="text-sm text-muted-foreground">
            대시보드 기능은 곧 구현됩니다. 현재 인증 시스템이 정상 동작하고 있습니다.
          </p>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
