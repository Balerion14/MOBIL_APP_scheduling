package com.example.projet

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.text.SimpleDateFormat

interface OpenAIApiService {
    @Headers("Content-Type: application/json")
    /*@POST("v1/engines/davinci-002	/completions")*/
    @POST("v1/chat/completions")
    suspend fun getChatCompletion(@Body requestBody: RequestBody): Response<ResponseBody>
    /*suspend fun getCompletion(@Body requestBody: RequestBody): Response<ResponseBody>*/
}

fun provideOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("Authorization", "Bearer votre-cle_api_openai")
            val request = requestBuilder.build()
            chain.proceed(request)
        })
        .build()
}

fun provideRetrofit(): Retrofit {
    return Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .client(provideOkHttpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

// Structure représentant un message dans une conversation
data class Message(val role: String, val content: String)

// Payload pour une requête de modèle de chat
data class ChatCompletionRequest(val model: String, val messages: List<Message>, val max_tokens: Int)

fun createChatRequestBody(messagesWithContactInfo: List<String>, model: String, maxTokens: Int): RequestBody {
    // Ajouter chaque message comme une entrée de l'utilisateur
    val chatMessages = messagesWithContactInfo.map { Message(role = "user", content = it) }.toMutableList()

    // Ajouter une demande explicite de résumé
    chatMessages.add(Message(role = "system", content = "Résume la conversation ci-dessus."))

    val requestBody = ChatCompletionRequest(
        model = model, // Assurez-vous d'utiliser le modèle correct pour le chat
        messages = chatMessages,
        max_tokens = maxTokens  // Ajustez selon le besoin de détail du résumé
    )

    val gson = Gson()
    val json = gson.toJson(requestBody)
    return json.toRequestBody("application/json".toMediaType())
}

fun createChatRequestQueryBody(messagesWithContactInfo: List<String>, userQuery: String, model: String, maxTokens: Int): RequestBody {
    val chatMessages : MutableList<Message> = mutableListOf()
    // Ajouter une introduction explicative comme un message de 'system'
    val systemIntroduction = "Assistant virtuel: Je suis ici pour fournir des réponses basées uniquement sur les messages (SMS) fourni par un utilisateur pour obtenir des informations dessus. Si la réponse à votre question n'est pas contenue dans ces messages, je ne serai pas en mesure de fournir une réponse."
    chatMessages.add(Message(role = "system", content = systemIntroduction))

    // Ajouter la requête utilisateur comme un message de 'system'
    chatMessages.add(Message(role = "system", content = userQuery))

    // Instruction explicite pour le modèle GPT
    val systemInstruction = "Veuillez répondre uniquement si la réponse peut être clairement déduite des messages ci-dessus (ce n'est pas un bot pour la conversation, soit on peut repondre soit on peut pas..). Sinon, indiquez que la réponse n'est pas disponible dans le contexte donné."
    chatMessages.add(Message(role = "system", content = systemInstruction))

    // Liste de messages servant de contexte pour le chatbot
    chatMessages.add(Message(role = "user", content = "voir messsages SMS ci dessous :"))
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


// Créer un objet pour le corps de la requête
data class Payload(val prompt: String, val max_tokens: Int)

class ContactSummaryActivity : AppCompatActivity() {
    private var messages_list: List<String> = listOf()
    companion object {
        const val REQUEST_READ_SMS_PERMISSION = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contact_summary)

        // Vérifier si la permission READ_SMS est déjà accordée
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {

            // Demander la permission READ_SMS à l'utilisateur
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.READ_SMS),
                REQUEST_READ_SMS_PERMISSION)
        } else {
            // La permission est déjà accordée, vous pouvez accéder aux SMS
            val date_ = intent.getStringExtra("selected_date")
            val selectedContactName = intent.getStringExtra("contact_name")
            val number = intent.getStringExtra("contact_number")
            Log.d("contact_name", "Contact name: $selectedContactName")
            Log.d("number", "number: $number")
            selectedContactName?.let { contactName ->
                val contactId = getContactIdByName(this, contactName)
                Log.d("id contact", "Contact ID: $contactId")
                if (contactId != null) {
                    /*val messages = getMessagesFromContact(this, contactId)*/
                    val messages = readSmsDate(number.toString(), date_.toString())/*visiblment il faut ce format de num ("+33767319627") et par contre pb car j ai pas tout les messages voir si c est pas juste l affiche qui est limite*/
                    Log.d("message", "message: $messages")
                    val contactInfo = getContactInfo(this, contactId)
                    Log.d("contactinfo", "Contact info: $contactInfo")
                    val messagesWithContactInfo = listOf(contactInfo) + messages
                    Log.d("messagewith", "message with: $messagesWithContactInfo")
                    if (messages.isEmpty()) {
                        Toast.makeText(this,
                            "PAS DE RESUME CAR AUCUN MESSAGE N EST DISPONIBLE", Toast.LENGTH_LONG).show()}
                    else{
                        messages_list = messagesWithContactInfo
                        getSummary(messagesWithContactInfo)/*ok*/
                    }
                }
            }
            /*query process */
            if (messages_list.isEmpty()) {
                Toast.makeText(this,
                    "PAS DE REPONSE POUR VOTRE REQUETE CAR AUCUN MESSAGE N EST DISPONIBLE", Toast.LENGTH_LONG).show()
            }
            else{
                val queryTextView = findViewById<TextView>(R.id.queryTextView)
                val searchView = findViewById<SearchView>(R.id.searchView)
                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        query?.let {
                            if (it.isNotBlank()) {
                                sendChatRequest(messages_list, it)
                            }
                        }
                        return true
                    }
                    override fun onQueryTextChange(newText: String?): Boolean = false
                })
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_READ_SMS_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // La permission est accordée, vous pouvez accéder aux SMS
                    val date_ = intent.getStringExtra("selected_date")
                    val selectedContactName = intent.getStringExtra("contact_name")
                    val number = intent.getStringExtra("contact_number")
                    Log.d("contact_name", "Contact name: $selectedContactName")
                    Log.d("number", "number: $number")
                    selectedContactName?.let { contactName ->
                        val contactId = getContactIdByName(this, contactName)
                        Log.d("id contact", "Contact ID: $contactId")
                        if (contactId != null) {
                            /*val messages = getMessagesFromContact(this, contactId)*/
                            val messages = readSmsDate(number.toString(), date_.toString())/*visiblment il faut ce format de num ("+33767319627") et par contre pb car j ai pas tout les messages voir si c est pas juste l affiche qui est limite*/
                            Log.d("message", "message: $messages")
                            val contactInfo = getContactInfo(this, contactId)
                            Log.d("contactinfo", "Contact info: $contactInfo")
                            val messagesWithContactInfo = listOf(contactInfo) + messages
                            Log.d("messagewith", "message with: $messagesWithContactInfo")
                            if (messages.isEmpty()) {
                                Toast.makeText(this,
                                    "PAS DE RESUME CAR AUCUN MESSAGE N EST DISPONIBLE", Toast.LENGTH_LONG).show()}
                            else{
                                messages_list = messagesWithContactInfo/*add*/
                                getSummary(messagesWithContactInfo)/*ok*/
                            }
                        }
                    }
                    /*query process */
                    if (messages_list.isEmpty()) {
                        Toast.makeText(this,
                            "PAS DE REPONSE POUR VOTRE REQUETE CAR AUCUN MESSAGE N EST DISPONIBLE", Toast.LENGTH_LONG).show()
                    }
                    else{
                        val queryTextView = findViewById<TextView>(R.id.queryTextView)
                        val searchView = findViewById<SearchView>(R.id.searchView)
                        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                            override fun onQueryTextSubmit(query: String?): Boolean {
                                query?.let {
                                    if (it.isNotBlank()) {
                                        sendChatRequest(messages_list, it)
                                    }
                                }
                                return true
                            }
                            override fun onQueryTextChange(newText: String?): Boolean = false
                        })
                    }
                } else {
                    // La permission est refusée, vous ne pouvez pas accéder aux SMS
                    Log.d("permission resuseeeeee", "PERMMMM")
                }
            }
        }
    }



    @SuppressLint("Range")
    private fun getContactInfo(context: Context, contactId: String): String {
        val contentResolver = context.contentResolver
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId.toLong())
        val cursor = contentResolver.query(uri, null, null, null, null)

        var name = ""
        var phoneNumber = ""

        cursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                val hasPhoneNumber = it.getInt(it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))

                if (hasPhoneNumber > 0) {
                    val phoneCursor = contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        arrayOf(contactId),
                        null
                    )

                    phoneCursor?.use { phone ->
                        if (phone.moveToNext()) {
                            phoneNumber = phone.getString(phone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        }
                    }
                }
            }
        }

        return "Name: $name\nPhone: $phoneNumber"
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }



    /*private fun getSummary(messagesWithContactInfo: List<String>) {
        val apiService = provideRetrofit().create(OpenAIApiService::class.java)
        val joinedText = messagesWithContactInfo.joinToString(separator = "\n")

        // Préparer le payload JSON
        val payload = Payload(prompt = joinedText, max_tokens = 150)
        val gson = Gson()
        val json = gson.toJson(payload)

        // Préparer le RequestBody
        val requestBody = json.toRequestBody("application/json".toMediaType())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getCompletion(requestBody)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val summaryText = response.body()?.string() ?: "Error parsing response"
                        setSummaryText(summaryText)
                    } else {
                        Log.e("erreur 1", "erreur with: ${response.errorBody()?.string()}")
                        showErrorDialog("Failed to fetch summary: ${response.errorBody()?.string()}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("erreur 2", "erreur with: ${e.localizedMessage}")
                    showErrorDialog("Error: ${e.localizedMessage}")
                }
            }
        }
    }*/

    private fun getSummary(messagesWithContactInfo: List<String>) {
        val apiService = provideRetrofit().create(OpenAIApiService::class.java)
        val requestBody = createChatRequestBody(messagesWithContactInfo, "gpt-4-turbo-2024-04-09", 4096)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getChatCompletion(requestBody)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val summaryText = response.body()?.string() ?: "Error parsing response"
                        setSummaryText(summaryText)
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





    private fun setSummaryText(response: String) {
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
                val summaryTextView = findViewById<TextView>(R.id.summaryTextView)
                summaryTextView.text = text
                summaryTextView.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("Range")
    private fun getContactIdByName(context: Context, name: String): String? {
        val contentResolver = context.contentResolver
        val uri = ContactsContract.Contacts.CONTENT_URI
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(name)

        var contactId: String? = null

        val cursor: Cursor? = contentResolver.query(uri, null, selection, selectionArgs, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                contactId = it.getString(idIndex)
            }
        }

        return contactId
    }

    private fun formatNumberToE164(phoneNumber: String): String? {
        val phoneUtil = PhoneNumberUtil.getInstance()
        try {
            val parsedNumber = phoneUtil.parse(phoneNumber, "FR") // "FR" is the country code for France
            return phoneUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (e: NumberParseException) {
            Log.e("Error", "Error parsing phone number: $phoneNumber")
        }
        return null
    }
    private fun readSms(contactNumber : String): ArrayList<String> {
        val smsList = ArrayList<String>()
        val contentResolver = contentResolver
        val formattedNumber = formatNumberToE164(contactNumber)
        Log.d("numbertransform", "numbertransform: $formattedNumber")
        if (formattedNumber != null) {
            val selection = "${Telephony.Sms.ADDRESS} = ?"
            val selectionArgs = arrayOf(formattedNumber)
            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                    smsList.add("Sender: $address\nMessage: $body")
                } while (cursor.moveToNext())
            }
            cursor?.close()
        }
        return smsList
    }

    private fun readSmsDate(contactNumber: String, specificDate: String): ArrayList<String> {
        val smsList = ArrayList<String>()
        val contentResolver = contentResolver
        val formattedNumber = formatNumberToE164(contactNumber)
        Log.d("numbertransform", "numbertransform: $formattedNumber")

        // Convertir la date spécifique en timestamp
        val sdf = SimpleDateFormat("yyyy-MM-dd")
        val date = sdf.parse(specificDate)
        val startTime = date.time
        val endTime = startTime + 86400000  // Ajoute 24 heures en millisecondes

        if (formattedNumber != null) {
            val selection = "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?"
            val selectionArgs = arrayOf(formattedNumber, startTime.toString(), endTime.toString())

            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                    smsList.add("Sender: $address\nMessage: $body")
                } while (cursor.moveToNext())
                cursor.close()
            }
        }
        // Vérifier si la liste des messages est vide
        if (smsList.isEmpty()) {
            Toast.makeText(this,
                "Aucun message n'est disponible à la date sélectionnée($date)", Toast.LENGTH_LONG).show()}
        return smsList
    }

    private fun sendChatRequest(messages: List<String>, query: String) {
        // La fonction createChatRequestBody est mise à jour pour prendre la requête utilisateur directement
        val requestBody = createChatRequestQueryBody(messages, query, "gpt-4-turbo-2024-04-09", 4096)
        val apiService = provideRetrofit().create(OpenAIApiService::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getChatCompletion(requestBody)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val summaryText = response.body()?.string() ?: "Error parsing response"
                        setQueryText(summaryText)
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

