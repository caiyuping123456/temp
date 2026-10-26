# 检测目录与文档合并

本目录保存两道笔试题的实现，前后端相互独立。

| 目录 | 内容 | 技术栈 |
| --- | --- | --- |
| `前端/` | 树形目录表格 | Vue 3、Vite、Element Plus、vxe-table |
| `后端/` | DOCX 内容提取与合并 | Spring Boot、docx4j |

## 前端

```bash
cd 前端
npm install
npm run dev
npm test
npm run build
```

目录数据保存在浏览器 `localStorage` 中，结构为一维数组，通过 `parentId` 表示层级。

## 后端

```bash
cd 后端
mvn test
mvn spring-boot:run
```

启动后访问 `http://localhost:8080/`，上传主文档和副文档即可合并。接口说明见 `后端/README.md`。
