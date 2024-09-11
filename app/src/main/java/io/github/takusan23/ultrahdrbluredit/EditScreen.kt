package io.github.takusan23.ultrahdrbluredit

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.takusan23.akaricore.video.GpuShaderImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 画像を表示するだけの、最低限のシェーダー
 * https://github.com/GameMakerDiscord/blur-shaders
 */
const val TEXTURE_BLUR_FRAGMENT_SHADER = """precision mediump float;

uniform sampler2D s_texture;
uniform vec2 v_resolution;

const int Quality = 8;
const int Directions = 16;
const float Pi = 6.28318530718; //pi * 2
const float Radius = 32.0; // ぼかし具合

uniform float x_start;
uniform float x_end;
uniform float y_start;
uniform float y_end;

void main()
{
    vec2 v_vTexcoord = gl_FragCoord.xy / v_resolution.xy;
    v_vTexcoord = vec2(v_vTexcoord.x, 1.-v_vTexcoord.y);
    
    vec2 radius = Radius / v_resolution.xy;
    vec4 Color = texture2D( s_texture, v_vTexcoord);
    
    // 透過箇所以外
    if (x_start < v_vTexcoord.x && v_vTexcoord.x < x_end) {
        if (y_start < v_vTexcoord.y && v_vTexcoord.y < y_end) {
            for( float d=0.0;d<Pi;d+=Pi/float(Directions) )
            {
                for( float i=1.0/float(Quality);i<=1.0;i+=1.0/float(Quality) )
                {
                    Color += texture2D( s_texture, v_vTexcoord+vec2(cos(d),sin(d))*radius*i);
                }
            }
            Color /= float(Quality)*float(Directions)+1.0;
        }
    }
        
    gl_FragColor = Color;
}
"""

private suspend fun Uri.loadBitmap(context: Context): Bitmap? = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(this@loadBitmap)?.use {
        BitmapFactory.decodeStream(it)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val imageUri = remember { mutableStateOf<Uri?>(null) }
    val processBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val isEnableUltraHdr = remember { mutableStateOf(true) }
    var imageProcessor = remember<GpuShaderImageProcessor?> { null }

    val xStart = remember { mutableFloatStateOf(0f) }
    val xEnd = remember { mutableFloatStateOf(1f) }
    val yStart = remember { mutableFloatStateOf(0f) }
    val yEnd = remember { mutableFloatStateOf(1f) }

    val imagePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { imageUri.value = it }
    )

    LaunchedEffect(key1 = isEnableUltraHdr.value) {
        (context as? Activity)?.window?.colorMode = if (isEnableUltraHdr.value) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun preview() {
        scope.launch {
            // Bitmap をロードする
            val imageBitmap = imageUri.value?.loadBitmap(context)!!

            if (imageProcessor == null) {
                imageProcessor = GpuShaderImageProcessor().apply {
                    prepare(
                        fragmentShaderCode = TEXTURE_BLUR_FRAGMENT_SHADER,
                        width = imageBitmap.width,
                        height = imageBitmap.height
                    )
                    addCustomFloatUniformHandle("x_start")
                    addCustomFloatUniformHandle("x_end")
                    addCustomFloatUniformHandle("y_start")
                    addCustomFloatUniformHandle("y_end")
                }
            }

            // Uniform 変数を更新
            imageProcessor?.setCustomFloatUniform("x_start", xStart.floatValue)
            imageProcessor?.setCustomFloatUniform("x_end", xEnd.floatValue)
            imageProcessor?.setCustomFloatUniform("y_start", yStart.floatValue)
            imageProcessor?.setCustomFloatUniform("y_end", yEnd.floatValue)

// 複数回版
//            var tempBitmap = imageBitmap
//            repeat(10) {
//                tempBitmap = imageProcessor?.drawShader(imageBitmap)!!
//            }

            // 処理する
            val effectBitmap = imageProcessor?.drawShader(imageBitmap)!!
            // ゲインマップを戻す
            effectBitmap.gainmap = imageBitmap.gainmap
            // プレビュー
            processBitmap.value = effectBitmap
        }
    }

    fun save() {
        scope.launch(Dispatchers.IO) {
            context.getExternalFilesDir(null)?.resolve("image_${System.currentTimeMillis()}.jpg")?.outputStream()?.use {
                processBitmap.value?.compress(Bitmap.CompressFormat.JPEG, 100, it)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = stringResource(id = R.string.app_name)) }) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(5.dp)
        ) {

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(text = "UltraHdr")
                Switch(checked = isEnableUltraHdr.value, onCheckedChange = { isEnableUltraHdr.value = it })
            }

            Text(text = imageUri.value.toString())

            Button(onClick = { imagePhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(text = "写真選択")
            }

            AndroidView(
                modifier = Modifier.weight(1f),
                factory = { ImageView(it) },
                update = {
                    if (processBitmap.value != null) {
                        it.setImageBitmap(processBitmap.value)
                    }
                }
            )

            Row(modifier = Modifier.padding(5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(text = "xStart")
                Slider(modifier = Modifier.weight(1f), value = xStart.floatValue, onValueChange = { xStart.floatValue = it }, valueRange = 0f..1f)
                Text(text = "xEnd")
                Slider(modifier = Modifier.weight(1f), value = xEnd.floatValue, onValueChange = { xEnd.floatValue = it }, valueRange = 0f..1f)
            }

            Row(modifier = Modifier.padding(5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(text = "yStart")
                Slider(modifier = Modifier.weight(1f), value = yStart.floatValue, onValueChange = { yStart.floatValue = it }, valueRange = 0f..1f)
                Text(text = "yEnd")
                Slider(modifier = Modifier.weight(1f), value = yEnd.floatValue, onValueChange = { yEnd.floatValue = it }, valueRange = 0f..1f)
            }

            Button(onClick = { preview() }) {
                Text(text = "プレビュー更新")
            }

            Button(onClick = { save() }) {
                Text(text = "保存")
            }

        }
    }
}