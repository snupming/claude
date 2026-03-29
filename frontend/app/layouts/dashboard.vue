<script setup lang="ts">
const sidebarCollapsed = useState('sidebar-collapsed', () => false)
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader />
    <div class="flex flex-1">
      <!-- Sidebar (desktop) + Bottom Bar (mobile) -->
      <AppSidebar :collapsed="sidebarCollapsed" @toggle="sidebarCollapsed = !sidebarCollapsed" />

      <!-- Main Content: md+ 사이드바 margin, mobile 하단바 padding -->
      <main
        class="flex-1 overflow-y-auto pb-20 transition-all duration-200 md:pb-0"
        :class="sidebarCollapsed ? 'md:ml-16' : 'md:ml-60'"
      >
        <slot />
      </main>
    </div>
  </div>
</template>
