class Runner {

    class StartResult(
        val strategy: Strategy,
        val result: StrategyTurns,
        val took: Long,
    )

    fun move(input: String): StrategyTurns {

        var w: World = gson.fromJson<World>(input, World::class.java)
        w.init2()
        val worldStats = getWorldStats(w)
        System.err.println(worldStats)
        w.monsters.forEachIndexed { index, monster ->
            monster.id = index
        }

        val strats = listOf<Strategy>(
         //   SimpleGuySearch(checkNextMonster = false),
         //   SimpleGuySearch(checkNextMonster = true),
            BeamSearch(penaltyForOldMovementEnabled = true),
            BeamSearch(penaltyForOldMovementEnabled = false),
            //BeamSearch(penaltyForOldMovementEnabled = false),
        )

        val stratResults = mutableListOf<StartResult>()


        strats.map { strat ->
            val startTs = System.currentTimeMillis()
            val result = strat.calcResult(w.deepCopy())

            val took = System.currentTimeMillis() - startTs
            val stratResult = StartResult(strat, result, took)

            loge1 { "strat finished ${getInfoFromResult(stratResult, worldStats)}" }

            stratResults.add(stratResult)
        }

        stratResults.sortByDescending { it.result.heroUnit.gold }

        loge1 { "Final scores:" }
        stratResults.fastForEach { startResult ->
            // print info about the strategy
            val info = getInfoFromResult(startResult, worldStats)
            loge1 { info }

        }

        return stratResults.first().result
    }

    private fun getInfoFromResult(startResult: StartResult, worldStats: WorldStats): String {
        val strat = startResult.strategy
        val result = startResult.result
        val heroUnit = result.heroUnit
        val info = "Strategy: $strat " +
                "with gold=${heroUnit.gold} ${heroUnit.gold / worldStats.numMonsterGold.toFloat() * 100}%  " +
                "exp=${heroUnit.exp} ${heroUnit.exp / worldStats.numMonsterExp.toFloat() * 100}% took=${startResult.took}ms"
        return info
    }

    private fun getWorldStats(w: World): WorldStats {
        return WorldStats(
            numMonsters = w.monsters.size,
            numTurns = w.num_turns,
            numMonsterExp = w.monsters.sumOf { it.exp.toLong() },
            numMonsterGold = w.monsters.sumOf { it.gold.toLong() }
        )
    }
}
