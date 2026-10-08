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

    /**
     * 各段占整圆的百分比（整数），保证加在一起精确等于 100。
     *
     * 逐段各自四舍五入是不行的：三段各 33.3% 会算出 33/33/33 = 99%，
     * 图例那一列看着就少了 1%。这里用「最大余数法」：先给每段取其下整，
     * 再把剩下的名额按小数部分从大到小依次补 1，和始终是 100。
     *
     * 另外保底：凡是数值不为 0 的段至少给 1%，避免出现「占了份额却写着 0%」。
     * 与鸿蒙版 ChartData.percents 是同一套算法。
     */
    fun percents(slices: List<PieSlice>): IntArray {
        val n = slices.size
        val out = IntArray(n)
        if (n == 0) return out
        var total = 0.0
        for (s in slices) total += s.v
        if (total <= 0.0) return out

        val raw = DoubleArray(n)
        val order = ArrayList<Int>(n)
        var sum = 0
        for (i in 0 until n) {
            val exact = slices[i].v / total * 100.0
            raw[i] = exact
            // 数值不为 0 就至少留 1%，否则这一段在图例里会是"占着份额却显示 0%"
            var whole = Math.floor(exact).toInt()
            if (whole == 0 && slices[i].v > 0.0) whole = 1
            out[i] = whole
            sum += whole
            order.add(i)
        }

        var diff = 100 - sum
        if (diff > 0) {
            // 名额富余：小数部分越大的越该进位
            order.sortByDescending { raw[it] - Math.floor(raw[it]) }
            var k = 0
            while (diff > 0) {
                val i = order[k % n]
                out[i] += 1
                diff--
                k++
            }
        } else if (diff < 0) {
            // 保底提 1% 把总数顶过 100 了：从数值最大的段里往回扣
            order.sortByDescending { slices[it].v }
            var k = 0
            var guard = 0
            while (diff < 0 && guard < n * 100) {
                val i = order[k % n]
                if (out[i] > 1) {
                    out[i] -= 1
                    diff++
                }
                k++
                guard++
            }
        }
        return out
    }
}
