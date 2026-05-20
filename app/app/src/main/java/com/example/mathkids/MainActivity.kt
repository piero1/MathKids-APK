package com.example.mathkids

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("math_stats")
object StatsRepo {
    suspend fun save(c: Context, t: Int, co: Int) = c.dataStore.edit { prefs -> prefs[intPreferencesKey("t")]=t; prefs[intPreferencesKey("co")]=co }
    suspend fun load(c: Context): Pair<Int,Int> { val p=c.dataStore.data.first(); return Pair(p[intPreferencesKey("t")]?:0, p[intPreferencesKey("co")]?:0) }
    suspend fun getPin(c: Context): String = c.dataStore.data.first()[stringPreferencesKey("pin")] ?: "1234"
    suspend fun setPin(c: Context, p: String) = c.dataStore.edit { it[stringPreferencesKey("pin")]=p }
}
class Tts(c: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(c, this); var ready=false
    override fun onInit(s: Int) { if(s==TextToSpeech.SUCCESS) { tts?.language=Locale.CHINESE; ready=true } }
    fun speak(t: String) { if(ready) tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, null) }
    fun stop() { tts?.stop(); tts?.shutdown() }
}
data class Q(val txt: String, val ans: Int)
fun genQ(): Q = if(Random.nextBoolean()) { val a=Random.nextInt(1,11); val b=Random.nextInt(1,11-a); Q("$a + $b = ?", a+b) }
else { val a=Random.nextInt(1,11); val b=Random.nextInt(1,a+1); Q("$a - $b = ?", a-b) }

@Composable fun PinDlg(close:()->Unit, ok:()->Unit) {
    var i by remember{mutableStateOf("")}; val c=LocalContext.current; val s=rememberCoroutineScope()
    Dialog(onDismissRequest=close){ Surface(shape=MaterialTheme.shapes.medium, tonalElevation=8.dp){ Column(Modifier.padding(20.dp), Alignment.CenterHorizontally){
        Text("🔐 家长密码", style=MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value=i, onValueChange={ if(it.length<=4&&it.all{c->c.isDigit()}) i=it }, label={Text("4位数字")}, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){ TextButton(onClick=close){ Text("取消") }
            Button(onClick={ s.launch{ if(StatsRepo.getPin(c)==i) ok() else Toast.makeText(c,"密码错误",Toast.LENGTH_SHORT).show() } }, enabled=i.length==4){ Text("确认") } }
    }}}
}

@Composable fun PracticeScreen(goStats:()->Unit) {
    val c=LocalContext.current; val s=rememberCoroutineScope(); var q by remember{mutableStateOf(genQ())}; var a by remember{mutableStateOf("")}; var showPin by remember{mutableStateOf(false)}; var unlock by remember{mutableStateOf(false)}
    val tts=remember{Tts(c)}; var st by remember{mutableStateOf(Pair(0,0))}
    LaunchedEffect(Unit){ st=StatsRepo.load(c) }
    LaunchedEffect(q){ tts.speak("${q.txt.replace("=","等于")}？") }
    DisposableEffect(Unit){ onDispose{tts.stop()} }
    Column(Modifier.fillMaxSize().padding(16.dp), Alignment.CenterHorizontally, Arrangement.Center){
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween){ Text("📐 口算练习", style=MaterialTheme.typography.titleLarge); TextButton(onClick={ if(unlock) goStats() else showPin=true }){ Text("📊 统计") } }
        Spacer(Modifier.height(30.dp)); Text(q.txt, style=MaterialTheme.typography.displayMedium, fontSize=48.sp); Spacer(Modifier.height(20.dp))
        OutlinedTextField(value=a, onValueChange={ a=it.filter{c->c.isDigit()} }, label={Text("答案")}, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number), modifier=Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick={ a.toIntOrNull()?.let{ inp -> val ok=inp==q.ans; s.launch{ StatsRepo.save(c,st.first+1, if(ok) st.second+1 else st.second) }; Toast.makeText(c, if(ok) "🎉 答对了！" else "💪 再想想", Toast.LENGTH_SHORT).show(); q=genQ(); a=""; st=Pair(st.first+1, if(ok) st.second+1 else st.second) } }, modifier=Modifier.fillMaxWidth()){ Text("提交") }
    }
    if(showPin) PinDlg(close={showPin=false}, ok={unlock=true; showPin=false})
}

@Composable fun StatsScreen(back:()->Unit) {
    val c=LocalContext.current; var st by remember{mutableStateOf(Pair(0,0))}
    LaunchedEffect(Unit){ st=StatsRepo.load(c) }
    Column(Modifier.fillMaxSize().padding(20.dp)){
        Text("📊 学习报告", style=MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(20.dp))
        Text("总答题数：${st.first}", fontSize=20.sp); Text("正确数：${st.second}", fontSize=20.sp); Text("正确率：${if(st.first>0) (st.second*100/st.first) else 0}%", fontSize=20.sp)
        Spacer(Modifier.height(30.dp)); Button(onClick=back, modifier=Modifier.fillMaxWidth()){ Text("返回练习") }
    }
}

@Composable fun App() { var showS by remember{mutableStateOf(false)}; Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background){ if(showS) StatsScreen(back={showS=false}) else PracticeScreen(goStats={showS=true}) } }
class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } } }
