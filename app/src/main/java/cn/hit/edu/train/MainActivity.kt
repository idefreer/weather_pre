package cn.hit.edu.train

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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

data class WeatherData(val temp: String, val weather: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrainTheme {
                MainContainer()
            }
        }
    }
}

@Composable
fun MainContainer() {
    var selectedIndex by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                    label = { Text("首页") },
                    selected = selectedIndex == 0,
                    onClick = { selectedIndex = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "个人") },
                    label = { Text("个人") },
                    selected = selectedIndex == 1,
                    onClick = { selectedIndex = 1 }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedIndex) {
                0 -> HomeScreen()
                1 -> ProfileScreen()
            }
        }
    }
}

/**
 * 首页：支持最近 5 个城市历史记录的下拉搜索框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    var temperature by remember { mutableStateOf("--") }
    var weatherText by remember { mutableStateOf("等待查询") }
    var inputText by remember { mutableStateOf("") }

    // 存储最近 5 个城市的列表
    var historyCities by remember { mutableStateOf(listOf("北京", "上海", "哈尔滨")) }
    var isExpanded by remember { mutableStateOf(false) } // 控制下拉菜单展开状态

    // 抽取查询逻辑：搜索并更新历史记录
    val performSearch: (String) -> Unit = { city ->
        if (city.isNotBlank()) {
            scope.launch {
                val result = fetchWeather(city)
                temperature = result.temp
                weatherText = result.weather

                // 更新历史记录逻辑：去重 -> 置顶 -> 截取前5个
                val newList = (listOf(city) + historyCities)
                    .distinct()
                    .take(5)
                historyCities = newList
            }
        }
    }

    // 根据天气文本返回对应的 Emoji 图标
    fun getWeatherIcon(weather: String): String {
        return when {
            weather.contains("晴") -> "☀️"
            weather.contains("云") || weather.contains("阴") -> "☁️"
            weather.contains("雨") -> "🌧️"
            weather.contains("雪") -> "❄️"
            else -> "🌈" // 默认图标
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(title = { Text("欢迎光~~~临", fontSize = 18.sp) })

        Column(modifier = Modifier.padding(16.dp)) {

            // 搜索框
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("输入城市名搜索") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { performSearch(inputText) }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 最近城市下拉框
            ExposedDropdownMenuBox(
                expanded = isExpanded,
                onExpandedChange = { isExpanded = !isExpanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.menuAnchor(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("最近查询 ▼")
                }

                ExposedDropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false }
                ) {
                    historyCities.forEach { city ->
                        DropdownMenuItem(
                            text = { Text(city) },
                            onClick = {
                                inputText = city
                                isExpanded = false
                                performSearch(city) // 点击历史城市直接查询
                            }
                        )
                    }
                    if (historyCities.isEmpty()) {
                        DropdownMenuItem(text = { Text("暂无记录", color = Color.Gray) }, onClick = {})
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 展示面板
            Card(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Spacer(modifier = Modifier.height(20.dp))
                    // ... 在 HomeScreen 内部的展示面板 Card 中
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(Color(0xFFE3F2FD), RoundedCornerShape(30.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // ✨ 修改这里：动态获取图标
                            Text(text = getWeatherIcon(weatherText), fontSize = 30.sp)
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column {
                            Text(text = "天气：$weatherText", fontSize = 18.sp)
                            Text(text = "温度：$temperature", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(title = { Text("我的", fontSize = 18.sp) })
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(80.dp).background(Color.LightGray, RoundedCornerShape(40.dp)))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Box(modifier = Modifier.width(150.dp).height(2.dp).background(Color.LightGray))
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.width(100.dp).height(2.dp).background(Color.LightGray))
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
            Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))) {}
        }
    }
}

suspend fun fetchWeather(cityName: String): WeatherData {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val myKey = "560260b1f1064e5b9b57c96af147f85d"
        val my_token = "kj3jph2h68.re.qweatherapi.com"

        try {
            val encodedCity = java.net.URLEncoder.encode(cityName, "UTF-8")
            val geoUrl = "https://$my_token/geo/v2/city/lookup?location=$encodedCity&key=$myKey"
            val geoResponse = client.newCall(Request.Builder().url(geoUrl).build()).execute().body?.string() ?: ""

            val geoJson = JsonParser.parseString(geoResponse).asJsonObject
            val locationId = geoJson.getAsJsonArray("location").get(0).asJsonObject.get("id").asString

            val weatherUrl = "https://$my_token/v7/weather/now?location=$locationId&key=$myKey"
            val weatherResponse = client.newCall(Request.Builder().url(weatherUrl).build()).execute().body?.string() ?: ""
            val now = JsonParser.parseString(weatherResponse).asJsonObject.getAsJsonObject("now")

            WeatherData(now.get("temp").asString + "°C", now.get("text").asString)
        } catch (e: Exception) {
            WeatherData("N/A", "查询失败")
        }
    }
}