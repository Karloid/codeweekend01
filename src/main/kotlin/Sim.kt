inline fun <E> List<E>.fastSumBy(funn: (E) -> Int): Int {
    var sum = 0
    repeat(size) {
        sum += funn(get(it))
    }
    return sum

}

fun updateWorld(turn: Move, currentW: World): World {
    when (turn) {
        is Move.Attack -> {
            val monster = currentW.monsters.find { it.id == turn.target_id && currentW.getHp(it) > 0 }
                ?: throw IllegalStateException("Monster not found id=${turn.target_id}")
            currentW.subHp(monster, currentW.heroUnit.power)

            if (currentW.getHp(monster) <= 0) {
                //currentW.monsters.remove(monster)
                currentW.heroUnit.gold += (monster.gold * (1000f / (1000 + currentW.heroUnit.fatigue))).toInt()
                currentW.heroUnit.exp += monster.exp
                currentW.heroUnit.level = determineLevel(currentW.heroUnit.exp)
                if (currentW.heroUnit.level == 16) {
                    val x = 10
                }
                currentW.heroUnit.power = calculatePower(currentW.hero.base_power, currentW.heroUnit.level, currentW.hero.level_power_coeff)
                currentW.heroUnit.range = calculateRange(currentW.hero.base_range, currentW.heroUnit.level, currentW.hero.level_range_coeff)
                currentW.heroUnit.speed = calculateSpeed(currentW.hero.base_speed, currentW.heroUnit.level, currentW.hero.level_speed_coeff)
            }
        }

        is Move.Travel -> {
            // todo add check if hero can move to target
            val euclDistToTar = Point2D(turn.target_x, turn.target_y).euclidianDistance2(currentW.heroUnit.pos)
            val speed2 = currentW.heroUnit.speed * currentW.heroUnit.speed
            if (euclDistToTar > speed2) {
                throw IllegalStateException("Hero can't move to target")
            }

            if (turn.target_x > currentW.width || turn.target_y > currentW.height) {
                throw IllegalStateException("Hero can't move out of the map target_x=${turn.target_x} target_y=${turn.target_y} width=${currentW.width} height=${currentW.height}")
            }
            if (turn.target_x < 0 || turn.target_y < 0) {
                throw IllegalStateException("Hero can't move out of the map target_x=${turn.target_x} target_y=${turn.target_y} width=${currentW.width} height=${currentW.height}")
            }

            currentW.heroUnit.pos.set(turn.target_x, turn.target_y)
        }
    }

    var totalMonsterDmg = 0L

    currentW.monsters.fastForEach { monster ->
        if (monster.attack == 0L || currentW.getHp(monster) <= 0) {
            return@fastForEach
        }
        if (monster.pos.euclidianDistance2(currentW.heroUnit.pos) <= monster.range * monster.range) {
            val dmg = monster.attack
            totalMonsterDmg += dmg
        }
    }

    currentW.heroUnit.fatigue += totalMonsterDmg

    currentW.num_turns--

    return currentW
}


fun determineLevel(exp: Long): Int {

    if (exp < 1000) {
        return 0
    }

    if (exp < 1000 + 1100) {
        return 1
    }

    if (exp < 1000 + 1100 + 1300) {
        return 2
    }

    if (exp < 1000 + 1100 + 1300 + 1600) {
        return 3
    }

    val expFor5 = 1000 + 1100 + 1300 + 1600 + 2000
    if (exp < expFor5) {
        return 4
    }

    // calc level dynamically
    var level = 5
    var expRequired = 1000 + (level + 1) * ((level + 1) - 1) * 50
    var accumulatedExp = expFor5

    while (exp >= accumulatedExp + expRequired) {
        //loge("exp=$exp accumulatedExp=$accumulatedExp expRequired=$expRequired level=${level}->${level + 1}")
        accumulatedExp += expRequired
        level++
        expRequired = 1000 + (level + 1) * ((level + 1) - 1) * 50
    }

    return level
}

fun calculatePower(basePower: Long, level: Int, levelPowerCoeff: Int): Long {
    return ((basePower * (1 + level * (levelPowerCoeff / 100f))) + 0.001f).toLong()
}           // 20 * (1 + 1 * (50 / 100)) = 30
// 50 * (1 + 1 * (5 / 100))

private fun calculateSpeed(baseSpeed: Long, level: Int, levelSpeedCoeff: Int): Long {
    return (baseSpeed * (1 + level * (levelSpeedCoeff / 100f))).toLong()
}         // 10 * (1 + 2 * (50 / 100))

private fun calculateRange(baseRange: Long, level: Int, levelRangeCoeff: Int): Long {
    return (baseRange * (1 + level * (levelRangeCoeff / 100f))).toLong()
}


fun inRangeOfAttack(from: Point2D, to: Point2D, range: Long): Boolean {
    return range * range > from.euclidianDistance2(to)
}
