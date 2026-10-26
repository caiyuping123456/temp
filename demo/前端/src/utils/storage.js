import { LEGACY_STORAGE_KEY, STORAGE_KEY } from '@/constants/directory'

const isStorageAvailable = () => {
  try {
    return typeof window !== 'undefined' && !!window.localStorage
  } catch (error) {
    return false
  }
}

export function loadSnapshot() {
  if (!isStorageAvailable()) return null

  try {
    let raw = window.localStorage.getItem(STORAGE_KEY)
    let migrated = false

    if (!raw) {
      raw = window.localStorage.getItem(LEGACY_STORAGE_KEY)
      migrated = Boolean(raw)
    }

    if (!raw) return null
    const snapshot = JSON.parse(raw)
    if (!snapshot || !Array.isArray(snapshot.rows)) return null

    if (migrated) {
      saveSnapshot(snapshot)
      window.localStorage.removeItem(LEGACY_STORAGE_KEY)
    }

    return snapshot
  } catch (error) {
    console.warn('[directory-demo] 读取本地数据失败', error)
    return null
  }
}

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
    console.warn('[directory-demo] 保存本地数据失败', error)
  }
}

export function clearSnapshot() {
  if (!isStorageAvailable()) return
  try {
    window.localStorage.removeItem(STORAGE_KEY)
    window.localStorage.removeItem(LEGACY_STORAGE_KEY)
  } catch (error) {
    console.warn('[directory-demo] 清空本地数据失败', error)
  }
}
