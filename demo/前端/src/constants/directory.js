import { createNode, renumber } from '../utils/tree'

export const STORAGE_KEY = 'inspection-directory:v1'
export const LEGACY_STORAGE_KEY = 'directory-demo:v1'

export const DEFAULT_PAGE_SIZE = 10
export const PAGE_SIZES = [10, 20, 50]

export const EDITABLE_FIELDS = [
  { key: 'name', label: '目录名称', minWidth: 280, placeholder: '请输入目录名称' },
  { key: 'opType', label: '操作类型', width: 120, placeholder: '文字编辑' },
  { key: 'domain', label: '检测领域', width: 120, placeholder: '请输入领域' },
  { key: 'owner', label: '子项目/目录负责人', width: 170, placeholder: '请输入负责人' },
  { key: 'dueDate', label: '要求完成时间', width: 150, placeholder: '2026-12-31' },
  { key: 'standard', label: '标准器', width: 130, placeholder: '请输入标准器' },
  { key: 'remark', label: '备注', minWidth: 180, placeholder: '请输入备注' },
]

export const QUICK_SET_FIELDS = [
  {
    key: 'owner',
    label: '负责人',
    placeholder: '请输入负责人姓名',
    maxlength: 30,
  },
  {
    key: 'dueDate',
    label: '要求完成时间',
    placeholder: '例如 2026-12-31',
    maxlength: 30,
  },
  {
    key: 'standard',
    label: '标准器',
    placeholder: '请输入标准器名称',
    maxlength: 60,
  },
]

export function createSeedRows() {
  const overview = createNode(null, {
    name: '被测对象概述',
    opType: '文字编辑',
    domain: '通用',
    owner: '张工',
    dueDate: '2026-11-10',
    remark: '项目总体说明',
  })
  const content = createNode(null, {
    name: '检测内容',
    opType: '目录',
    domain: '通用',
    owner: '李工',
    dueDate: '2026-11-20',
  })
  const electric = createNode(content.id, {
    name: '电气系统',
    opType: '目录',
    domain: '电气',
    owner: '王工',
    dueDate: '2026-11-25',
  })
  const power = createNode(electric.id, {
    name: '电源输入',
    opType: '数据记录',
    domain: '电气',
    owner: '王工',
    dueDate: '2026-11-28',
    standard: '数字万用表',
  })
  const control = createNode(electric.id, {
    name: '控制回路',
    opType: '数据记录',
    domain: '电气',
    owner: '赵工',
    dueDate: '2026-12-02',
    standard: '示波器',
  })
  const hvac = createNode(content.id, {
    name: '暖通系统',
    opType: '目录',
    domain: '暖通',
    owner: '陈工',
    dueDate: '2026-12-05',
  })
  const airflow = createNode(hvac.id, {
    name: '风量测试',
    opType: '数据记录',
    domain: '暖通',
    owner: '陈工',
    dueDate: '2026-12-08',
    standard: '风速仪',
  })
  const data = createNode(null, {
    name: '检测数据',
    opType: '目录',
    domain: '通用',
    owner: '李工',
    dueDate: '2026-12-10',
  })
  const temperature = createNode(data.id, {
    name: '温度记录',
    opType: '数据记录',
    domain: '环境',
    owner: '周工',
    dueDate: '2026-12-12',
    standard: '温湿度记录仪',
  })
  const pressure = createNode(data.id, {
    name: '压力记录',
    opType: '数据记录',
    domain: '环境',
    owner: '周工',
    dueDate: '2026-12-15',
    standard: '压力表',
  })
  const conclusion = createNode(null, {
    name: '检测结论',
    opType: '文字编辑',
    domain: '通用',
    owner: '张工',
    dueDate: '2026-12-18',
  })
  const exceptions = createNode(conclusion.id, {
    name: '异常项说明',
    opType: '文字编辑',
    domain: '通用',
    owner: '张工',
    dueDate: '2026-12-20',
    remark: '如有异常项，需附处理建议',
  })

  return renumber([
    overview,
    content,
    electric,
    power,
    control,
    hvac,
    airflow,
    data,
    temperature,
    pressure,
    conclusion,
    exceptions,
  ])
}
