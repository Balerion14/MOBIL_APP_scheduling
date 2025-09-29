package com.example.projet


import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class SignupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        val signupButton: Button = findViewById(R.id.signUpButton)
        signupButton.setOnClickListener {
            val name = findViewById<EditText>(R.id.nameEditText).text.toString()
            val surname = findViewById<EditText>(R.id.surnameEditText).text.toString()
            val email = findViewById<EditText>(R.id.emailSignUpEditText).text.toString()
            val password = findViewById<EditText>(R.id.passwordSignUpEditText).text.toString()

            val user = JSONObject()
            user.put("name", name)
            user.put("surname", surname)
            user.put("email", email)
            user.put("password", password)

            saveUserLocally(user.toString())
        }
    }

    private fun saveUserLocally(userJson: String) {
        val sharedPreferences = getSharedPreferences("users", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("user_data", userJson)
        editor.apply()

        // Après avoir sauvegardé l'utilisateur, navigue vers LoginActivity
        navigateToLoginActivity()
    }

    private fun navigateToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)

        // Optionnel: si tu ne veux pas que l'utilisateur retourne à l'écran d'inscription en appuyant sur le bouton retour, appelle finish()
        finish()
    }
}


