package com.example.projet

import android.Manifest
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import pub.devrel.easypermissions.EasyPermissions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Calendar as Calendar1
//APRES DES TESTS, JE N ARRIVE PAS TOUT LE TEMPS A AJOUTE DES RDV A  LAUDIO CAR COMME JE PREND LE RISQUE DE TOUT SUPPRIER ET TOUT DONNER A MON LLM, SI IL DECIDE POUR UNE RAISON OU UNE AUTRE D ENGRADER QU UNE PARTIE OU DE MODIFIER QUELQUES CHOSE, JE PEUX PAS FAIRE GRAND CHOSE. GLOBALEMENT CA MARCHE EN TOUT CAS DANS LE CONCEPT APRES CA DEMANDERAI DES AJUSTEMENT SI LE PROMPT ET SAVOIR SI JE SUPPRIME TOUT LES EVENT OU BIEN JE LES GARDE ET JE RAJOUTE QUE LEVENT QUE LE LLM ME DI DE RAJOUTER MAIS LE PB C EST QUE  Y AURA PB SI IL  YA CONFLIT ET QU IL FAUT DECALER AUTRE RDV
//Pour ameliorer voir prompte chat gpt....
//ATTENTION, SI ON EST PAS DANS LE MEME FUSEAU HORAIRE, IL Y AURA BESOIN DE FAIRE AJUSTEMENT
//ATTENTION, si on a beacoup d evenement dans son planning, penser a modif se param :setMaxResults(10) dans les fonction pour recup evenements
data class GetEventModel(
    var id: Int = 0,
    var summary: String? = "",
    var startDate: String = "",
) {
    fun getFormattedDate(): String {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        /*parser.timeZone = TimeZone.getDefault()*/
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val parsedDate = parser.parse(startDate)
        return formatter.format(parsedDate)
    }
}

data class GetEventModel2(
    var id: Int = 0,
    var summary: String? = "",
    var startDate: String = "",
    var description: String = "",
) {
    fun getFormattedDate(): String {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        /*parser.timeZone = TimeZone.getDefault()*/
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val parsedDate = parser.parse(startDate)
        return formatter.format(parsedDate)
    }
}
fun createChatRequestQueryBody3(events: List<GetEventModel2>, newEventInfo: String, model: String, maxTokens: Int): RequestBody {
    val chatMessages: MutableList<Message> = mutableListOf()
    // Ajouter une introduction explicative comme un message de 'system'
    val systemIntroduction = "Assistant virtuel: Je suis ici pour vous aider à gérer votre planning de taxi. Voici le planning actuel :"
    chatMessages.add(Message(role = "system", content = systemIntroduction))

    // Listez tous les événements existants comme contexte
    events.forEach { event ->
        val eventDetails = "${event.summary} - ${event.startDate}\t${event.description}"
        chatMessages.add(Message(role = "user", content = eventDetails))
    }

    // Ajouter le nouvel événement de taxi comme un message de 'user'
    chatMessages.add(Message(role = "user", content = "Nouvel événement à ajouter: $newEventInfo"))

    // Instruction explicite pour le modèle GPT
    val systemInstruction = "Veuillez intégrer le nouvel événement dans le planning existant et générer une liste mise à jour des événements, chacun sous forme d'une chaîne de caractères formatée comme suit: titre / description / heure de début (heure) / minute de début / heure de fin / minute de fin .NE MET RIEN D AUTRE ET SUIT BIEN L ORDRE DE FORMATAGE, je ne veux pas de text superflux, l output que tu genere est = titre / description / heure de début (heure) / minute de début / heure de fin / minute de fin . Rajoute un ' ; ' pour separer les different event entre eux. Prenez en compte les conflits potentiels et les besoins logistiques et pense a me redonner tous les autres event que tu as eu en contexte en plus du nouveau ajoute dans l ordre et le formatage. exemple de sortie qui doit etre respecte parfaitement notament les espaces et les caractere delimiter = rdv / chauvin / 14 / 30 / 16 / 00 ; rdv2 / chauvin2 / 18 / 30 / 19 / 00"
    chatMessages.add(Message(role = "system", content = systemInstruction))

    // Préparer la requête finale avec le modèle spécifié
    val requestBody = ChatCompletionRequest(
        model = model,  // Utilisez le modèle correct pour le chat
        messages = chatMessages,
        max_tokens = maxTokens  // Ajustez selon le besoin de détail de la réponse
    )

    // Convertir l'objet de requête en JSON
    val gson = Gson()
    val json = gson.toJson(requestBody)
    return json.toRequestBody("application/json".toMediaType())
}

/*lien utlise pour l api calendar : https://medium.com/@eneskocerr/get-events-to-your-android-app-using-google-calendar-api-4411119cd586*/

class VoicePlanner : AppCompatActivity() {
    private val TAG = "VoicePlanner"
    private var mediaRecorder: MediaRecorder? = null
    private lateinit var audioFilePath: String
    private lateinit var eventsListView: ListView
    private lateinit var listAdapter: ArrayAdapter<String>
    private var mCredential: GoogleAccountCredential? = null
    private var mService: Calendar? = null
    private val REQUEST_AUTHORIZATION = 1002
    private var currentOrSelectedDateMillis: Long = Calendar1.getInstance().timeInMillis
    private var messages: String = ""
    private val endTranscript = MutableStateFlow(false)
    private var reponse_llm: String = ""
    private val endgeneration = MutableStateFlow(false)

    companion object {
        const val REQUEST_ACCOUNT_PICKER = 1000
        const val REQUEST_PERMISSION_GET_ACCOUNTS = 1001
        const val PREF_ACCOUNT_NAME = "accountName"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_planner)

        if (!hasMicrophonePermission()) {
            requestMicrophonePermission()
        }

        createAudioDirectory()
        initCredentials()
        Log.d(TAG, "Credentials initialized")

        val btnRecordAudio = findViewById<ImageButton>(R.id.btnRecordAudio)
        btnRecordAudio.setOnTouchListener { _, event ->
            // Check if Google Account is already selected and if necessary permissions are granted
            if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)) {
                val accountName = getPreferences(Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null)
                if (accountName != null) {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startRecording()
                            Log.d(TAG, "Recording started")
                        }

                        MotionEvent.ACTION_UP -> {
                            stopRecording()
                            Log.d(TAG, "Recording stopped")
                        }
                    }
                }
                else{
                    Toast.makeText(this, "PAS DE COMPTE GOOGLE CONNECTE", Toast.LENGTH_SHORT).show()
                }
            }
            else{
                Toast.makeText(this, "PAS D AUTORISATION OU DE COMPTE GOOGLE", Toast.LENGTH_SHORT).show()
            }
            true
        }

        eventsListView = findViewById(R.id.listView)
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        eventsListView.adapter = listAdapter

        // Check if Google Account is already selected and if necessary permissions are granted
        if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)) {
            val accountName = getPreferences(Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null)
            if (accountName != null) {
                mCredential?.selectedAccountName = accountName
                updateEventList(currentOrSelectedDateMillis)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                val date = Date(currentOrSelectedDateMillis)
                val formattedDate = dateFormat.format(date)
                Toast.makeText(this, "Par defaults, les evenements du " + formattedDate +  "seront affichés", Toast.LENGTH_SHORT).show()
            } else {
                // Optional: Prompt user to select account if not already set
                chooseAccount()
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                val date = Date(currentOrSelectedDateMillis)
                val formattedDate = dateFormat.format(date)
                Toast.makeText(this, "Par defaults, les evenements du " + formattedDate +  "seront affichés", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Ask for permissions if not already granted
            EasyPermissions.requestPermissions(
                this,
                "This application needs to access your Google account (via Contacts).",
                REQUEST_PERMISSION_GET_ACCOUNTS,
                Manifest.permission.GET_ACCOUNTS
            )
        }

        // Ajoutez un appel à showDatePicker() à un bouton ou à une autre interaction de l'interface utilisateur
        val selectDateButton = findViewById<Button>(R.id.btnAction)
        selectDateButton.setOnClickListener {
            // Check if Google Account is already selected and if necessary permissions are granted
            if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)) {
                val accountName = getPreferences(Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null)
                if (accountName != null) {
                    mCredential?.selectedAccountName = accountName
                    showDatePicker()
                    Toast.makeText(this, "Les evenements de la date choisie seront affichés", Toast.LENGTH_SHORT).show()
                } else {
                    // Optional: Prompt user to select account if not already set
                    chooseAccount()
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                    val date = Date(currentOrSelectedDateMillis)
                    val formattedDate = dateFormat.format(date)
                    Toast.makeText(this, "Par defaults, les evenements du " + formattedDate +  "seront affichés", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Ask for permissions if not already granted
                EasyPermissions.requestPermissions(
                    this,
                    "This application needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS
                )
            }
        }
    }

    private fun createAudioDirectory() {
        val audioDirectory = File(cacheDir, "plannervoice")
        if (!audioDirectory.exists()) {
            audioDirectory.mkdirs()
        }
        audioFilePath = File(audioDirectory, "recorded_audio.mp4").absolutePath
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicrophonePermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1000)
    }

    private fun startRecording() {
        Toast.makeText(this, "Debut enregistrement", Toast.LENGTH_SHORT).show()
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(audioFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            try {
                prepare()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            start()
        }
    }

    private fun stopRecording() {
        Toast.makeText(this, "Fin enregistrement", Toast.LENGTH_SHORT).show()
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        //Traitement pour  inserction new data dans calendrier a la derniere date selectionné
        var output_llm_save = ""
        lifecycleScope.launch {
            try {
                // Changer de contexte pour exécuter le code bloquant sur un autre thread
                val eventStrings = withContext(Dispatchers.IO) {
                    getDataFromCalendarcomplete(currentOrSelectedDateMillis)
                }
                Log.d(TAG, "LISTE EVENT//////$eventStrings")

                // S'assurez que les autres opérations nécessitant d'être exécutées en arrière-plan utilisent également withContext si nécessaire
                withContext(Dispatchers.IO) {
                    transcribeSelectedAudios(listOf(audioFilePath))
                    val message = endTranscript.first { it }
                    endTranscript.value = false
                    Log.d(TAG, "transcript//////$messages")
                }

                val nb_event_remove = withContext(Dispatchers.IO) {
                    deleteEventsFromCalendar(currentOrSelectedDateMillis)
                }
                Log.d(TAG, "LISTE EVENT REMOVE//////$nb_event_remove")

                withContext(Dispatchers.IO) {
                    generer_response(eventStrings, messages)
                    val message = endgeneration.first { it }
                    endgeneration.value = false
                    Log.d(TAG, "repsonse allm//////$reponse_llm")
                    output_llm_save = reponse_llm
                    reponse_llm = ""
                }

                /*// Changer de contexte pour exécuter le code bloquant sur un autre thread
                val event_new = withContext(Dispatchers.IO) {
                    addEventToCalendar(currentOrSelectedDateMillis, "test1", "ok go...", 14, 30, 16, 30)
                }
                Log.d(TAG, "LISTE EVENT//////$event_new")*/

                val ajout_new_event = withContext(Dispatchers.IO) {
                    addEventsFromString(output_llm_save, currentOrSelectedDateMillis)
                }
                Log.d(TAG, "LISTE EVENT//////$ajout_new_event")
                updateEventList(currentOrSelectedDateMillis)
            } catch (e: Exception) {
                // Gérer l'exception
                Log.e(TAG, "Error in coroutine", e)
            }
        }
    }

    private fun initCredentials() {
        mCredential = GoogleAccountCredential.usingOAuth2(
            this, listOf(CalendarScopes.CALENDAR)
        ).setBackOff(ExponentialBackOff())

        val transport = AndroidHttp.newCompatibleTransport()
        val jsonFactory = JacksonFactory.getDefaultInstance()
        mService = Calendar.Builder(transport, jsonFactory, mCredential)
            .setApplicationName("VoicePlanner")
            .build()
    }

    fun loginGoogleAccount(view: View) {
        chooseAccount()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        val date = Date(currentOrSelectedDateMillis)
        val formattedDate = dateFormat.format(date)
        Toast.makeText(this, "Par defaults, les evenements du " + formattedDate +  "seront affichés", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Attempting to log in")
    }

    fun logoutGoogleAccount(view: View) {
        val editor = getPreferences(Context.MODE_PRIVATE).edit()
        editor.remove(PREF_ACCOUNT_NAME)
        editor.apply()
        mCredential?.selectedAccountName = null
        listAdapter.clear()
        listAdapter.notifyDataSetChanged()
        Toast.makeText(this, "Disconnected from Google Account", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Logged out")
    }

    private fun chooseAccount() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)) {
            val accountName = getPreferences(Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null)
            if (accountName != null) {
                mCredential!!.selectedAccountName = accountName
                updateEventList(currentOrSelectedDateMillis)
            } else {
                // Intention pour choisir un compte
                mCredential?.newChooseAccountIntent()
                    ?.let { startActivityForResult(it, REQUEST_ACCOUNT_PICKER) }
            }
        } else {
            // Demander la permission de GET_ACCOUNTS
            EasyPermissions.requestPermissions(
                this,
                "This application needs to access your Google account (via Contacts).",
                REQUEST_PERMISSION_GET_ACCOUNTS,
                Manifest.permission.GET_ACCOUNTS
            )
        }
        Log.d(TAG, "Choosing account")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_ACCOUNT_PICKER -> if (resultCode == Activity.RESULT_OK && data != null) {
                val accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                if (accountName != null) {
                    val editor = getPreferences(Context.MODE_PRIVATE).edit()
                    editor.putString(PREF_ACCOUNT_NAME, accountName)
                    editor.apply()
                    mCredential?.selectedAccountName = accountName
                    updateEventList(currentOrSelectedDateMillis)
                }
            }
            REQUEST_AUTHORIZATION -> if (resultCode == Activity.RESULT_OK) {
                updateEventList(currentOrSelectedDateMillis)  // Retry the operation that required authorization
            }
            // Add other cases here if necessary
        }
        Log.d(TAG, "Activity result: $resultCode")
    }

    private fun updateEventList(timeStamp: Long) {
        GlobalScope.launch(Dispatchers.Main) {
            val events = getDataFromCalendar(timeStamp).sortedByDescending  { it.getFormattedDate() }
            listAdapter.clear()
            listAdapter.addAll(events.map { "${it.summary ?: "No Title"} - ${it.getFormattedDate()}" })
            listAdapter.notifyDataSetChanged()
            Log.d(TAG, "Event list updated and sorted")
        }
    }

    // Ajustement de getDataFromCalendar pour filtrer les événements par une date spécifique
    //ATTENTION, SI ON EST PAS DANS LE MEME FUSEAU HORAIRE, IL Y AURA BESOIN DE FAIRE AJUSTEMENT
    private suspend fun getDataFromCalendar(selectedDateMillis: Long): List<GetEventModel> {
        val localTimeZone = TimeZone.getTimeZone("Europe/Paris")
        val calendar = Calendar1.getInstance(localTimeZone)
        calendar.timeInMillis = selectedDateMillis

        calendar.set(Calendar1.HOUR_OF_DAY, 0) // Set hour to midnight to start the day
        calendar.set(Calendar1.MINUTE, 0)
        calendar.set(Calendar1.SECOND, 0)
        calendar.set(Calendar1.MILLISECOND, 0)
        val startOfDay = DateTime(calendar.timeInMillis)

        calendar.add(Calendar1.DATE, 1) // Move to the end of the day
        calendar.set(Calendar1.MILLISECOND, -1) // Set one millisecond before the next day starts
        val endOfDay = DateTime(calendar.timeInMillis)

        val eventStrings = mutableListOf<GetEventModel>()

        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching events from: ${startOfDay} to: ${endOfDay}")  // Log the request bounds
                val events = mService!!.events().list("primary")
                    .setMaxResults(10)
                    .setTimeMin(startOfDay)
                    .setTimeMax(endOfDay)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()

                Log.d(TAG, "Number of events retrieved: ${events.items.size}")
                for (event in events.items) {
                    var start = event.start.dateTime
                    if (start == null) {
                        start = event.start.date
                    }
                    eventStrings.add(GetEventModel(summary = event.summary, startDate = start.toString()))
                    Log.d(TAG, "Event added: ${event.summary}")
                }
            } catch (e: UserRecoverableAuthIOException) {
                // Handle the exception by launching the intent to ask for user permission
                withContext(Dispatchers.Main) {
                    startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
                }
                Log.e(TAG, "UserRecoverableAuthIOException: ${e.message}")
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e(TAG, "IOException: ${e.message}")
            }
        }
        return eventStrings
    }



    private fun showDatePicker() {
        val calendar = Calendar1.getInstance()
        val year = calendar.get(Calendar1.YEAR)
        val month = calendar.get(Calendar1.MONTH)
        val day = calendar.get(Calendar1.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            val selectedCalendar = Calendar1.getInstance()
            selectedCalendar.set(year, month, dayOfMonth)
            currentOrSelectedDateMillis = selectedCalendar.timeInMillis
            updateEventList(currentOrSelectedDateMillis)  // Met à jour la liste avec la date choisie
        }, year, month, day)

        datePickerDialog.show()
    }

    private fun getDataFromCalendarcomplete(selectedDateMillis: Long): List<GetEventModel2> {
        val localTimeZone = TimeZone.getTimeZone("Europe/Paris")
        val calendar = Calendar1.getInstance(localTimeZone)
        calendar.timeInMillis = selectedDateMillis

        calendar.set(Calendar1.HOUR_OF_DAY, 0) // Set hour to midnight to start the day
        calendar.set(Calendar1.MINUTE, 0)
        calendar.set(Calendar1.SECOND, 0)
        calendar.set(Calendar1.MILLISECOND, 0)
        val startOfDay = DateTime(calendar.timeInMillis)

        calendar.add(Calendar1.DATE, 1) // Move to the end of the day
        calendar.set(Calendar1.MILLISECOND, -1) // Set one millisecond before the next day starts
        val endOfDay = DateTime(calendar.timeInMillis)

        val eventStrings = mutableListOf<GetEventModel2>()

        try {
            Log.d(TAG, "Fetching events from: ${startOfDay} to: ${endOfDay}")  // Log the request bounds
            val events = mService!!.events().list("primary")
                .setMaxResults(10)
                .setTimeMin(startOfDay)
                .setTimeMax(endOfDay)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute()

            Log.d(TAG, "Number of events retrieved: ${events.items.size}")
            for (event in events.items) {
                var start = event.start.dateTime
                if (start == null) {
                    start = event.start.date
                }
                eventStrings.add(GetEventModel2(id = event.hashCode(), summary = event.summary, startDate = start.toString(), description = event.description ?: "Pas de description disponible"))
                Log.d(TAG, "Event added: ${event.summary}")
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.e(TAG, "UserRecoverableAuthIOException: ${e.message}")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, "IOException: ${e.message}")
        }
        return eventStrings
    }

    private suspend fun transcribeSelectedAudios(selectedPaths: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            selectedPaths.forEach { path ->
                val (filePart, modelPart) = prepareFilePart(path, "whisper-1")
                try {
                    val response = provideRetrofit2().create(OpenAIApiService2::class.java).transcribeAudio(
                        file = filePart,
                        model = modelPart,
                        authorization = "Bearer sk-proj-votre_code"
                    )
                    if (response.isSuccessful) {
                        // Processer et afficher la transcription ici
                        val transcription = response.body()?.string() ?: ""
                        Log.d("Transcription", "Transcription: $transcription")
                        messages = transcription
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

    private fun addEventToCalendar(selectedDateMillis: Long, title: String, description: String, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) : Event
    {
        val localTimeZone = TimeZone.getTimeZone("Europe/Paris")
        val calendar = Calendar1.getInstance(localTimeZone)
        calendar.timeInMillis = selectedDateMillis

        // Configurer l'heure de début exacte
        calendar.set(Calendar1.HOUR_OF_DAY, startHour)
        calendar.set(Calendar1.MINUTE, startMinute)
        calendar.set(Calendar1.SECOND, 0)
        calendar.set(Calendar1.MILLISECOND, 0)
        val startDateTime = DateTime(calendar.timeInMillis)

        // Configurer l'heure de fin exacte
        calendar.set(Calendar1.HOUR_OF_DAY, endHour)
        calendar.set(Calendar1.MINUTE, endMinute)
        val endDateTime = DateTime(calendar.timeInMillis)

        val event = Event()
        event.summary = title
        event.description = description

        val eventStart = EventDateTime()
        eventStart.dateTime = startDateTime
        eventStart.timeZone = "Europe/Paris"
        event.start = eventStart

        val eventEnd = EventDateTime()
        eventEnd.dateTime = endDateTime
        eventEnd.timeZone = "Europe/Paris"
        event.end = eventEnd

        var save_envent = Event()
        try {
            val createdEvent = mService!!.events().insert("primary", event).execute()
            Log.d(TAG, "Event created: ${createdEvent.id}")
            save_envent = createdEvent
        } catch (e: Exception) {
            Log.e(TAG, "Error creating event: ${e.message}")
        }
        return save_envent
    }

    private fun deleteEventsFromCalendar(selectedDateMillis: Long): Int {
        val localTimeZone = TimeZone.getTimeZone("Europe/Paris")
        val calendar = Calendar1.getInstance(localTimeZone)
        calendar.timeInMillis = selectedDateMillis

        // Définir le début de la journée
        calendar.set(Calendar1.HOUR_OF_DAY, 0)
        calendar.set(Calendar1.MINUTE, 0)
        calendar.set(Calendar1.SECOND, 0)
        calendar.set(Calendar1.MILLISECOND, 0)
        val startOfDay = DateTime(calendar.timeInMillis)

        // Définir la fin de la journée
        calendar.add(Calendar1.DATE, 1)
        calendar.set(Calendar1.MILLISECOND, -1)
        val endOfDay = DateTime(calendar.timeInMillis)

        var eventsDeleted = 0

        try {
            val events = mService!!.events().list("primary")
                .setTimeMin(startOfDay)
                .setTimeMax(endOfDay)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute()

            for (event in events.items) {
                mService!!.events().delete("primary", event.id).execute()
                eventsDeleted++
                Log.d(TAG, "Deleted event: ${event.summary}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting events: ${e.message}")
        }

        return eventsDeleted
    }

    private fun generer_response(events: List<GetEventModel2>, newEventInfo: String) {
        val apiService = provideRetrofit().create(OpenAIApiService::class.java)
        val requestBody = createChatRequestQueryBody3(events, newEventInfo, "gpt-4-turbo-2024-04-09", 4096)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getChatCompletion(requestBody)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val reponse = response.body()?.string() ?: "Error parsing response"
                        get_response(reponse)
                    } else {
                        showErrorDialog("Failed to fetch chat completion: ${response.errorBody()?.string()}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorDialog("Error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun get_response(response: String) {
        try {
            Log.d(TAG, "JSON---------: $response")
            // Using JsonParser to parse the response string into a JsonElement
            val jsonElement = JsonParser.parseString(response)
            val jsonObject = jsonElement.asJsonObject

            // Accessing the nested elements to get the content string
            val text = jsonObject.getAsJsonArray("choices")
                .get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
            reponse_llm = text
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if(reponse_llm.isEmpty())
        {
            reponse_llm = "E"
        }
        endgeneration.value = true
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun addEventsFromString(eventData: String, currentOrSelectedDateMillis: Long) : Boolean {
        val eventList = eventData.split(" ; ") // Séparation des différents événements
        for (event in eventList) {
            val details = event.split(" / ") // Séparation des détails de chaque événement
            if (details.size >= 6) {
                Log.d(TAG, "parsing!!!!!!: $details")
                val title = if (details[0].isNotBlank()) details[0] else "Événement sans titre"
                val description = if (details[1].isNotBlank()) details[1] else "Aucune description"
                try {
                    val startHour = if (details[2].isNotBlank()) details[2].toInt() else 0
                    val startMinute = if (details[3].isNotBlank()) details[3].toInt() else 0
                    val endHour = if (details[4].isNotBlank()) details[4].toInt() else 0
                    val endMinute = if (details[5].isNotBlank()) details[5].toInt() else 0
                    // Appel à la fonction d'ajout au calendrier
                    addEventToCalendar(currentOrSelectedDateMillis, title, description, startHour, startMinute, endHour, endMinute)
                } catch (e: NumberFormatException) {
                    // Gérer l'exception si la conversion en entier échoue
                    Log.d(TAG, "parsing!!!!!!: $e")
                    return false // Retourne false si une erreur se produit
                }
            } else {
                Log.d(TAG, "parsing!!!!!!: DERNIER ELSE")
                // Si certains champs sont manquants, appliquer des valeurs par défaut et continuer
                val title = if (details.isNotEmpty() && details[0].isNotBlank()) details[0] else "Événement incomplet"
                val description = "Détails manquants"
                addEventToCalendar(currentOrSelectedDateMillis, title, description, 0, 0, 0, 0)
            }
        }
        return true
    }
}