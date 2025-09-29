package com.example.projet

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ContactOptionsActivity : AppCompatActivity() {
    private var selectedDate: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contact_option)

        val datePickerButton = findViewById<Button>(R.id.datePickerButton)
        datePickerButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, dayOfMonth ->
                // Configuration du calendrier pour la date sélectionnée
                calendar.set(Calendar.YEAR, selectedYear)
                calendar.set(Calendar.MONTH, selectedMonth)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                // Formatage de la date au format ISO "yyyy-MM-dd"
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                selectedDate = sdf.format(calendar.time)

                // Mise à jour du texte du bouton avec la date formatée
                datePickerButton.text = selectedDate
            }, year, month, day)
            datePickerDialog.show()
        }

        // Récupérer le contact sélectionné à partir des données d'intention
        val selectedContact = intent.getStringExtra("selected_contact")

        // Extraire le nom et le numéro de téléphone du contact
        val contactInfo = selectedContact?.split(" - ") ?: emptyList()
        val contactName = contactInfo[0]
        val contactNumber = contactInfo[1]

        // Définir un écouteur pour le premier bouton
        val button1 = findViewById<Button>(R.id.button1)
        button1.setOnClickListener {
            // Si aucune date n'est sélectionnée, utiliser la date du jour
            if (selectedDate.isBlank()) {
                val today = Calendar.getInstance()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                selectedDate = sdf.format(today.time)
            }
            // Lancer une nouvelle activité pour le premier bouton
            Toast.makeText(this, "message", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, ContactSummaryActivity::class.java)
            intent.putExtra("contact_name", contactName)
            intent.putExtra("contact_number", contactNumber)
            intent.putExtra("selected_contact", selectedContact)
            intent.putExtra("selected_date", selectedDate)  // Passer la date sélectionnée
            startActivity(intent)
        }

        // Définir un écouteur pour le deuxième bouton
        val button2 = findViewById<Button>(R.id.button2)
        button2.setOnClickListener {
            // Lancer une nouvelle activité pour le deuxième bouton
            /*val intent = Intent(this, FourthActivity::class.java)
            intent.putExtra("selected_contact", selectedContact)
            startActivity(intent)*/
            Toast.makeText(this, "Appel", Toast.LENGTH_SHORT).show()
        }

        // Définir un écouteur pour le troisième bouton
        val button3 = findViewById<Button>(R.id.button3)
        button3.setOnClickListener {
            // Lancer une nouvelle activité pour le troisième bouton
            /*val intent = Intent(this, FifthActivity::class.java)
            intent.putExtra("selected_contact", selectedContact)
            startActivity(intent)*/
            Toast.makeText(this, "Messagerie vocal", Toast.LENGTH_SHORT).show()
        }
    }
}

