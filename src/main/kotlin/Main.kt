import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.internal.closeQuietly
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors.newFixedThreadPool
import kotlin.math.roundToInt
import kotlin.random.Random

val TOKEN = File("token.txt").readText().trim()


data class ResultTest(
    val name: String,
    val gold: Long,
    val other: String
)

var random = Random(10)

fun main(args: Array<String>) {
    while (true) {
        runCatching {
            mainRoutine(args)
        }
            .onFailure {
                it.printStackTrace()
                Thread.sleep(20_000)
            }

        //  return //!!!! ENDLESS

        MAX_GENERATION_SIZE = (MAX_GENERATION_SIZE * 1.1).roundToInt()

        Thread.sleep(1000)
        System.err.println("Restarting")
        random = Random((Math.random() * 100000000).toInt())
    }
}

val servicePool = newFixedThreadPool(4)

private fun mainRoutine(args: Array<String>) {
    println("Hello World!")

    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    println("Program arguments: ${args.joinToString()}")


    val outputFold = File("outputs_1")
    if (!outputFold.exists()) {
        outputFold.mkdirs()
    }
    // read inputs from "inputs_1" folder
    val inputs = File("inputs_1").listFiles()
        .sortedBy { it.absolutePath }

    val results = mutableListOf<ResultTest>()

    inputs.map {
        val latch = CountDownLatch(1)
        servicePool.submit {
                println("Input file: $it")
                it.readBytes().toString(Charsets.UTF_8).let { input ->
                    // println("Input: $input")

                    val startTurns = Runner().move(input)

                    val turns = gson.toJson(startTurns)

                    val output = File(outputFold, it.name)
                    output.writeText(turns)

                    results.add(ResultTest(it.name, startTurns.heroUnit.gold, "${startTurns.heroUnit}"))
                    submit(it.name.replace(".json", ""), turns, TOKEN)
                    loge { "finished writing to ${output.name}" }
                }
                latch.countDown()
        }
        latch
    }
        .forEach {
            it.await()

        }

    // print table of results
    loge1 { "----" }
    results.forEach {
        loge1 { it.toString() }
    }
    loge1 { "Done" }
}

inline fun loge(msg: () -> String) {
    //loge1(msg)
}

inline fun loge1(msg: () -> String) {
    System.err.println(msg())
}

/**
 * rewrite to kotlin with okhttp
 *
 * api_token = userdata.get('TOKEN')
 * api_url = 'https://codeweekend.dev:3721/api/'
 * files_url = 'https://codeweekend.dev:81/'
 *
 *
 * headers = {
 *     'authorization': f'Bearer {api_token}'
 * }
 *
 *
 * def submit(task_id, solution):
 *     res = requests.post(url = f'{api_url}submit/{task_id}',
 *                         headers=headers, files={'file': solution})
 *     if res.status_code == 200:
 *         return res.text
 *     print(f'Error: {res.text}')
 *     return None
 */

val okHttpClient = OkHttpClient()

fun submit(name: String, output: String, token: String) {

    val url = "https://codeweekend.dev:3721/api/submit/$name"
    val fileUrl = "https://codeweekend.dev:81/"
    val headers = mapOf("authorization" to "Bearer $token")

    val request = okhttp3.Request.Builder()
        .url(url)
        .addHeader("authorization", "Bearer $token")
        .post(
            okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", name, okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), output))
                .build()
        )
        .build()

    val response = okHttpClient.newCall(request).execute()
    if (response.isSuccessful) {
        loge { "Success: ${response.body?.string()}" }
    } else {
        loge { "Error: ${response.body?.string()}" }
    }
    response.closeQuietly()
}
