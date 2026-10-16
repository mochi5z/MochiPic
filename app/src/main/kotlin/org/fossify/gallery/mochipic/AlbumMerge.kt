package org.fossify.gallery.mochipic

import java.util.Locale

/**
 * MochiPic: アルバム一覧への反映（マージ）。差分読み込み・変化の通知・自分で書き込んだファイルの反映は、すべてここを通る。
 *
 * - [rebuilt] のキーはフォルダの場所（大文字小文字は区別しない）。値が null ならそのフォルダを一覧から外す
 * - [rebuilt] にないフォルダは前の一覧のまま残す（順番も保つ）
 * - 新しいフォルダは最後に足す（表示の並べ替えは一覧の側で行う）
 * - 同じ [rebuilt] を何度当てても結果は同じ
 */
object AlbumMerge {

    fun key(path: String) = path.trimEnd('/').lowercase(Locale.ROOT)

    fun <T> merge(cached: List<T>, rebuilt: Map<String, T?>, pathOf: (T) -> String): ArrayList<T> {
        val updates = HashMap<String, T?>()
        rebuilt.forEach { (path, item) -> updates[key(path)] = item }

        val result = ArrayList<T>(cached.size + updates.size)
        val seen = HashSet<String>()
        for (item in cached) {
            val k = key(pathOf(item))
            if (!seen.add(k)) continue  // 同じフォルダが 2 回入っていたら 1 つにする
            if (updates.containsKey(k)) {
                updates[k]?.let { result.add(it) }
            } else {
                result.add(item)
            }
        }
        updates.forEach { (k, item) ->
            if (item != null && seen.add(k)) result.add(item)
        }
        return result
    }
}
