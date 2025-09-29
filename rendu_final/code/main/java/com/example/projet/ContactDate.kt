package com.example.projet

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
/*IDEE POUR AMELIORATION : AU LIEU DE FAIRE POUR UNE DATE POUR L ANALYSE DES MESSAGES, FAIRE POUR UNE PERIODE (SEMAINE,MOIS en integrant secruite du nb token a pas deapsser pour l appel api)
* AUTRE IDEE, POUR LES NOTES VOCAL, AVOIR UN BOUTON START AND STOP ET PAS UN BOUTON OU ON DOIT RESTE APPUYE*/
/*PAS UTILISE POUR L INSTANT*/
class ContactDate : AppCompatActivity() {
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
                // Traiter la date ici, par exemple la mettre en forme et l'afficher
                val selectedDate = "$dayOfMonth/${selectedMonth + 1}/$selectedYear"
                datePickerButton.text = selectedDate
            }, year, month, day)
            datePickerDialog.show()
        }
    }
}
