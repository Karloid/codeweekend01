class SimpleGuySearch(val checkNextMonster: Boolean) : Strategy {
    private var maxTurns: Int = 0
    private var turnsToGold: Int = 0
    private var index: Int = 0
    lateinit var w: World

    private var result = StrategyTurns()

    private var bestResult: StrategyTurns? = null

    override fun toString(): String {
        return "SimpleGuySearch(checkNextMonster=$checkNextMonster)"
    }

    override fun calcResult(w: World): StrategyTurns {
        val searchAttempts = 1
        repeat(searchAttempts) { index ->
            result = StrategyTurns()
            internalCalcResult(w.deepCopy())

            val currentResult = result

            if (bestResult == null || bestResult!!.heroUnit.gold < currentResult.heroUnit.gold) {
                loge1 { "\n!!!! New best result gold=${bestResult?.heroUnit?.gold} -> ${currentResult.heroUnit.gold}  i=${index}\n" }
                bestResult = currentResult
            } else {
                System.err.print(".")
                //     loge1 { "Current result gold=${currentResult.heroEnd.gold} worse than best=${bestResult!!.heroEnd.gold}" }
            }
        }
        System.err.println("")
        return bestResult!!
    }

    private fun internalCalcResult(w: World) {
        var currentW = w

        maxTurns = w.num_turns
        turnsToGold = (w.num_turns * Math.random()).toInt()
        repeat(w.num_turns) { index ->

            val turn = move(currentW, index)
            result.moves.add(turn)

            currentW = updateWorld(turn, currentW)
        }

        result.heroUnit = currentW.heroUnit
        loge { "Result w.heroUnit=${currentW.heroUnit} stats=${currentW.hero}" }
    }

    private fun move(world: World, index: Int): Move {
        w = world
        this.index = index
        loge { "#$index w.heroUnit=${w.heroUnit} stats=${w.hero}" }

        return simpleMove()
    }

    private fun simpleMove(): Move {
        val monsterToAttack = getMonsterToAttack()

        if (monsterToAttack != null) {
            val inRangeOfAttach = inRangeOfAttack(w.heroUnit.pos, monsterToAttack.pos, w.heroUnit.range)

            if (inRangeOfAttach) {
                val msg =
                    "Attack monster=${monsterToAttack} hp=${w.getHp(monsterToAttack)} myPower=${w.heroUnit.power} monsterHp=${w.getHp(monsterToAttack)} myLevel=${w.heroUnit.level} myExp=${w.heroUnit.exp} myGold=${w.heroUnit.gold}"
                loge { "#$index Attack $monsterToAttack msg=$msg" }
                return Move.Attack(monsterToAttack.id).apply {
                    comment = msg
                }
            }

            var positionToMove = monsterToAttack.pos

            val myRange = w.heroUnit.range
            val gridSize = 20
            var lowestDamage = Int.MAX_VALUE
            var bestAttackOpps = 0

            repeat(gridSize) { yi ->
                repeat(gridSize) { xi ->

                    val x = (xi - gridSize / 2) * myRange / gridSize + monsterToAttack.x
                    val y = (yi - gridSize / 2) * myRange / gridSize + monsterToAttack.y

                    // if in circle
                    val candidate = Point2D(x.toInt(), y.toInt())
                    if (inBounds(candidate) && inRangeOfAttack(candidate, monsterToAttack.pos, myRange)) {

                        val damage = w.monsters.fastSumBy { monster ->
                            if (monster.pos.euclidianDistance2(candidate) < monster.range * monster.range) {
                                monster.attack.toInt()
                            } else {
                                0
                            }
                        }                           // fast
                        val attackOpps = w.monsters.count { monster ->
                            monster.pos.euclidianDistance2(candidate) < monster.range * monster.range
                        }

                        if (damage < lowestDamage ||
                            (damage == lowestDamage && attackOpps > bestAttackOpps)
                        ) {
                            lowestDamage = damage
                            positionToMove = candidate
                            bestAttackOpps = attackOpps
                        }
                    }
                }
            }

            val moveVector = positionToMove.copy().sub(w.heroUnit.pos)
            val length = moveVector.length()

            if (length * length > w.heroUnit.speed * w.heroUnit.speed) {
                moveVector.mult(w.heroUnit.speed / length)
            }

            moveVector.add(w.heroUnit.pos).let { newPos ->
                val msg =
                    "Move to monster=${monsterToAttack}  myPower=${w.heroUnit.power} monsterHp=${w.getHp(monsterToAttack)}" +
                            " myLevel=${w.heroUnit.level} myExp=${w.heroUnit.exp} myGold=${w.heroUnit.gold}"

                loge { "#$index Move to $newPos msg=$msg" }
                return Move.Travel(newPos).apply {
                    comment = msg
                }
            }

        } else {
            return Move.Travel(w.heroUnit.pos).apply {
                comment = "Stay in place"
            }
        }
    }

    private fun inBounds(candidate: Point2D): Boolean {
        return candidate.x >= 0 && candidate.x <= w.width && candidate.y >= 0 && candidate.y <= w.height
    }

    var prevMonster: MonsterScore? = null

    data class MonsterScore(
        val monster: Monster,
        val score: Double
    )

    private fun getMonsterToAttack(): Monster? {

        if (prevMonster?.monster?.let { w.getHp(monster = it) > 0 } == true) {
            loge { "#$index OLD attack=${prevMonster}}" }
            return prevMonster!!.monster
        }

        val scoreFun: (Monster) -> Double? = scoreFun@{ monster ->
            var turnsToKill = 0L

            val moveTurns = ((monster.pos.euclidianDistance(w.heroUnit.pos) - w.heroUnit.range) / w.heroUnit.speed).toInt().coerceAtLeast(0)

            turnsToKill += moveTurns

            var turnsToKillHp = w.getHp(monster) / w.heroUnit.power

            if (w.getHp(monster) % w.heroUnit.power > 0) turnsToKillHp++

            turnsToKill += turnsToKillHp

            if (turnsToKill > w.num_turns) {
                return@scoreFun 0.0
            }

            val measure = if (w.num_turns < this.turnsToGold) monster.gold else monster.exp
            var goldPerTurn = measure.toFloat() / turnsToKill

            val nextMonster = findNextMonster(monster, turnsToKill.toInt())
            if (nextMonster != null) {
                val turnsToKillNext = calcTurnsToKill(nextMonster, fromPos = monster.pos)
                val measureNext = if (w.num_turns < this.turnsToGold) nextMonster.gold else monster.exp
                val totalExp = measure + measureNext.toFloat()

                val totalTurns = turnsToKill + turnsToKillNext

                var totalPenaltyByTurn = 1.0
                repeat(totalTurns.toInt()) {
                    totalPenaltyByTurn *= 0.9
                }

                return@scoreFun totalExp / totalTurns * totalPenaltyByTurn
            }

            var penaltyByTurn = 1.0
            repeat(turnsToKill.toInt()) {
                penaltyByTurn *= 0.9
            }

            goldPerTurn * penaltyByTurn
        }

        val monsterScores = w.monsters.mapNotNull { monster ->
            if (w.getHp(monster) <= 0) return@mapNotNull null

            MonsterScore(monster, scoreFun(monster)!!)
        }.toMutableList()

        monsterScores.sortByDescending { it.score }

        val resss = monsterScores.getOrNull((3 * random.nextFloat()).toInt())

        prevMonster = resss

        loge { "#$index NEW attack=${prevMonster}}" }
        return resss?.monster
    }

    private fun findNextMonster(monster: Monster, turnsShift: Int): Monster? {
        if (checkNextMonster.not()) {
            return null
        }

        val candidates = w.monsters

        var bestMonster: Monster? = null
        var bestScore: Double? = null
        val scores = candidates.fastForEach { candidate ->

            if (candidate == monster || w.getHp(candidate) <= 0) {
                return@fastForEach
            }

            var turnsToKill = calcTurnsToKill(monster, fromPos = monster.pos)

            if (turnsToKill > w.num_turns - turnsShift) {
                return@fastForEach
            }


            val measure = if (w.num_turns < this.turnsToGold) monster.gold else monster.exp
            var goldPerTurn = measure.toFloat() / turnsToKill

            var penaltyByTurn = 1.0
            repeat(turnsToKill) {
                penaltyByTurn *= 0.9
            }

            var score = goldPerTurn * penaltyByTurn

            if (bestScore == null || bestScore!! < score) {
                bestScore = score
                bestMonster = candidate
            }
        }

        return bestMonster
    }

    private fun calcTurnsToKill(monster: Monster, fromPos: Point2D = w.heroUnit.pos): Int {
        var turnsToKill = 0

        val moveTurns = ((monster.pos.euclidianDistance(fromPos) - w.heroUnit.range) / w.heroUnit.speed).toInt().coerceAtLeast(0)

        turnsToKill += moveTurns

        var turnsToKillHp = w.getHp(monster) / w.heroUnit.power

        if (w.getHp(monster) % w.heroUnit.power > 0) turnsToKillHp++

        turnsToKill += turnsToKillHp.toInt()
        return turnsToKill
    }
}
