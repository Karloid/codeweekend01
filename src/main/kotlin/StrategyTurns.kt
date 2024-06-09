data class StrategyTurns(
    val moves: MutableList<Move> = mutableListOf<Move>()
) {

    @Transient
    lateinit var heroUnit : HeroUnit
}

sealed class Move {
    var comment = ""

    data class Travel(
        val type: String = "move",
        val target_x: Int,
        val target_y: Int
    ) : Move() {
        constructor(toPos: Point2D) : this("move", toPos.x, toPos.y)
    }

    data class Attack(
        val type: String = "attack",
        val target_id: Int
    ) : Move() {
        constructor(targetId: Int) : this("attack", targetId)
    }
}
