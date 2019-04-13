package works.wever.redsnotifier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.work.*
import de.siegmar.fastcsv.reader.CsvReader
import de.siegmar.fastcsv.reader.CsvRow
import kotlinx.android.synthetic.main.activity_main.*
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val games = readCsv(this)

        Log.d("games", games.toString())

        val today = LocalDate.now()

        var isGame = false

        var todaysGame: RedsGame? = null

        for (game in games) {
            if (game.startDate.isEqual(today)) {
                Log.d("games", "there is a game today")
                todaysGame = game
                isGame = true
            }
        }

        if (!isGame) {
            Log.d("games", "there is not a game today")
        }

        if (todaysGame != null) {
            gameText.text = "The reds are playing today at ${todaysGame.startTime}"
        } else {
            gameText.text = "There is no game today"
        }

        val now = LocalDateTime.now()

        var nextDay = now.withHour(7)

        if (nextDay.isBefore(now)) {
            nextDay = nextDay.plusDays(1)
        }

        val timeUntilSeven = ChronoUnit.HOURS.between(now, nextDay)

        val workRequest =
            OneTimeWorkRequestBuilder<RedsGameWorker>().setInitialDelay(timeUntilSeven, TimeUnit.HOURS).build()

        WorkManager.getInstance().enqueue(workRequest)

    }
}

class RedsGameWorker(val context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val games = readCsv(context)

        val today = LocalDate.now()

        var isGame = false

        var todaysGame: RedsGame? = null

        for (game in games) {
            if (game.startDate.isEqual(today)) {
                Log.d("games", "there is a game today")
                todaysGame = game
                isGame = true
            }
        }

        if (!isGame) {
            Log.d("games", "there is not a game today")
        }

        if (todaysGame != null) {
            showNotification(context, "There is a reds game today at ${todaysGame.startTime}")
        }

        val workRequest = PeriodicWorkRequestBuilder<RedsGameWorker>(24, TimeUnit.HOURS).build()

        WorkManager.getInstance().enqueueUniquePeriodicWork("redsGame", ExistingPeriodicWorkPolicy.KEEP, workRequest)

        return Result.success()
    }
}

fun showNotification(context: Context, message: String) {
    createNotificationChannel(context)
    val notification = NotificationCompat.Builder(context, "redsGame")
        .setSmallIcon(R.drawable.ic_error)
        .setContentTitle("Reds Game")
        .setContentText(message)
        .build()

    context.getSystemService<NotificationManager>()?.notify(1, notification)
}

fun createNotificationChannel(context: Context) {
    val name = "Reds Game"
    val descriptionText = "Know when the next Reds Game is"
    val importance = NotificationManager.IMPORTANCE_HIGH
    val channel = NotificationChannel("redsGame", name, importance).apply {
        description = descriptionText
    }
    NotificationManagerCompat.from(context).createNotificationChannel(channel)
}

fun readCsv(context: Context): List<RedsGame> {
    val schedule = context.resources.openRawResource(R.raw.schedule)
    val reader = InputStreamReader(schedule)
    val csvReader = CsvReader()

    val redsGames = mutableListOf<RedsGame>()

    csvReader.parse(reader).use { csvParser ->
        var row: CsvRow?

        while (true) {
            row = csvParser.nextRow()
            if (row == null) {
                break
            }
            redsGames.add(RedsGame.fromRow(row))
        }
    }

    return redsGames
}


data class RedsGame(
    val startDate: LocalDate,
    val startTime: LocalTime
) {

    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("M/d/yy")!!
        private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")!!


        fun fromRow(row: CsvRow): RedsGame {
            return RedsGame(
                startDate = LocalDate.parse(row.getField(0), dateFormatter),
                startTime = LocalTime.parse(row.getField(1), timeFormatter)
            )
        }
    }
}

