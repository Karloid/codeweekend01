data class World(
    val hero: HeroConsts,
    val start_x: Int,
    val start_y: Int,
    val width: Int,
    val height: Int,
    var num_turns: Int,
    val monsters: List<Monster>
) {
    lateinit var heroUnit: HeroUnit
    lateinit var unitHps: HashMap<Int, Long>


    fun init2() {
        unitHps = hashMapOf()
        heroUnit = HeroUnit().apply {
            pos = Point2D(start_x, start_y)
            speed = hero.base_speed
            power = hero.base_power
            range = hero.base_range
        }
    }

    fun deepCopy(): World {
        val newMonsters = monsters
        val newHeroUnit = heroUnit.copy()
        val newWorld = World(hero, start_x, start_y, width, height, num_turns, newMonsters)
        newWorld.heroUnit = newHeroUnit
        newWorld.unitHps = HashMap(unitHps)
        return newWorld
    }

    fun getHp(monster: Monster): Long {
        return unitHps[monster.id] ?: monster._hp
    }

    fun subHp(monster: Monster, power: Long) {
        val hp = getHp(monster)
        unitHps[monster.id] = hp - power
    }
}

class HeroUnit {
    var gold: Long = 0
    var exp: Long = 0

    var level = 0
    var speed: Long = 0
    var power: Long = 0
    var range: Long = 0
    var pos: Point2D = Point2D(0, 0)

    var fatigue = 0L

    override fun toString(): String {
        return "HeroUnit(gold=$gold, exp=$exp, level=$level, speed=$speed, power=$power, range=$range, pos=$pos fatigue=$fatigue)"
    }

    fun copy(): HeroUnit {
        val newHeroUnit = HeroUnit()
        newHeroUnit.gold = gold
        newHeroUnit.exp = exp
        newHeroUnit.level = level
        newHeroUnit.speed = speed
        newHeroUnit.power = power
        newHeroUnit.range = range
        newHeroUnit.fatigue = fatigue
        newHeroUnit.pos = pos.copy()
        return newHeroUnit
    }
}

data class HeroConsts(
    val base_speed: Long,
    val base_power: Long,
    val base_range: Long,
    val level_speed_coeff: Int,
    val level_power_coeff: Int,
    val level_range_coeff: Int
)

data class Monster(
    val x: Int,
    val y: Int,
    private var hp: Long,
    val gold: Long,
    val exp: Long,
    val range: Long = 0,
    val attack: Long = 0,
) {

    var id = 0

    val _hp: Long
        get() = hp

    private var _pos: Point2D? = null
    var pos: Point2D
        get() {
            if (_pos == null) {
                _pos = Point2D(x, y)
            }
            return _pos!!
        }
        set(value) = run { _pos = value }

    override fun toString(): String {
        return "Monster(id=$id, x=$x, y=$y, hp=$hp, gold=$gold, exp=$exp, range=$range, attack=$attack, )"
    }
}
