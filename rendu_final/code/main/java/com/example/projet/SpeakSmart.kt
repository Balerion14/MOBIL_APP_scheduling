package com.example.projet

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ListView
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.util.Date
import java.util.UUID

fun createChatRequestQueryBody2(messagesWithContactInfo: List<String>, userQuery: String, model: String, maxTokens: Int): RequestBody {
    val chatMessages : MutableList<Message> = mutableListOf()
    // Ajouter une introduction explicative comme un message de 'system'
    val systemIntroduction = "Assistant virtuel: Je suis ici pour fournir des réponses basées uniquement sur les notes vocal fourni par un utilisateur pour obtenir des informations dessus. Si la réponse à votre question n'est pas contenue dans ces notes vocal, je ne serai pas en mesure de fournir une réponse."
    chatMessages.add(Message(role = "system", content = systemIntroduction))

    // Ajouter la requête utilisateur comme un message de 'system'
    chatMessages.add(Message(role = "system", content = userQuery))

    // Instruction explicite pour le modèle GPT
    val systemInstruction = "Veuillez répondre uniquement si la réponse peut être clairement déduite des notes vocales ci-dessous (ce n'est pas un bot pour la conversation, soit on peut repondre soit on peut pas..). Sinon, indiquez que la réponse n'est pas disponible dans le contexte donné."
    chatMessages.add(Message(role = "system", content = systemInstruction))

    // Liste de messages servant de contexte pour le chatbot
    chatMessages.add(Message(role = "user", content = "voir notes vocales ci dessous :"))
    chatMessages.addAll(messagesWithContactInfo.map { Message(role = "user", content = it) }.toMutableList())

    // Préparer la requête finale avec le modèle spécifié
    val requestBody = ChatCompletionRequest(
        model = model,  // Utilisez le modèle correct pour le chat, comme "gpt-3.5-turbo" ou autre
        messages = chatMessages,
        max_tokens = maxTokens  // Ajustez selon le besoin de détail de la réponse
    )

    // Convertir l'objet de requête en JSON
    val gson = Gson()
    val json = gson.toJson(requestBody)
    return json.toRequestBody("application/json".toMediaType())
}
interface OpenAIApiService2 {
    @Multipart
    @POST("/v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>
}

fun prepareFilePart(filePath: String, model: String): Pair<MultipartBody.Part, RequestBody> {
    val file = File(filePath)
    val requestFile = file.asRequestBody("audio/mp4".toMediaTypeOrNull()) // Change MIME type to audio/mp4
    val bodyFile = MultipartBody.Part.createFormData("file", file.name, requestFile)
    val modelBody = RequestBody.create("multipart/form-data".toMediaTypeOrNull(), model)
    return Pair(bodyFile, modelBody)
}


fun provideRetrofit2(): Retrofit {
    return Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .client(provideOkHttpClient())
        .build()
}

data class Recording(val name: String, val filePath: String, val timestamp: Long)

class RecordingAdapter(context: Context, private val recordings: List<Recording>)
    : ArrayAdapter<Recording>(context, android.R.layout.simple_list_item_checked, recordings) {

    var selectedIndices = mutableSetOf<Int>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as CheckedTextView
        val recording = getItem(position)

        view.text = "${recording!!.name} - ${Date(recording.timestamp)}"
        view.isChecked = selectedIndices.contains(position)

        // Modifier la couleur de fond lorsqu'un élément est sélectionné
        if (view.isChecked) {
            view.setBackgroundColor(ContextCompat.getColor(context, R.color.selected_item_background))
        } else {
            view.setBackgroundColor(ContextCompat.getColor(context, R.color.default_item_background))
        }

        view.setOnClickListener {
            toggleSelection(position)
        }

        return view
    }
    fun toggleSelection(position: Int) {
        if (selectedIndices.contains(position)) {
            selectedIndices.remove(position)
        } else {
            selectedIndices.add(position)
        }
        notifyDataSetChanged()
    }

    fun getSelectedRecordingsPaths(): List<String> {
        return selectedIndices.map { recordings[it].filePath }
    }
}

class SpeakSmart : AppCompatActivity() {

    private lateinit var recordBtn: Button
    private lateinit var statusTv: TextView
    private var recorder: MediaRecorder? = null
    private var localFilePath: String = ""
    private lateinit var recordingsListView: ListView
    private lateinit var adapter: RecordingAdapter
    private var recordingsList = mutableListOf<Recording>()
    private var messages_list: List<String> = listOf()
    private val endTranscript = MutableStateFlow(false)

    companion object {
        const val MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 1
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speaksmart)

        recordBtn = findViewById(R.id.btn_record)
        statusTv = findViewById(R.id.tv_status)
        recordingsListView = findViewById(R.id.lv_recordings)
        val searchView = findViewById<SearchView>(R.id.search_view)

        loadRecordingsFromJson()

        recordBtn.setOnTouchListener { _, event ->
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), MY_PERMISSIONS_REQUEST_RECORD_AUDIO)
            } else {
                handleRecordTouch(event)
            }
            true
        }

        setupSearchView(searchView)
    }

    private fun updateAndRefreshAdapter() {
        recordingsList.sortByDescending { it.timestamp }
        adapter.notifyDataSetChanged()
    }

    private fun setupSearchView(searchView: SearchView) {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    handleSearchQuery(it)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean = false
        })
    }

    private fun handleSearchQuery(query : String) {
        val selectedPaths = adapter.getSelectedRecordingsPaths()
        for (path in selectedPaths) {
            Log.d("SelectedPath", "Selected recording path: $path")
        }
        lifecycleScope.launch {
            transcribeSelectedAudios(selectedPaths)
            // Attendre que la transcription soit terminée
            endTranscript.first { it }
            endTranscript.value = false

            val total_mots =
                messages_list.flatMap { it.split("\\s+".toRegex()) } // Divise en mots en utilisant les espaces comme délimiteurs
                    .filter { it.isNotEmpty() } // Filtre les chaînes vides qui peuvent se produire avec des espaces supplémentaires
                    .count() // Compte le nombre total de mots
            Log.d("contenu note vocales", "note vocales: $messages_list")
            if (query.isNotEmpty() && messages_list.isNotEmpty() && total_mots < 127000) {
                Log.d("condition valide", "condition: $total_mots, $query, $messages_list")
                /*Appel gpt4 pour generer reponse*/
                // La fonction createChatRequestBody est mise à jour pour prendre la requête utilisateur directement
                val requestBody = createChatRequestQueryBody2(
                    messages_list,
                    query,
                    "gpt-4-turbo-2024-04-09",
                    4096
                )
                val apiService = provideRetrofit().create(OpenAIApiService::class.java)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = apiService.getChatCompletion(requestBody)
                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful && response.body() != null) {
                                val summaryText =
                                    response.body()?.string() ?: "Error parsing response"
                                setQueryText(summaryText)
                            } else {
                                showErrorDialog(
                                    "Failed to fetch chat completion: ${
                                        response.errorBody()?.string()
                                    }"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            showErrorDialog("Error: ${e.localizedMessage}")
                        }
                    }
                }
            } else {
                Toast.makeText(
                    this@SpeakSmart,
                    "PAS DE REPONSE CAR le ou les fichier(s) audio sont soient vide,soit il y a trop de tokens, soit la question est vide",
                    Toast.LENGTH_LONG
                ).show()
            }
            messages_list = listOf()
        }
    }

    private fun loadRecordingsFromJson() {
        val file = File(cacheDir, "data_audio.json")
        if (file.exists()) {
            val type = object : TypeToken<List<Recording>>() {}.type
            val json = file.readText()
            var recordings = Gson().fromJson<List<Recording>>(json, type)
            // Tri des enregistrements du plus récent au moins récent
            recordings = recordings.sortedByDescending { it.timestamp }
            recordingsList.addAll(recordings)
            adapter = RecordingAdapter(this, recordingsList)
            recordingsListView.adapter = adapter
        } else {
            recordingsList = mutableListOf<Recording>()  // Initialise une nouvelle liste si le fichier n'existe pas
            adapter = RecordingAdapter(this, recordingsList)
            recordingsListView.adapter = adapter
        }
    }



    private fun handleRecordTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> startRecording()
            MotionEvent.ACTION_UP -> {
                stopRecording()
                recordBtn.performClick()  // Ensure performClick is called on ACTION_UP
            }
        }
        return false
    }

    private fun startRecording() {
        localFilePath = "${cacheDir.absolutePath}/${UUID.randomUUID()}.mp4"
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(localFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
            start()
        }
        statusTv.text = getString(R.string.enregistrement_commence)
    }

    private fun stopRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        statusTv.text = getString(R.string.enregistrement_termine)
        promptForFileName()
    }

    private fun promptForFileName() {
        val input = EditText(this)
        AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.entrer_le_nom_du_fichier))
            setView(input)
            setPositiveButton("OK") { _, _ ->
                val name = input.text.toString()
                saveToLocalJson(name, localFilePath)
            }
            setNegativeButton("Cancel", null)
            show()
        }
    }

    private fun saveToLocalJson(name: String, filePath: String) {
        val timestamp = System.currentTimeMillis()
        val recording = Recording(name, filePath, timestamp)
        recordingsList.add(recording)
        updateAndRefreshAdapter() // Mise à jour et rafraîchissement après ajout
        File(cacheDir, "data_audio.json").writeText(Gson().toJson(recordingsList))
        adapter.notifyDataSetChanged()
    }

    private suspend fun transcribeSelectedAudios(selectedPaths: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            selectedPaths.forEach { path ->
                val (filePart, modelPart) = prepareFilePart(path, "whisper-1")
                try {
                    val response = provideRetrofit2().create(OpenAIApiService2::class.java).transcribeAudio(
                        file = filePart,
                        model = modelPart,
                        authorization = "Bearer votre-cle-api-openai"
                    )
                    if (response.isSuccessful) {
                        // Processer et afficher la transcription ici
                        val transcription = response.body()?.string() ?: ""
                        Log.d("Transcription", "Transcription: $transcription")
                        var list_new = messages_list + transcription
                        messages_list = list_new
                    } else {
                        Log.e("Transcription", "Error transcribing audio: ${response.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e("Transcription", "Failed to transcribe audio", e)
                }
            }
            endTranscript.value = true
        }
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun setQueryText(response: String) {
        try {
            // Using JsonParser to parse the response string into a JsonElement
            val jsonElement = JsonParser.parseString(response)
            val jsonObject = jsonElement.asJsonObject

            // Accessing the nested elements to get the content string
            val text = jsonObject.getAsJsonArray("choices")
                .get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString

            // Updating the TextView on the UI thread
            runOnUiThread {
                val summaryTextView = findViewById<TextView>(R.id.queryTextView)
                summaryTextView.text = text
                summaryTextView.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}



