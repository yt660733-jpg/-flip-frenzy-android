package com.indie.flipfrenzy

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import kotlin.math.min
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(13, 16, 35)
        window.navigationBarColor = android.graphics.Color.rgb(13, 16, 35)
        window.decorView.systemUiVisibility = 0
        gameView = GameView(this) { score ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "I scored $score in Flip Frenzy! Can you beat me? #FlipFrenzy")
            }
            startActivity(Intent.createChooser(send, "Share your score"))
        }
        setContentView(gameView)
    }

    override fun onResume() { super.onResume(); if (::gameView.isInitialized) gameView.resume() }
    override fun onPause() { if (::gameView.isInitialized) gameView.pause(); super.onPause() }
}

private class GameView(context: android.content.Context, val shareScore: (Int) -> Unit) : View(context) {
    private val bg = android.graphics.Color.rgb(13, 16, 35)
    private val panel = android.graphics.Color.rgb(25, 31, 60)
    private val white = android.graphics.Color.rgb(247, 248, 255)
    private val muted = android.graphics.Color.rgb(165, 176, 208)
    private val cyan = android.graphics.Color.rgb(62, 232, 220)
    private val pink = android.graphics.Color.rgb(255, 83, 150)
    private val yellow = android.graphics.Color.rgb(255, 211, 92)
    private val purple = android.graphics.Color.rgb(153, 119, 255)
    private val green = android.graphics.Color.rgb(106, 242, 151)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private enum class Screen { MENU, PLAY, OVER, SHOP }
    private data class Orb(var x: Float, var y: Float, var r: Float, var good: Boolean, var color: Int)
    private var screen = Screen.MENU
    private var orbs = mutableListOf<Orb>()
    private var score = 0
    private var best = 0
    private var coins = 0
    private var lives = 3
    private var streak = 0
    private var timeLeft = 30f
    private var lastTick = System.nanoTime()
    private var flipTimer = 0f
    private var targetColor = cyan
    private var targetLabel = "CYAN"
    private var round = 0
    private var skin = 0
    private var purchased = mutableSetOf<Int>()
    private var message = "Tap only the target!"
    private var messageTimer = 0f
    private var w = 0f
    private var h = 0f

    init {
        isFocusable = true
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    fun pause() { lastTick = System.nanoTime() }
    fun resume() { lastTick = System.nanoTime(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        w = width.toFloat(); h = height.toFloat()
        val now = System.nanoTime()
        val dt = ((now - lastTick) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastTick = now
        drawBackground(canvas)
        when (screen) {
            Screen.MENU -> drawMenu(canvas)
            Screen.PLAY -> {
                timeLeft -= dt
                flipTimer += dt
                if (messageTimer > 0f) messageTimer -= dt
                if (flipTimer >= 5f) { flipTimer = 0f; changeTarget() }
                if (timeLeft <= 0f || lives <= 0) endGame()
                drawGame(canvas)
                postInvalidateDelayed(16)
            }
            Screen.OVER -> drawGameOver(canvas)
            Screen.SHOP -> drawShop(canvas)
        }
    }

    private fun drawBackground(c: Canvas) {
        c.drawColor(bg)
        p.style = Paint.Style.FILL
        p.color = android.graphics.Color.rgb(22, 30, 62)
        c.drawCircle(w * .92f, h * .08f, w * .32f, p)
        p.color = android.graphics.Color.rgb(32, 24, 64)
        c.drawCircle(w * .02f, h * .83f, w * .30f, p)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) {
        p.color = color; p.textSize = size; p.textAlign = align
        p.typeface = if (bold) Typeface.create("sans-serif-black", Typeface.NORMAL) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        p.style = Paint.Style.FILL
        c.drawText(s, x, y, p)
    }

    private fun rounded(c: Canvas, rect: RectF, radius: Float, color: Int) {
        p.color = color; p.style = Paint.Style.FILL
        c.drawRoundRect(rect, radius, radius, p)
    }

    private fun button(c: Canvas, label: String, cx: Float, cy: Float, bw: Float, bh: Float, color: Int, size: Float = 18f) {
        rounded(c, RectF(cx-bw/2, cy-bh/2, cx+bw/2, cy+bh/2), bh/2, color)
        text(c, label, cx, cy + size*.34f, size, bg, true)
    }

    private fun drawMenu(c: Canvas) {
        text(c, "FLIP", w/2, h*.19f, w*.15f, white, true)
        text(c, "FRENZY", w/2, h*.27f, w*.15f, cyan, true)
        text(c, "ONE FINGER. ZERO MERCY.", w/2, h*.32f, w*.033f, muted, true)
        // Decorative orb constellation
        val cols = intArrayOf(cyan, pink, yellow, purple, green)
        for (i in 0..4) {
            val x = w*(.18f + i*.16f)
            val y = h*.43f + kotlin.math.sin(i*1.2).toFloat()*h*.025f
            p.color = cols[i]; p.setShadowLayer(22f, 0f, 0f, cols[i])
            c.drawCircle(x, y, w*.045f, p); p.clearShadowLayer()
        }
        text(c, "Tap the colour shown.", w/2, h*.52f, w*.045f, white, true)
        text(c, "The target flips every 5 seconds.", w/2, h*.56f, w*.035f, muted)
        button(c, "PLAY NOW", w/2, h*.67f, w*.62f, h*.075f, cyan, w*.048f)
        button(c, "SKINS  •  $coins coins", w/2, h*.77f, w*.62f, h*.065f, purple, w*.038f)
        text(c, "BEST  $best", w/2, h*.88f, w*.038f, muted, true)
        text(c, "NO ADS • FREE TO PLAY", w/2, h*.94f, w*.028f, muted)
    }

    private fun startGame() {
        score = 0; lives = 3; streak = 0; timeLeft = 30f; flipTimer = 0f; round = 0
        changeTarget()
        orbs.clear()
        repeat(12) { addOrb() }
        screen = Screen.PLAY
        lastTick = System.nanoTime()
        invalidate()
    }

    private fun changeTarget() {
        val palette = listOf(cyan to "CYAN", pink to "PINK", yellow to "GOLD", purple to "PURPLE", green to "MINT")
        val next = palette[Random.nextInt(palette.size)]
        targetColor = next.first; targetLabel = next.second; round++
        orbs.forEach { it.good = Random.nextFloat() < .34f; it.color = if (it.good) targetColor else palette.filter { q -> q.first != targetColor }.random().first }
        if (orbs.none { it.good }) orbs.firstOrNull()?.let { it.good = true; it.color = targetColor }
    }

    private fun addOrb() {
        val margin = w*.12f
        val top = h*.29f
        val bottom = h*.82f
        val palette = listOf(cyan, pink, yellow, purple, green)
        val good = Random.nextFloat() < .34f
        val color = if (good) targetColor else palette.filter { it != targetColor }.random()
        orbs.add(Orb(margin + Random.nextFloat()*(w-2*margin), top + Random.nextFloat()*(bottom-top), w*.065f, good, color))
    }

    private fun drawGame(c: Canvas) {
        text(c, "SCORE", w*.22f, h*.075f, w*.032f, muted, true)
        text(c, "$score", w*.22f, h*.125f, w*.075f, white, true)
        text(c, "BEST $best", w*.78f, h*.075f, w*.032f, muted, true)
        text(c, "♥ ".repeat(lives).trim(), w*.78f, h*.125f, w*.05f, pink, true)
        rounded(c, RectF(w*.08f, h*.16f, w*.92f, h*.177f), h*.01f, panel)
        rounded(c, RectF(w*.08f, h*.16f, w*.08f + w*.84f*(timeLeft/30f).coerceIn(0f,1f), h*.177f), h*.01f, if (timeLeft < 8) pink else cyan)
        text(c, "TAP ONLY", w/2, h*.225f, w*.032f, muted, true)
        text(c, targetLabel, w/2, h*.27f, w*.085f, targetColor, true)
        text(c, "TARGET CHANGES IN ${(5f-flipTimer).toInt()+1}s", w/2, h*.305f, w*.027f, muted)
        for (orb in orbs) {
            p.color = orb.color; p.setShadowLayer(20f, 0f, 0f, orb.color)
            c.drawCircle(orb.x, orb.y, orb.r, p); p.clearShadowLayer()
            p.color = android.graphics.Color.argb(85, 255, 255, 255)
            c.drawCircle(orb.x-orb.r*.28f, orb.y-orb.r*.3f, orb.r*.22f, p)
        }
        if (messageTimer > 0f) text(c, message, w/2, h*.88f, w*.04f, yellow, true)
        text(c, "STREAK  $streak", w/2, h*.94f, w*.035f, muted, true)
    }

    private fun endGame() {
        if (screen != Screen.PLAY) return
        best = maxOf(best, score)
        coins += score / 3 + streak
        screen = Screen.OVER
        invalidate()
    }

    private fun drawGameOver(c: Canvas) {
        text(c, "TIME'S UP!", w/2, h*.22f, w*.105f, pink, true)
        text(c, "YOUR SCORE", w/2, h*.34f, w*.04f, muted, true)
        text(c, "$score", w/2, h*.43f, w*.17f, white, true)
        text(c, "BEST  $best", w/2, h*.50f, w*.045f, cyan, true)
        text(c, "+${score/3 + streak} COINS EARNED", w/2, h*.56f, w*.04f, yellow, true)
        button(c, "PLAY AGAIN", w/2, h*.67f, w*.65f, h*.075f, cyan, w*.045f)
        button(c, "SHARE SCORE", w/2, h*.77f, w*.65f, h*.065f, purple, w*.038f)
        button(c, "HOME", w/2, h*.86f, w*.42f, h*.06f, panel, w*.035f)
    }

    private fun drawShop(c: Canvas) {
        text(c, "THE STYLE LAB", w/2, h*.13f, w*.075f, white, true)
        text(c, "$coins COINS", w/2, h*.19f, w*.042f, yellow, true)
        val names = listOf("DEFAULT CYAN", "NEON PINK", "GOLD RUSH", "ULTRAVIOLET")
        val colors = listOf(cyan, pink, yellow, purple)
        for (i in names.indices) {
            val y = h*(.31f + i*.13f)
            rounded(c, RectF(w*.12f, y-h*.045f, w*.88f, y+h*.045f), h*.025f, panel)
            p.color = colors[i]; c.drawCircle(w*.22f, y, w*.025f, p)
            text(c, names[i], w*.32f, y+5f, w*.032f, white, true, Paint.Align.LEFT)
            val label = if (skin == i) "EQUIPPED" else if (purchased.contains(i) || i == 0) "USE" else "${i*50} COINS"
            text(c, label, w*.78f, y+5f, w*.027f, if (skin == i) cyan else yellow, true)
        }
        text(c, "Cosmetic skins only. No pay-to-win.", w/2, h*.82f, w*.032f, muted)
        button(c, "BACK", w/2, h*.90f, w*.45f, h*.065f, cyan, w*.04f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val x = event.x; val y = event.y
        when (screen) {
            Screen.MENU -> {
                if (y > h*.61f && y < h*.72f) startGame()
                else if (y > h*.73f && y < h*.82f) screen = Screen.SHOP
            }
            Screen.PLAY -> {
                val hit = orbs.indexOfLast { dx -> (x-dx.x)*(x-dx.x)+(y-dx.y)*(y-dx.y) <= dx.r*dx.r*1.8f }
                if (hit >= 0) {
                    val orb = orbs.removeAt(hit)
                    if (orb.good && orb.color == targetColor) {
                        streak++; score += 1 + (streak/5); coins += 1
                        message = when { streak % 10 == 0 -> "UNSTOPPABLE!"; streak % 5 == 0 -> "COMBO x$streak!"; else -> "+${1 + (streak/5)}" }
                        messageTimer = .8f
                    } else {
                        lives--; streak = 0; message = "WRONG COLOUR!"; messageTimer = .8f
                    }
                    addOrb()
                } else {
                    lives--; streak = 0; message = "MISSED!"; messageTimer = .8f
                }
                if (lives <= 0) endGame()
            }
            Screen.OVER -> {
                if (y > h*.61f && y < h*.72f) startGame()
                else if (y > h*.72f && y < h*.82f) shareScore(score)
                else if (y > h*.82f) screen = Screen.MENU
            }
            Screen.SHOP -> {
                if (y > h*.85f) screen = Screen.MENU
                else {
                    val idx = ((y/h-.265f)/.13f).toInt()
                    if (idx in 0..3) {
                        val cost = idx*50
                        if (idx == 0 || purchased.contains(idx)) skin = idx
                        else if (coins >= cost) { coins -= cost; purchased.add(idx); skin = idx }
                        else { message = "NOT ENOUGH COINS"; messageTimer = 1f }
                    }
                }
            }
        }
        invalidate()
        return true
    }
}
