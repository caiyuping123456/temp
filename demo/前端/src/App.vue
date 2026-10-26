<script setup>
import DirectoryPanel from '@/components/DirectoryPanel.vue'
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="topbar-inner">
        <span class="product-name">检测目录</span>
        <span class="storage-note">数据保存在当前浏览器</span>
      </div>
    </header>

    <main class="app-main">
      <div class="page-heading">
        <h1>检测目录</h1>
        <p>维护目录层级、负责人、完成时间和标准器。</p>
      </div>

      <DirectoryPanel />
    </main>
  </div>
</template>
