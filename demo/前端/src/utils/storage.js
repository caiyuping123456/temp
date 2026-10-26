import { STORAGE_KEY } from '@/constants/directory'

const isStorageAvailable = () => {
  try {
    return typeof window !== 'undefined' && !!window.localStorage
  } catch (error) {
    return false
  }
}

/**
 * 读取本地快照。
 * 返回 { rows, page, pageSize, updatedAt } 或 null（首次进入 / 数据损坏）。
 */
export function loadSnapshot() {
  if (!isStorageAvailable()) return null

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return null

    const snapshot = JSON.parse(raw)
    if (!snapshot || !Array.isArray(snapshot.rows)) return null

    return snapshot
  } catch (error) {
    console.warn('[directory-demo] 读取本地数据失败，已回退到示例数据', error)
    return null
  }
}

/** 保存快照：rows 始终是「一维先序数组」，每行带 no 字段。 */
export function saveSnapshot({ rows, page, pageSize }) {
  if (!isStorageAvailable()) return

  try {
    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        version: 1,
        rows,
        page,
        pageSize,
        updatedAt: new Date().toISOString(),
      }),
    )
  } catch (error) {
    console.warn('[directory-demo] 保存本地数据失败（可能是存储空间不足）', error)
  }
}

export function clearSnapshot() {
  if (!isStorageAvailable()) return
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch (error) {
    console.warn('[directory-demo] 清空本地数据失败', error)
  }
}
