package com.example.etfdrawdown

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.etfdrawdown.data.PrefsStore
import com.example.etfdrawdown.work.UpdateWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 메인 화면: 면책 고지 + 최근 캐시값 표시 + 수동 갱신 + 위젯 추가 안내.
 * (실제 표시 주체는 홈 화면 위젯이며, 이 화면은 보조 역할)
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
    }

    override fun onResume() {
        super.onResume()
        renderLastValues()
    }

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
            val m3 = r.periods["3m"]?.dropRatio ?: 0.0
            val y1 = r.periods["1y"]?.dropRatio ?: 0.0
            sb.append(r.name)
                .append("  ")
                .append(String.format(Locale.US, "%,.0f", r.currentPrice))
                .append("\n")
                .append(String.format(Locale.US, "  1M %.2f%%  ·  3M %.2f%%  ·  1Y %.2f%%", m1, m3, y1))
                .append("\n\n")
        }
        if (snapshot.updatedAtMs > 0) {
            sb.append("(기준 ").append(timeFmt.format(Date(snapshot.updatedAtMs))).append(")")
        }
        view.text = sb.toString().trim()
    }
}
