import utils.Point2DF

var MAX_GENERATION_SIZE = 50

class BeamSearch(
    val penaltyForOldMovementEnabled: Boolean = false,
) : Strategy {
    private var stepsCalculated: Int = 0

    lateinit var accessGrid: PlainArray<Double>
    private var gridStep: Int = 0

    override fun toString(): String {
        return "BeamSearch(steps=$stepsCalculated penaltyForOldMovementEnabled=$penaltyForOldMovementEnabled)"
    }

    override fun calcResult(w: World): StrategyTurns {
        prepareAccess(w)


        val steps = ArrayDeque<Step>()
        val finalSteps = ArrayDeque<Step>()
        val nextSteps = ArrayDeque<Step>()

        steps.add(
            Step(
                w = w.deepCopy(),
                moves = emptyList(),
                score = 0.0,
                killCount = 0
            )
        )

        var indexNotAccurate = -1

        while (true) {
            indexNotAccurate++
            val gameProgress = indexNotAccurate / w.num_turns.toFloat()
            steps.fastForEach { step ->
                if (step.w.num_turns == 0) {
                    finalSteps.add(step)
                    return@fastForEach
                }

                mutateStep(step, nextSteps)
            }

            if (nextSteps.isEmpty()) {
                break
            }
            steps.clear()

            if (nextSteps.size > MAX_GENERATION_SIZE) {
                nextSteps.sortByDescending {
                    // generation score fun    EVALUATION FUNCTION
                    var attacksBonus = it.moves.count { it is Move.Attack }

                    // attacks are pointless
                    if (it.killCount > 0 && it.w.heroUnit.gold == 0L) {
                        attacksBonus = 0
                    }


                    val closestPointInAccessValue = accessGrid.getFast(
                        (it.w.heroUnit.pos.x / gridStep).toInt(),
                        (it.w.heroUnit.pos.y / gridStep).toInt()
                    )

                    var bonusAccessGrid = 0f
                    if (closestPointInAccessValue > 0f && it.killCount == 0 && indexNotAccurate < 43) {
                        bonusAccessGrid += closestPointInAccessValue.toFloat() / 1000000000f
                    }

                    var penaltyForOldMovement = 0f
                    if (this.penaltyForOldMovementEnabled) {
                        val oldHeroPos = Point2D(it.w.start_x, it.w.start_y)
                        var index = 0
                        var maxIndex = it.moves.count{it is Move.Travel}
                        it.moves.fastForEach { prevMove ->
                            if (prevMove is Move.Travel) {
                                index++
                                oldHeroPos.set(prevMove.target_x, prevMove.target_y)
                                if (oldHeroPos.euclidianDistance(it.w.heroUnit.pos).toFloat() < w.heroUnit.speed)  {
                                    penaltyForOldMovement += 1 / 1000f * (index.toFloat() / maxIndex)
                                }
                            }
                        }

                    }

                    it.w.heroUnit.exp * (1 - gameProgress) * 10 +
                            it.w.heroUnit.gold +
                            attacksBonus - it.w.heroUnit.fatigue / 10000f +
                            bonusAccessGrid - penaltyForOldMovement

                }
                while (nextSteps.size > MAX_GENERATION_SIZE) {
                    nextSteps.removeLast()
                }
            }

            steps.addAll(nextSteps)
            nextSteps.clear()
        }


        val result = finalSteps.maxBy { it.w.heroUnit.gold }

        val resultingMoves = result.moves.toMutableList()

        //debugResultingMoves(resultingMoves, w.deepCopy())

        return StrategyTurns(
            moves = resultingMoves,
        ).also { it.heroUnit = result.w.heroUnit }
    }

    private fun prepareAccess(w: World) {
        gridStep = w.heroUnit.speed.div(3).toInt().coerceIn(5, (w.width.toInt() / 30).coerceAtLeast(6))
        accessGrid = PlainArray(w.width / gridStep + 1, w.height / gridStep + 1) { Double.MIN_VALUE }
        val gridFatigue = PlainArray(w.width / gridStep + 1, w.height / gridStep + 1) { 0.0 }

        val tmpPoint = Point2D(0, 0)

        val startPoints = mutableListOf<Point2D>()

        accessGrid.fori { x, y, v ->
            val canBeAttackedByMonsters = w.monsters.any { m ->
                tmpPoint.set(x * gridStep, y * gridStep)
                m.attack > 0 && m.pos.euclidianDistance2(tmpPoint) < m.range * m.range
            }

            if (canBeAttackedByMonsters) {
                gridFatigue.set(x, y, 1.0)
                return@fori
            }

            val canAttackMontser = w.monsters.firstOrNull { m ->
                tmpPoint.set(x * gridStep, y * gridStep)
                stepsToKill(w.heroUnit.power, w.getHp(m)) <= w.num_turns &&
                        tmpPoint.euclidianDistance2(m.pos) < w.heroUnit.range * w.heroUnit.range
            }

            if (canAttackMontser != null) {
                accessGrid.set(x, y, canAttackMontser.gold.toDouble())

                startPoints.add(Point2D(x, y))
            }
        }

        // fill grid with dejkstra from startPoints, keep at minimum points where gridFatigue > 0.0

        val queue = ArrayDeque<Point2D>()
        startPoints.fastForEach { start ->
            queue.add(start)
        }

        while (queue.isNotEmpty()) {
            val point = queue.removeFirst()
            val x = point.x.toInt()
            val y = point.y.toInt()

            val fatigue = gridFatigue.getFast(x, y)
            if (fatigue > 0.0) {
                continue
            }

            val newValue = accessGrid.getFast(x, y) - 1

            val left = x - 1
            val right = x + 1
            val top = y - 1
            val bottom = y + 1

            if (left >= 0) {
                val leftValue = accessGrid.getFast(left, y)
                if (gridFatigue.getFast(left, y) == 0.0 && leftValue < newValue) {
                    accessGrid.set(left, y, newValue)
                    queue.add(Point2D(left, y))
                }
            }

            if (right < accessGrid.cellsWidth) {
                val rightValue = accessGrid.getFast(right, y)
                if (gridFatigue.getFast(right, y) == 0.0 && rightValue < newValue) {
                    accessGrid.set(right, y, newValue)
                    queue.add(Point2D(right, y))
                }
            }

            if (top >= 0) {
                val topValue = accessGrid.getFast(x, top)
                if (gridFatigue.getFast(x, top) == 0.0 && topValue < newValue) {
                    accessGrid.set(x, top, newValue)
                    queue.add(Point2D(x, top))
                }
            }

            if (bottom < accessGrid.cellsHeight) {
                val bottomValue = accessGrid.getFast(x, bottom)
                if (gridFatigue.getFast(x, bottom) == 0.0 && bottomValue < newValue) {
                    accessGrid.set(x, bottom, newValue)
                    queue.add(Point2D(x, bottom))
                }
            }
        }
    }

    private fun mutateStep(step: Step, nextSteps: ArrayDeque<Step>) {
        // mutate step
        val prevMove = step.moves.lastOrNull()
        val stepW = step.w

        // keep attack no other variants
        if (prevMove is Move.Attack) {
            val monster = step.w.monsters.firstOrNull { it.id == prevMove.target_id }
            if (monster != null && step.w.getHp(monster) > 0) {

                val newTurn = Move.Attack(monster.id)
                addNewStep(newTurn, step, nextSteps)
                return
            } else {
                step.killCount++
            }
        }

        val maxSpeed = stepW.heroUnit.speed
        val rotateVector = Point2DF(maxSpeed.toInt(), 0)
        val totalDirs = 10
        repeat(totalDirs) { index ->
            // move in all directions if in bounds in circle
            rotateVector.set(maxSpeed.toInt(), 0)
            val rotate = rotateVector.rotate(index * 2 * Math.PI / totalDirs)
            var moveVector = rotate.toVec2()
            if (moveVector.length() > maxSpeed) {
                rotate.length(rotate.length() * 0.9f)
                moveVector = rotate.toVec2()
            }
            val newPosition = stepW.heroUnit.pos.copy() + moveVector
            if (inBounds(stepW, newPosition)) {
                addNewStep(Move.Travel(newPosition), step, nextSteps)
            }
        }

        // go directly to closest good monster

        val monstersToCome = mutableListOf<Monster>()

        val closestMonsters = ArrayDeque<Monster>()
        stepW.monsters.fastForEach { monster ->
            val monsterHp = stepW.getHp(monster)
            if (monsterHp <= 0 || stepsToKill(stepW.heroUnit.power, monsterHp) > stepW.num_turns) {
                return@fastForEach
            }

            closestMonsters.add(monster)
            closestMonsters.sortBy { it.pos.euclidianDistance2(stepW.heroUnit.pos) }
            if (closestMonsters.size > 5) {
                closestMonsters.removeLast()
            }
        }
        monstersToCome.addAll(closestMonsters)

        monstersToCome.fastForEach { monster ->
            var moveVector = monster.pos.copy().sub(stepW.heroUnit.pos)

            // limit moveVector length by speed
            val length = moveVector.length()
            if (length > stepW.heroUnit.speed) {
                moveVector = moveVector.toFloat().normalize().length(stepW.heroUnit.speed.toDouble()).toVec2NoRound()
            }

            val travelPos = moveVector + stepW.heroUnit.pos

            addNewStep(Move.Travel(travelPos), step, nextSteps)
        }


        // try attack if nearby
        stepW.monsters.fastForEach { monster ->
            if (!inRangeOfAttack(stepW.heroUnit.pos, monster.pos, stepW.heroUnit.range) || stepW.getHp(monster) <= 0) {
                return@fastForEach
            }

            if (stepsToKill(stepW.heroUnit.power, stepW.getHp(monster)) > stepW.num_turns) {
                return@fastForEach
            }


            loge { "monster to attack ${monster} hp=${stepW.getHp(monster)}" }
            addNewStep(Move.Attack(monster.id), step, nextSteps)
        }

        // go through access grid top 3 points
        val pointsToVisitAtGrid = ArrayDeque<Point2D>() // grid coordinates

        val tmp = Point2D(0, 0)
        if (step.killCount == 0) {
            accessGrid.fori { x, y, v ->
                if (v == Double.MIN_VALUE) {
                    return@fori
                }
                if (stepW.heroUnit.pos.euclidianDistance2(tmp.set(x * gridStep, y * gridStep)) > maxSpeed * maxSpeed) {
                    return@fori
                }
                pointsToVisitAtGrid.add(Point2D(x, y))
                if (pointsToVisitAtGrid.size > 3) {
                    pointsToVisitAtGrid.sortByDescending {
                        accessGrid.getFast(it.x, it.y)
                    }
                    while (pointsToVisitAtGrid.size > 3) {
                        pointsToVisitAtGrid.removeLast()
                    }
                }
            }
        }

        pointsToVisitAtGrid.fastForEach { gridPoint ->
            val x = gridPoint.x.toInt()
            val y = gridPoint.y.toInt()
            val pos = tmp.set(x * gridStep, y * gridStep)
            addNewStep(Move.Travel(pos), step, nextSteps)
        }
    }

    private fun stepsToKill(power: Long, hp: Long): Long {
        return (hp + power - 1) / power
    }

    private fun inBounds(stepW: World, newPosition: Point2D): Boolean {
        return newPosition.x >= 0 && newPosition.y >= 0 && newPosition.x <= stepW.width && newPosition.y <= stepW.height
    }

    private fun addNewStep(newTurn: Move, oldStep: Step, nextSteps: ArrayDeque<Step>) {
        if (newTurn is Move.Travel) {
            if (oldStep.moves.any { it is Move.Travel && it.target_x == newTurn.target_x && it.target_y == newTurn.target_y }) {
                return
            }
        }
        val newW = updateWorld(newTurn, oldStep.w.deepCopy())
        val newStep = Step(newW, oldStep.moves + newTurn, oldStep.score, oldStep.killCount)
        nextSteps.add(newStep)
        stepsCalculated++
    }

    private fun debugResultingMoves(resultingMoves: MutableList<Move>, world: World) {
        loge1 { "debug beam" }
        resultingMoves.take(60).forEachIndexed { index, move ->
            loge1 { "\n#$index move=$move" }
            val newWorld = updateWorld(move, world)

            val closestMonstersToHero = newWorld.monsters.sortedBy { it.pos.euclidianDistance2(newWorld.heroUnit.pos) }.take(10)

            closestMonstersToHero.forEach {
                val euclidianDistance = it.pos.euclidianDistance(newWorld.heroUnit.pos)
                loge1 { "monster=${it} hp=${newWorld.getHp(it)} distanceToHero=$euclidianDistance" }
            }

            loge1 { "world.heroUnit=${world.heroUnit}" }
        }

    }


}

data class Step(
    val w: World,
    val moves: List<Move>,
    val score: Double,
    var killCount: Int,
) {

}
