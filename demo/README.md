# Demo 合集

本目录存放两份笔试题 demo，互相独立、可分别运行：

| 目录 | 对应需求 | 技术栈 |
| --- | --- | --- |
| [`frontend/`](./frontend) | 前端 demo：树形目录表格 | Vite + Vue 3 + Element Plus |
| [`backend/`](./backend) | 后端 demo：DOCX 标识符截取与合并 | Spring Boot + docx4j |

## 快速开始

### 前端

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

实现要点：树形目录（一维数组 + `parentId`）、自动编号 `1 / 1.1 / 1.1.1`、新增一级/下级、删除（含子级时二选一：一并删除或子级提升）、同级上下移、层级升降、拖拽排序（左右拖动改层级）、单元格编辑、批量设置字段、分页、localStorage 持久化。详见 [frontend/README.md](./frontend/README.md)。

### 后端

```bash
cd backend
mvn spring-boot:run   # http://localhost:8080
```

打开页面后上传 `backend/samples/主文档.docx` 与两个正常副文档即可体验；`samples/合并结果示例.docx` 是已生成的结果预览。实现要点：识别 `${tag-start}` / `${tag-end}` 标记（支持 Word 跨 run 拆分）、提取区域内容（段落/表格/图片/样式）、按顺序合并到主文档（支持 `${merge-point}`）、自动生成 TOC 域与标题层级、临时文件下载。接口与调用示例见 [backend/README.md](./backend/README.md)。

## 目录说明

```
demo/
├─ frontend/   # 前端 demo（Vue 3 + Element Plus，含 Vitest 单测）
└─ backend/    # 后端 demo（Spring Boot + docx4j，含 JUnit / MockMvc 测试）
```

> 两个 demo 按题目要求分别实现，未做前后端联调；前端的目录数据保存在浏览器本地，后端通过 REST 接口独立演示文档合并。
