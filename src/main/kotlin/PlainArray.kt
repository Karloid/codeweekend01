class PlainArray<T> internal constructor(val cellsWidth: Int, val cellsHeight: Int, init: (Int) -> T) {

    @JvmField
    val array: Array<T>

    init {
        array = Array(cellsWidth * cellsHeight, init as (Int) -> Any) as Array<T>
    }

    inline internal operator fun get(x: Int, y: Int): T? {
        return if (!inBounds(x, y)) {
            null
        } else {
            array[y * cellsWidth + x]
        }
    }

    inline fun getFast(x: Int, y: Int): T {
        return array[y * cellsWidth + x]
    }

    internal fun add(x: Int, y: Int, `val`: T) {
        if (!inBounds(x, y)) {
            return
        }
        array[y * cellsWidth + x] = `val`
    }

    internal operator fun set(x: Int, y: Int, `val`: T) {
        if (!inBounds(x, y)) {
            return
        }
        array[y * cellsWidth + x] = `val`
    }

    internal fun setFast(x: Int, y: Int, `val`: T) {
        array[y * cellsWidth + x] = `val`
    }

    inline fun inBounds(x: Int, y: Int): Boolean {
        return !(x < 0 || x >= cellsWidth || y < 0 || y >= cellsHeight)
    }

    fun fori(block: (x: Int, y: Int, v: T) -> Unit) {
        for (y in 0 until cellsHeight) {
            for (x in 0 until cellsWidth) {
                block(x, y, getFast(x, y))
            }
        }
    }

    fun foriInSquare(center: Point2D, radius: Int, block: (x: Int, y: Int, v: T) -> Unit) {
        for (y in (center.y - radius).coerceAtLeast(0) until (center.y + radius).coerceAtMost(cellsHeight)) {
            for (x in (center.x - radius).coerceAtLeast(0) until (center.x + radius).coerceAtMost(cellsWidth)) {
                block(x, y, getFast(x, y))
            }
        }
    }


    fun set(pos: Point2D, value: T) {
        set(pos.x, pos.y, value)
    }

    fun get(pos: Point2D): T? {
        return get(pos.x.toInt(), pos.y.toInt())
    }

    fun getFastNoRound(pos: Point2D): T {
        return getFast(pos.x.toInt(), pos.y.toInt())
    }

    fun getNoRound(pos: Point2D): T? {
        return get(pos.x.toInt(), pos.y.toInt())
    }

    fun setFastNoRound(pos: Point2D, value: T) {
        setFast(pos.x, pos.y, value)
    }

    fun clear() {
        @Suppress("UNCHECKED_CAST") val array = array as Array<T?>
        for (i in array.indices) {
            array[i] = null
        }
    }

    inline fun filter(crossinline function: (T) -> Boolean): List<T> {
        val result = mutableListOf<T>()
        fori { x, y, v ->
            if (function(v)) {
                result.add(v)
            }
        }

        return result
    }

    inline fun filterWithPoints(crossinline function: (T) -> Boolean): List<Pair<Point2D, T>> {
        val result = mutableListOf<Pair<Point2D, T>>()
        fori { x, y, v ->
            if (function(v)) {
                result.add(Point2D(x, y) to v)
            }
        }

        return result
    }
}
