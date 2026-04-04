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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hit.edu.train.ui.theme.TrainTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// --- Data Models (与 API 结构对应) ---
data class WeatherResponse(val now: Now)
data class Now(val temp: String, val text: String)
data class GeoResponse(val location: List<LocationItem>?)
data class LocationItem(val id: String)

data class WeatherData(val temp: String, val weather: String)

// --- Network Layer (Retrofit 配置) ---
interface QWeatherService {
    @GET("geo/v2/city/lookup")
    suspend fun getCityId(@Query("location") cityName: String, @Query("key") key: String): GeoResponse

    @GET("v7/weather/now")
    suspend fun getWeather(@Query("location") locationId: String, @Query("key") key: String): WeatherResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://kj3jph2h68.re.qweatherapi.com/"
    val service: QWeatherService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QWeatherService::class.java)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrainTheme { MainContainer() }
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
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("首页") },
                    selected = selectedIndex == 0,
                    onClick = { selectedIndex = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, null) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    var temperature by remember { mutableStateOf("--") }
    var weatherText by remember { mutableStateOf("等待查询") }
    var inputText by remember { mutableStateOf("") }
    var historyCities by remember { mutableStateOf(listOf("北京", "上海", "哈尔滨")) }
    var isExpanded by remember { mutableStateOf(false) }

    // 搜索与历史记录更新逻辑
    val performSearch: (String) -> Unit = { city ->
        if (city.isNotBlank()) {
            scope.launch {
                val result = fetchWeather(city)
                temperature = result.temp
                weatherText = result.weather

                // 更新历史记录并去重，保留最近 5 个
                historyCities = (listOf(city) + historyCities).distinct().take(5)
            }
        }
    }

    fun getWeatherIcon(weather: String): String = when {
        weather.contains("晴") -> "☀️"
        weather.contains("云") || weather.contains("阴") -> "☁️"
        weather.contains("雨") -> "🌧️"
        weather.contains("雪") -> "❄️"
        else -> "🌈"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(title = { Text("为尔滨祈求好天气中...", fontSize = 18.sp) })

        Column(modifier = Modifier.padding(16.dp)) {
            // 搜索框
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("输入城市名") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { performSearch(inputText) }) {
                        Icon(Icons.Default.Search, null)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 历史记录下拉菜单
            ExposedDropdownMenuBox(
                expanded = isExpanded,
                onExpandedChange = { isExpanded = !isExpanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                OutlinedButton(onClick = {}, modifier = Modifier.menuAnchor()) {
                    Text("最近查询 ▼")
                }
                ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
                    historyCities.forEach { city ->
                        DropdownMenuItem(
                            text = { Text(city) },
                            onClick = {
                                inputText = city
                                isExpanded = false
                                performSearch(city)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 天气展示卡片
            Card(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(60.dp).background(Color(0xFFE3F2FD), RoundedCornerShape(30.dp)),
                        contentAlignment = Alignment.Center
                    ) {
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

// --- Logic Layer (协程与网络请求) ---
suspend fun fetchWeather(cityName: String): WeatherData = withContext(Dispatchers.IO) {
    val myKey = "560260b1f1064e5b9b57c96af147f85d"
    try {
        // 1. 获取城市 ID
        val geoResponse = RetrofitClient.service.getCityId(cityName, myKey)
        val locationId = geoResponse.location?.get(0)?.id ?: return@withContext WeatherData("N/A", "未找到城市")

        // 2. 获取天气详情
        val weatherResponse = RetrofitClient.service.getWeather(locationId, myKey)
        WeatherData(weatherResponse.now.temp + "°C", weatherResponse.now.text)
    } catch (e: Exception) {
        e.printStackTrace()
        WeatherData("N/A", "查询失败")
    }
}