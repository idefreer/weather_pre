package cn.hit.edu.train

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hit.edu.train.ui.theme.TrainTheme
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

// 1. 数据模型
data class WeatherData(val temp: String, val weather: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrainTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WeatherScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * 核心联网逻辑
 * 流程：输入城市名 -> 搜索城市ID -> 根据ID查天气
 */
suspend fun fetchWeather(cityName: String): WeatherData {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val myKey = "560260b1f1064e5b9b57c96af147f85d"
        val my_token = "kj3jph2h68.re.qweatherapi.com"

        try {
            val encodedCity = java.net.URLEncoder.encode(cityName, "UTF-8")

            // 第一步：搜索城市 ID (GeoAPI)
            val geoUrl = "https://$my_token/geo/v2/city/lookup?location=$encodedCity&key=$myKey"
            val geoRequest = Request.Builder().url(geoUrl).build()
            val geoResponse = client.newCall(geoRequest).execute().body?.string() ?: ""

            // 【改动 1】：增加空判断。防止 geoResponse 为空字符串时解析崩溃
            if (geoResponse.isBlank()) return@withContext WeatherData("X", "服务器无响应")

            val geoJson = JsonParser.parseString(geoResponse).asJsonObject
            val code = geoJson.get("code").asString

            if (code != "200") {
                return@withContext WeatherData("N/A", "城市搜索失败: $code")
            }

            // 【改动 2】：根据文档，校验 location 数组是否存在且不为空
            if (!geoJson.has("location") || geoJson.get("location").isJsonNull) {
                return@withContext WeatherData("N/A", "数据格式异常")
            }
            val locationArray = geoJson.getAsJsonArray("location")
            if (locationArray.size() == 0) {
                return@withContext WeatherData("N/A", "未找到该城市")
            }

            // 【改动 3】：明确提取 location.id（对应文档 location.id 字段）
            val locationId = locationArray.get(0).asJsonObject.get("id").asString

            // 第二步：查询实时天气 (WeatherAPI)
            val weatherUrl = "https://$my_token/v7/weather/now?location=$locationId&key=$myKey"
            val weatherRequest = Request.Builder().url(weatherUrl).build()
            val weatherResponse = client.newCall(weatherRequest).execute().body?.string() ?: ""

            if (weatherResponse.isBlank()) return@withContext WeatherData("X", "天气数据空")

            val weatherJson = JsonParser.parseString(weatherResponse).asJsonObject

            // 【改动 4】：严谨解析 "now" 对象（对应文档 now 节点）
            if (weatherJson.get("code").asString == "200") {
                // 检查 now 节点是否存在，防止出现截图中的 "Not a JSON Object: null"
                if (weatherJson.has("now") && !weatherJson.get("now").isJsonNull) {
                    val now = weatherJson.getAsJsonObject("now")

                    // 【改动 5】：严格对应文档字段名提取数据
                    // now.temp: 温度；now.text: 天气状况文字描述
                    val tempValue = now.get("temp").asString
                    val textValue = now.get("text").asString

                    WeatherData("$tempValue°", textValue)
                } else {
                    WeatherData("?", "缺失now数据")
                }
            } else {
                WeatherData("!", "错误码:${weatherJson.get("code").asString}")
            }
        } catch (e: Exception) {
            Log.e("WeatherApp", "联网失败", e)
            // 【改动 6】：优化错误捕获提示，防止抛出原始 NullPointerException
            WeatherData("X", "连接失败: ${e.localizedMessage ?: "未知错误"}")
        }
    }
}

@Composable
fun WeatherScreen(modifier: Modifier = Modifier) {
    var displayCity by remember { mutableStateOf("未查询") }
    var temperature by remember { mutableStateOf("--°") }
    var weatherText by remember { mutableStateOf("等待输入") }
    var inputText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 输入框
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("输入城市名称 (如: 北京)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(30.dp))

        // 城市名
        Text(text = displayCity, fontSize = 32.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(10.dp))

        // 温度
        Text(text = temperature, fontSize = 80.sp, color = Color(0xFF2196F3))

        // 天气描述
        Text(text = weatherText, fontSize = 24.sp, color = Color.Gray)

        Spacer(modifier = Modifier.weight(1f))

        // 查询按钮
        Button(
            onClick = {
                if (inputText.isNotBlank()) {
                    displayCity = inputText
                    temperature = "..."
                    weatherText = "正在获取..."

                    scope.launch {
                        val result = fetchWeather(inputText)
                        temperature = result.temp
                        weatherText = result.weather
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("立即查询真实天气")
        }
    }
}