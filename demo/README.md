# 检测目录与文档合并

这里放两个笔试题，前后端分开，可以单独运行。

## 前端

Vue 3 + Vite + Element Plus + vxe-table，实现检测目录的树形表格和本地保存。

```bash
cd 前端
npm install
npm run dev
```

测试和构建：

```bash
npm test
npm run build
```

## 后端

Spring Boot + docx4j，按 `${tag-start}` / `${tag-end}` 截取副文档内容，插入主文档并生成 Word 目录。

```bash
cd 后端
mvn test
mvn spring-boot:run
```

启动后访问 `http://localhost:8080/`。示例文件和接口说明见 `后端/README.md`。
