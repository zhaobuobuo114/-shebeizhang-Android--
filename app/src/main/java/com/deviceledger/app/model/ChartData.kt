package com.deviceledger.app.model

/** 扇形图的一段 */
data class PieSlice(
    val n: String,   // 名称
    val v: Double,   // 数值
    val c: String    // 颜色
)

/** 最多显示多少段，其余合并为"其他" */
private const val MAX_SLICE = 6

object ChartData {

    /** 按设备类型聚合。byAmount = true 按金额，false 按台数 */
    fun slices(list: List<DeviceItem>, byAmount: Boolean): List<PieSlice> {
        val keys = ArrayList<String>()
        val vals = ArrayList<Double>()

        for (item in list) {
            val k = item.category
            val v = if (byAmount) item.price else 1.0
            val idx = keys.indexOf(k)
            if (idx < 0) {
                keys.add(k)
                vals.add(v)
            } else {
                vals[idx] = vals[idx] + v
            }
        }

        val out = ArrayList<PieSlice>()
        for (i in keys.indices) {
            val kd = findKind(keys[i])
            out.add(PieSlice(kd.label, vals[i], kd.colorHex))
        }
        // 与鸿蒙版一致：只按数值降序，不做二次排序
        out.sortWith(compareByDescending<PieSlice> { it.v })

        if (out.size > MAX_SLICE) {
            var rest = 0.0
            for (i in MAX_SLICE - 1 until out.size) {
                rest += out[i].v
            }
            val head = ArrayList<PieSlice>(out.subList(0, MAX_SLICE - 1))
            head.add(PieSlice("其他", rest, "#9CA3AF"))
            return head
        }
        return out
    }

    fun total(slices: List<PieSlice>): Double {
        var sum = 0.0
        for (s in slices) sum += s.v
        return sum
    }
}
