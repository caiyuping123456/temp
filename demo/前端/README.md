# 检测目录表格

用 Vue 3、Vite、Element Plus 和 vxe-table 写的树形目录表格。数据保存在浏览器 `localStorage` 里，刷新后还能接着编辑。

## 运行

```bash
npm install
npm run dev
npm test
npm run build
```

## 功能

- 新增一级目录；勾选一行或多行后新增下级。
- 删除时先检查有没有子级，有子级会弹出处理方式。
- 同级上移、下移，层级上移、下移，也可以直接拖动行排序。
- 拖拽、增删和层级变化后重新生成 `1 / 1.1 / 1.1.1` 格式的序号。
- 除序号和操作列外，其余单元格都可以直接编辑。
- 负责人、完成时间和标准器支持批量设置。
- 表格分页，切页后仍保留已勾选的行。

## 数据

`localStorage` key 为 `inspection-directory:v1`。`rows` 按树的前序保存成一维数组，父节点用 `parentId` 关联：

```json
{
  "id": "dir_a",
  "parentId": null,
  "no": "1",
  "name": "被测对象概述",
  "opType": "文字编辑",
  "domain": "通用",
  "owner": "张工",
  "dueDate": "2026-11-10",
  "standard": "",
  "remark": "项目总体说明"
}
```

## 备注

- 当前页内可以拖动排序；跨页调整请用操作列的层级按钮。
- 测试文件在 `src/utils/*.spec.js`。
