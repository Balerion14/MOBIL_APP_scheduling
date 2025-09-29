package com.example.projet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    override fun onBackPressed() {
        // Ne rien faire lorsque le bouton de retour est pressé
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connexion)

        // Initialisation du bouton d'inscription
        val signupButton: Button = findViewById(R.id.gotoSignUpButton)
        signupButton.setOnClickListener {
            // Intent pour démarrer SignupActivity
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }

        val loginButton: Button = findViewById(R.id.loginButton)
        loginButton.setOnClickListener {
            val email = findViewById<EditText>(R.id.emailEditText).text.toString()
            val password = findViewById<EditText>(R.id.passwordEditText).text.toString()
            try{
                val userJson = getUserData()
                if (userJson != null) {
                    val user = JSONObject(userJson)
                    if (user.getString("email") == email && user.getString("password") == password) {
                        Toast.makeText(this, "Connexion réussie", Toast.LENGTH_LONG).show()
                        // Intent pour démarrer activite principal
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Échec de la connexion", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Erreur lors de la connexion : ${e.message}")
                Toast.makeText(this, "Erreur lors de la connexion", Toast.LENGTH_LONG).show()
            }

        }
    }

    private fun getUserData(): String? {
        val sharedPreferences = getSharedPreferences("users", Context.MODE_PRIVATE)
        return sharedPreferences.getString("user_data", null)
    }
}