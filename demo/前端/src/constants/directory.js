import { createNode, renumber } from '../utils/tree'

/** localStorage 键名，版本化便于后续结构升级。 */
export const STORAGE_KEY = 'openharness-directory-demo:v1'

/** 默认每页条数（需求原型备注：默认 10 行，超过后分页）。 */
export const DEFAULT_PAGE_SIZE = 10
export const PAGE_SIZES = [10, 20, 50]

/** 表格中可编辑的列（操作列、序号列除外）。 */
export const EDITABLE_FIELDS = [
  { key: 'name', label: '目录名称', minWidth: 280, placeholder: '请输入目录名称' },
  { key: 'opType', label: '操作类型', width: 130, placeholder: '—' },
  { key: 'domain', label: '检测领域', width: 120, placeholder: '—' },
  { key: 'owner', label: '子项目/目录负责人', width: 170, placeholder: '—' },
  { key: 'dueDate', label: '要求完成时间', width: 150, placeholder: '例如 2026-12-31' },
  { key: 'standard', label: '标准器', width: 130, placeholder: '—' },
  { key: 'remark', label: '备注', minWidth: 160, placeholder: '—' },
]

/** 工具栏上「点一下弹窗输入内容」的快捷设置按钮。 */
export const QUICK_SET_FIELDS = [
  {
    key: 'owner',
    label: '负责人',
    button: '设置负责人',
    placeholder: '请输入负责人姓名，将写入所有选中行',
    maxlength: 30,
  },
  {
    key: 'dueDate',
    label: '要求完成时间',
    button: '设置完成日期',
    placeholder: '例如 2026-12-31，将写入所有选中行',
    maxlength: 30,
  },
  {
    key: 'standard',
    label: '标准器',
    button: '设置标准器',
    placeholder: '请输入标准器名称，将写入所有选中行',
    maxlength: 60,
  },
]

/**
 * 首次打开时的示例数据，对应原型第二张表。
 * 返回一维先序数组（保存格式与运行时格式一致）。
 */
export function createSeedRows() {
  const overview = createNode(null, { name: '被测对象概述', opType: '文字编辑', remark: '项目总体说明' })
  const content = createNode(null, { name: '检测内容' })
  const electric1 = createNode(content.id, { name: '电气系统' })
  const hvac = createNode(content.id, { name: '暖通系统' })
  const data = createNode(null, { name: '检测数据' })
  const electric2 = createNode(data.id, { name: '电气系统' })
  const itemA = createNode(electric2.id, { name: '项目A', owner: '张工', dueDate: '2026-11-30' })
  const itemB = createNode(electric2.id, { name: '项目B', owner: '李工', dueDate: '2026-12-15' })

  return renumber([overview, content, electric1, hvac, data, electric2, itemA, itemB])
}
