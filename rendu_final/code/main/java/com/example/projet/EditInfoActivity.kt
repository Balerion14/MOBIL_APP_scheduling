package com.example.projet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class EditInfoActivity : AppCompatActivity() {
    private lateinit var nameEditText: EditText
    private lateinit var surnameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_info)

        nameEditText = findViewById(R.id.nameEditText)
        surnameEditText = findViewById(R.id.surnameEditText)
        emailEditText = findViewById(R.id.emailSignUpEditText)
        passwordEditText = findViewById(R.id.passwordSignUpEditText)
        saveButton = findViewById(R.id.ModificationCompte)

        saveButton.setOnClickListener {
            val newName = nameEditText.text.toString()
            val newSurname = surnameEditText.text.toString()
            val newEmail = emailEditText.text.toString()
            val newPassword = passwordEditText.text.toString()

            if (newName.isNotBlank() && newSurname.isNotBlank() && newEmail.isNotBlank() && newPassword.isNotBlank()) {
                saveUserData(newName, newSurname, newEmail, newPassword)
                Toast.makeText(this, "Informations enregistrées", Toast.LENGTH_SHORT).show()
                // Intent pour come back menu
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Les champs ne doivent pas être vides", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserData(name: String, surname: String, email: String, password: String) {
        val sharedPreferences = getSharedPreferences("users", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val userJson = JSONObject().apply {
            put("name", name)
            put("surname", surname)
            put("email", email)
            put("password", password)
        }
        editor.putString("user_data", userJson.toString())
        editor.apply()
    }
}
