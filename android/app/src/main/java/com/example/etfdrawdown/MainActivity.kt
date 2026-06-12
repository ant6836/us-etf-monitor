package com.example.etfdrawdown

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.example.etfdrawdown.data.PrefsStore
import com.example.etfdrawdown.data.YahooClient
import com.example.etfdrawdown.work.UpdateWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 메인 화면: 추적 종목 관리(검색·추가·삭제) + 최근 캐시값 표시 + 수동 갱신.
 * 종목 추가 시 야후가 과거 일봉을 제공하는지 자동 검증한다(^DJUSDIV 사례 차단).
 * (실제 표시 주체는 홈 화면 위젯이며, 이 화면은 설정·보조 역할)
 */
class MainActivity : AppCompatActivity() {

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.btn_refresh_now).setOnClickListener {
            UpdateWorker.enqueueOnce(this)
            Toast.makeText(this, "갱신 요청됨", Toast.LENGTH_SHORT).show()
        }

        val searchInput = findViewById<EditText>(R.id.search_input)
        findViewById<Button>(R.id.btn_search).setOnClickListener { runSearch(searchInput.text.toString()) }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(searchInput.text.toString())
                true
            } else false
        }

        // 수동 갱신 작업이 끝나면 화면을 자동으로 다시 그린다(나갔다 들어올 필요 없음)
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(UpdateWorker.ONE_TIME)
            .observe(this) { infos ->
                if (infos.any { it.state.isFinished }) renderLastValues()
            }
    }

    override fun onResume() {
        super.onResume()
        renderWatchlist()
        renderLastValues()
    }

    // ---- 추적 종목 관리 ----

    private fun renderWatchlist() {
        val container = findViewById<LinearLayout>(R.id.watchlist_container)
        container.removeAllViews()
        val list = PrefsStore.loadWatchlist(this)
        for ((symbol, name) in list) {
            container.addView(makeRow("$name  ($symbol)", getString(R.string.main_remove)) {
                removeSymbol(symbol)
            })
        }
    }

    private fun removeSymbol(symbol: String) {
        val list = PrefsStore.loadWatchlist(this)
        if (list.size <= 1) {
            Toast.makeText(this, "최소 1개 종목은 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }
        PrefsStore.saveWatchlist(this, list.filter { it.first != symbol })
        renderWatchlist()
        UpdateWorker.enqueueOnce(this)
        Toast.makeText(this, "$symbol 삭제됨 — 위젯 갱신 중", Toast.LENGTH_SHORT).show()
    }

    // ---- 종목 검색·추가 ----

    private fun runSearch(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return
        val resultsBox = findViewById<LinearLayout>(R.id.search_results)
        resultsBox.removeAllViews()
        resultsBox.addView(makeInfoText("검색 중…"))

        lifecycleScope.launch {
            val items = try {
                withContext(Dispatchers.IO) { YahooClient.search(query) }
            } catch (e: Exception) {
                null
            }
            resultsBox.removeAllViews()
            when {
                items == null -> resultsBox.addView(makeInfoText("검색 실패 — 네트워크를 확인하세요"))
                items.isEmpty() -> resultsBox.addView(makeInfoText("검색 결과 없음"))
                else -> for (item in items) {
                    resultsBox.addView(
                        makeRow("${item.name}  (${item.symbol} · ${item.type})", getString(R.string.main_add)) {
                            addSymbol(item.symbol, item.name)
                        },
                    )
                }
            }
        }
    }

    private fun addSymbol(symbol: String, name: String) {
        val list = PrefsStore.loadWatchlist(this)
        if (list.any { it.first == symbol }) {
            Toast.makeText(this, "이미 추가된 종목입니다", Toast.LENGTH_SHORT).show()
            return
        }
        if (list.size >= PrefsStore.MAX_WATCHLIST) {
            Toast.makeText(this, "최대 ${PrefsStore.MAX_WATCHLIST}개까지 추가할 수 있습니다", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "$symbol 일별 데이터 확인 중…", Toast.LENGTH_SHORT).show()

        // 야후가 과거 일봉을 제공하는 종목인지 검증 후 추가
        lifecycleScope.launch {
            val bars = try {
                withContext(Dispatchers.IO) { YahooClient.countDailyBars(symbol) }
            } catch (e: Exception) {
                -1
            }
            when {
                bars < 0 ->
                    Toast.makeText(this@MainActivity, "확인 실패 — 네트워크를 확인하세요", Toast.LENGTH_SHORT).show()
                bars < YahooClient.MIN_DAILY_BARS ->
                    Toast.makeText(
                        this@MainActivity,
                        "$symbol: 과거 일봉 데이터 미제공(${bars}일)이라 추가할 수 없습니다",
                        Toast.LENGTH_LONG,
                    ).show()
                else -> {
                    PrefsStore.saveWatchlist(this@MainActivity, list + (symbol to name))
                    renderWatchlist()
                    UpdateWorker.enqueueOnce(this@MainActivity)
                    Toast.makeText(
                        this@MainActivity,
                        "$symbol 추가됨(일봉 ${bars}일 확인) — 위젯 갱신 중",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    // ---- 공용 뷰 빌더 ----

    /** "텍스트 + 우측 버튼" 한 줄을 만든다(추적 목록·검색 결과 공용). */
    private fun makeRow(label: String, buttonText: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        row.addView(
            TextView(this).apply {
                text = label
                textSize = 14f
                maxLines = 2
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            Button(this).apply {
                text = buttonText
                textSize = 12f
                minWidth = 0
                minimumWidth = 0
                setOnClickListener { onClick() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        return row
    }

    private fun makeInfoText(message: String): TextView = TextView(this).apply {
        text = message
        textSize = 13f
        setPadding(0, 8, 0, 8)
    }

    // ---- 최근 캐시값 ----

    private fun renderLastValues() {
        val view = findViewById<TextView>(R.id.last_values)
        val snapshot = PrefsStore.load(this)
        if (snapshot == null) {
            view.text = "아직 데이터가 없습니다. '지금 갱신'을 눌러주세요."
            return
        }
        val sb = StringBuilder()
        for (r in snapshot.results) {
            val m1 = r.periods["1m"]?.dropRatio ?: 0.0
            sb.append(r.name)
                .append("  ")
                .append(String.format(Locale.US, if (r.currentPrice < 1000) "%,.2f" else "%,.0f", r.currentPrice))
                .append("\n")
                .append(String.format(Locale.US, "  1개월 고점 대비 %.2f%%", m1))
                .append("\n\n")
        }
        if (snapshot.updatedAtMs > 0) {
            sb.append("(기준 ").append(timeFmt.format(Date(snapshot.updatedAtMs))).append(")")
        }
        view.text = sb.toString().trim()
    }
}
