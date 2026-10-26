<script setup>
import DirectoryPanel from '@/components/DirectoryPanel.vue'
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="brand">
        <div class="brand-mark">目</div>
        <div class="brand-text">
          <h1>检测目录管理</h1>
          <p>树形目录 · 拖拽排序 · 自动编号 · 本地持久化</p>
        </div>
      </div>
      <div class="header-tags">
        <span class="stack-tag">Vue 3</span>
        <span class="stack-tag">Element Plus</span>
        <span class="stack-tag">Vite</span>
      </div>
    </header>

    <main class="app-main">
      <DirectoryPanel />
    </main>

    <footer class="app-footer">
      数据以「一维数组」形式保存在浏览器 localStorage，刷新或下次打开会自动恢复。
    </footer>
  </div>
</template>
