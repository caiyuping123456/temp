let counter = 0

/**
 * 生成足够唯一的行 id（无需服务端协调）。
 * 使用时间戳 + 自增序号 + 随机串，避免同一毫秒内批量新增时重复。
 */
export function createRowId(prefix = 'node') {
  counter += 1
  const time = Date.now().toString(36)
  const seq = counter.toString(36)
  const random = Math.random().toString(36).slice(2, 6)
  return `${prefix}_${time}_${seq}${random}`
}
