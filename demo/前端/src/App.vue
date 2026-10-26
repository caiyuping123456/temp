<script setup>
import DirectoryPanel from '@/components/DirectoryPanel.vue'

const navItems = ['项目概览', '检测目录', '任务分工', '报告归档']
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="topbar-inner">
        <div class="product-name">
          <span class="product-code">JC</span>
          <span>检测项目管理系统</span>
        </div>
        <nav class="topnav" aria-label="主导航">
          <span
            v-for="item in navItems"
            :key="item"
            class="topnav-item"
            :class="{ 'is-active': item === '检测目录' }"
          >
            {{ item }}
          </span>
        </nav>
        <div class="user-entry">
          <span class="user-dot"></span>
          <span>张工</span>
        </div>
      </div>
    </header>

    <main class="app-main">
      <div class="breadcrumb">项目管理 / 检测目录</div>
      <div class="page-heading">
        <div>
          <h1>检测目录</h1>
          <p>维护检测项目目录、负责人、完成时间和标准器信息。</p>
        </div>
        <span class="project-meta">项目编号：JC-2026-018</span>
      </div>

      <DirectoryPanel />
    </main>

    <footer class="app-footer">
      <span>目录数据自动保存在当前浏览器</span>
      <span>目录管理</span>
    </footer>
  </div>
</template>
