# DOCX 文档合并

按标记从副文档中截取内容，插入主文档的指定位置，并生成 Word 目录。

## 运行

需要 JDK 21 和 Maven 3.9+。

```bash
mvn test
mvn spring-boot:run
```

也可以先打包：

```bash
mvn -DskipTests package
java -jar target/doc-merge-demo-1.0.0.jar
```

打开 `http://localhost:8080/` 使用上传页面。

## 标记

- 内容区域写作 `${name-start}` 和 `${name-end}`，名称只支持字母、数字、下划线，区分大小写。
- 主文档里的 `${merge-point}` 决定插入位置；没有这个标记时追加到正文末尾。
- `${toc}` 决定目录位置；没有这个标记时目录放在开头。
- 只处理成对的标记。缺少开始或结束标记时跳过该文档，并在返回结果里给出警告。

## 接口

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查 |
| POST | `/api/documents/inspect` | 检查文档里的标记 |
| POST | `/api/documents/merge` | 上传并合并文档 |
| GET | `/api/documents/download/{fileId}` | 下载结果 |
| DELETE | `/api/documents/files/{fileId}` | 删除临时文件 |

合并接口使用 `multipart/form-data`：

- `mainFile`：主文档。
- `subFiles`：副文档，可以传多个，按表单顺序合并。
- `outputName`：输出文件名，可选。

## 示例文件

`samples/` 下有几个测试文件：

- `主文档.docx`：带目录、合并位置和附录。
- `检测范围.docx`：带图片、表格和成对标记。
- `实施计划.docx`：计划表和注意事项。
- `异常标记.docx`：故意缺结束标记，用来检查警告。
- `合并结果.docx`：合并后的示例。

重新生成：

```bash
python samples/generate_samples.py
```

后端启动后可以跑一遍冒烟测试：

```bash
python samples/smoke_test.py
```

## 说明

- 只支持 `.docx`，不处理 `.doc`、`.docm` 和 PDF。
- 临时文件默认放在系统临时目录，保留 2 小时。
- 目录使用 Word 原生 TOC 域。页码需要打开 Word 后按 `Ctrl+A`、`F9` 更新。
- 表格、图片、段落样式和编号会尽量保留；图表、SmartArt、OLE 对象没有完整处理。
