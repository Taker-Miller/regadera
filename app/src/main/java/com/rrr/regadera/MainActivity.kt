package com.rrr.regadera

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import com.google.gson.Gson
import com.rrr.regadera.R
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var humidityTextView: TextView
    private lateinit var btnActivatePump: Button
    private lateinit var btnDeactivatePump: Button
    private lateinit var statusTextView: TextView

    private val apiUrl = "https://api.thingspeak.com/channels/2788742/feeds.json?results=1"
    private val pumpActivateUrl = "https://api.thingspeak.com/update?api_key=VOPIXI0UYLESDSSK&field2=1"
    private val pumpDeactivateUrl = "https://api.thingspeak.com/update?api_key=VOPIXI0UYLESDSSK&field2=0"
    private val client = OkHttpClient()

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 5000L // Actualización cada 5 segundos

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar vistas
        humidityTextView = findViewById(R.id.humidityTextView)
        btnActivatePump = findViewById(R.id.btnActivatePump)
        btnDeactivatePump = findViewById(R.id.btnDeactivatePump)
        statusTextView = findViewById(R.id.statusTextView)

        // Actualización automática de datos de humedad
        startAutoUpdate()

        // Botón para activar la bomba
        btnActivatePump.setOnClickListener {
            if (isInternetAvailable()) {
                controlPump(pumpActivateUrl, "Bomba encendida!")
            } else {
                statusTextView.text = "Sin conexión a Internet. No se puede encender la bomba."
            }
        }

        // Botón para desactivar la bomba
        btnDeactivatePump.setOnClickListener {
            if (isInternetAvailable()) {
                controlPump(pumpDeactivateUrl, "Bomba apagada!")
            } else {
                statusTextView.text = "Sin conexión a Internet. No se puede apagar la bomba."
            }
        }
    }

    private fun startAutoUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                fetchHumidityData()
                handler.postDelayed(this, updateInterval)
            }
        })
    }

    private fun fetchHumidityData() {
        val request = Request.Builder().url(apiUrl).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    humidityTextView.text = "Error de conexión al obtener datos"
                    statusTextView.text = "Error: ${e.message}"
                    Log.e("FetchHumidity", "Error al obtener datos: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { jsonResponse ->
                    val humidity = parseHumidityFromJson(jsonResponse)
                    runOnUiThread {
                        if (humidity != null) {
                            humidityTextView.text = "Humedad: $humidity%"
                            statusTextView.text = "Datos actualizados correctamente."
                        } else {
                            statusTextView.text = "Error al procesar los datos de humedad."
                        }
                    }
                } ?: runOnUiThread {
                    statusTextView.text = "Error: respuesta vacía del servidor."
                }
            }
        })
    }

    private fun controlPump(url: String, successMessage: String) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    statusTextView.text = "Error al controlar la bomba: ${e.message}"
                    Log.e("ControlPump", "Error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        statusTextView.text = successMessage
                        Log.d("ControlPump", "Comando enviado correctamente: $url")
                    } else {
                        statusTextView.text = "Error al enviar comando: ${response.code}"
                        Log.e("ControlPump", "Error al enviar comando: ${response.code}")
                    }
                }
            }
        })
    }

    private fun parseHumidityFromJson(json: String): String? {
        return try {
            val gson = Gson()
            val response = gson.fromJson(json, ThingSpeakResponse::class.java)
            response.feeds.getOrNull(0)?.field1
        } catch (e: Exception) {
            Log.e("ParseJson", "Error al parsear JSON: ${e.message}")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

data class ThingSpeakResponse(val feeds: List<Feed>)
data class Feed(val field1: String?)
