package com.coldai.assistant.cmd

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import com.coldai.assistant.R
import java.text.DateFormat
import java.util.Date

sealed interface Cmd
data class Reply(val text: String) : Cmd
data class Remember(val fact: String) : Cmd
/** Действие, требующее подтверждения. perms — разрешения, которые нужно получить перед выполнением. */
class Confirm(val prompt: String, val perms: List<String>, val run: () -> String) : Cmd
object PassToAi : Cmd

/**
 * Локальный разбор команд. Всё, что не распознано, уходит в Gemini.
 * Звонки и SMS отключены специально (школьный режим).
 */
object CommandRouter {
    private const val OPEN = "открой|открыть|відкрий|відкрити|запусти|запустити"

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    fun handle(ctx: Context, raw: String): Cmd {
        val c = raw.trim().replace(r("^колд[,!.:\\s]+"), "").trim().trimEnd('.', '!', '?').trim()
        val t = c.lowercase()
        val locale = ctx.resources.configuration.locales[0]

        r("^(запомни|запам['’ʼ]?ятай)[,:]?\\s+(.+)$").find(c)?.let { return Remember(it.groupValues[2].trim()) }

        if (r("^(допомога|помощь|що ти вмієш|что ты умеешь|які команди|какие команды|команди|команды|help)$").matches(t))
            return Reply(ctx.getString(R.string.cmd_help))

        if (r("который час|сколько времени|скільки часу|котра година|яка зараз година").containsMatchIn(t))
            return Reply(ctx.getString(R.string.cmd_time, DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date())))
        if (r("какое сегодня число|какая сегодня дата|какое число|какой сегодня день|яка сьогодні дата|яке сьогодні число|який сьогодні день").containsMatchIn(t))
            return Reply(ctx.getString(R.string.cmd_date, DateFormat.getDateInstance(DateFormat.FULL, locale).format(Date())))

        if (r("заряд|батаре|battery").containsMatchIn(t) && r("скільки|сколько|який|какой|рівень|уровень|процент").containsMatchIn(t)
            || r("^(заряд( батаре[їи])?|батарея)$").matches(t)
        ) {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return Reply(ctx.getString(R.string.cmd_battery, pct))
        }

        val opt = "( музыку| музику| трек| песню| пісню)?"
        when {
            r("^(поставь на паузу|постав на паузу|пауза|стоп|останови|зупини)$opt$").matches(t) -> return media(ctx, KeyEvent.KEYCODE_MEDIA_PAUSE)
            r("^(играй|продолжи|воспроизведи|грай|продовжи|відтвори)$opt$").matches(t) -> return media(ctx, KeyEvent.KEYCODE_MEDIA_PLAY)
            r("^(следующий трек|следующая песня|наступний трек|наступна пісня|дальше)$").matches(t) -> return media(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
            r("^(предыдущий трек|предыдущая песня|попередній трек|попередня пісня)$").matches(t) -> return media(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }

        when {
            r("^(громче|голосніше|збільш(и)? звук|увеличь звук|сделай громче|зроби голосніше)$").matches(t) ->
                return volume(ctx, AudioManager.ADJUST_RAISE)
            r("^(тише|тихше|зменш(и)? звук|сделай тише|зроби тихіше)$").matches(t) ->
                return volume(ctx, AudioManager.ADJUST_LOWER)
            r("^(без звуку|без звука|выключи звук|вимкни звук|мут|mute)$").matches(t) ->
                return volume(ctx, AudioManager.ADJUST_MUTE)
        }

        r("^(?:найди|найти|пошукай|знайди|загугли|google|пошук)\\s+(.+)$").find(c)?.let {
            val q = it.groupValues[1].trim()
            val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))
            return startSafely(ctx, Intent(Intent.ACTION_VIEW, uri))
        }

        r("^(?:поставь таймер|постав таймер|таймер на|таймер)\\s+(\\d+)\\s*(минут[аыу]?|хвилин[аи]?|мин|хв|секунд[аыу]?|сек)?$").find(t)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: return@let
            val unit = it.groupValues[2]
            val sec = if (unit.startsWith("сек")) n else n * 60
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, sec.coerceIn(1, 24 * 60 * 60))
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            return startSafely(ctx, intent)
        }

        if (r("^(позвони|набери|зателефонуй|подзвони|телефонуй)\\b").containsMatchIn(t))
            return Reply(ctx.getString(R.string.cmd_no_call))
        if (r("^(отправь смс|отправь сообщение|надішли смс|надішли повідомлення|смс)\\b").containsMatchIn(t))
            return Reply(ctx.getString(R.string.cmd_no_sms))

        r("^(?:$OPEN)\\s+(?:сайт\\s+)?((?:https?://)?[\\p{L}\\d-]+(?:\\.[\\p{L}\\d-]+)+\\S*)$").find(c)?.let {
            val u = it.groupValues[1].let { s -> if (s.startsWith("http", true)) s else "https://$s" }
            return startSafely(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        }

        r("^(?:$OPEN)\\s+(?:системные\\s+)?(?:настройки|налаштування)\\s*(.*)$").find(c)?.let {
            val k = it.groupValues[1].lowercase()
            val action = when {
                k.contains("wi") || k.contains("вай") -> Settings.ACTION_WIFI_SETTINGS
                k.contains("bluetooth") || k.contains("блютуз") -> Settings.ACTION_BLUETOOTH_SETTINGS
                k.contains("звук") -> Settings.ACTION_SOUND_SETTINGS
                k.contains("экран") || k.contains("екран") -> Settings.ACTION_DISPLAY_SETTINGS
                k.contains("приложен") || k.contains("застосун") || k.contains("додат") -> Settings.ACTION_APPLICATION_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            return startSafely(ctx, Intent(action))
        }

        r("^(?:$OPEN)\\s+(?:приложение\\s+|додаток\\s+|застосунок\\s+)?(.+)$").find(c)?.let {
            val name = it.groupValues[1].trim().lowercase()
            val pm = ctx.packageManager
            val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            val hit = apps.firstOrNull { a -> a.loadLabel(pm).toString().lowercase() == name }
                ?: apps.firstOrNull { a -> a.loadLabel(pm).toString().lowercase().contains(name) }
            val launch = hit?.let { h -> pm.getLaunchIntentForPackage(h.activityInfo.packageName) }
            return if (launch == null) Reply(ctx.getString(R.string.cmd_app_not_found, it.groupValues[1].trim()))
            else startSafely(ctx, launch)
        }

        return PassToAi
    }

    private fun media(ctx: Context, key: Int): Cmd {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        return Reply(ctx.getString(R.string.cmd_ok))
    }

    private fun volume(ctx: Context, dir: Int): Cmd {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        return Reply(ctx.getString(R.string.cmd_ok))
    }

    private fun startSafely(ctx: Context, i: Intent): Cmd = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Reply(ctx.getString(R.string.cmd_ok))
    } catch (e: ActivityNotFoundException) {
        Reply(ctx.getString(R.string.cmd_open_fail))
    }
}
