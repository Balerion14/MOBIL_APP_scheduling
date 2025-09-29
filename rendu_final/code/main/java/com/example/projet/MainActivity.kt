package com.example.projet

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onBackPressed() {
        // Ne rien faire lorsque le bouton de retour est pressé
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bouton pour accéder à une fonctionnalité 1
        val feature1Button: Button = findViewById(R.id.feature1Button)
        feature1Button.setOnClickListener {
            // Mettez ici le code pour lancer l'activité ou la fonction de la fonctionnalité 1
            Toast.makeText(this, "Fonctionnalité 1", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, ContactListActivity::class.java)
            startActivity(intent)
        }

        // Bouton pour accéder à une fonctionnalité 2
        val feature2Button: Button = findViewById(R.id.feature2Button)
        feature2Button.setOnClickListener {
            // Mettez ici le code pour lancer l'activité ou la fonction de la fonctionnalité 2
            Toast.makeText(this, "Fonctionnalité 2", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, SpeakSmart::class.java)
            startActivity(intent)
        }

        // Bouton pour accéder à une fonctionnalité 3
        val feature3Button: Button = findViewById(R.id.feature3Button)
        feature3Button.setOnClickListener {
            // Mettez ici le code pour lancer l'activité ou la fonction de la fonctionnalité 2
            Toast.makeText(this, "Fonctionnalité 3", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, VoicePlanner::class.java)
            startActivity(intent)
        }

        // Bouton pour deco et comme back connexion
        val deco: Button = findViewById(R.id.deco)
        deco.setOnClickListener {
            // Mettez ici le code pour lancer l'activité ou la fonction de la fonctionnalité 2
            Toast.makeText(this, "deconnexion de votre compte", Toast.LENGTH_SHORT).show()
            // Intent pour come connexion
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        // Bouton pour modifier les informations de l'utilisateur
        val editInfoButton: Button = findViewById(R.id.editInfoButton)
        editInfoButton.setOnClickListener {
            // Intention de démarrer une activité où l'utilisateur peut modifier ses informations
            val intent = Intent(this, EditInfoActivity::class.java)
            startActivity(intent)
        }


    }
}