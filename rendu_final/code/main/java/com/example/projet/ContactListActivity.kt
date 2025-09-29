package com.example.projet

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.widget.SearchView

class ContactListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private val REQUEST_CODE_PICK_CONTACT = 1
    private val REQUEST_CODE_PERMISSION = 2

    private val contacts = mutableListOf<String>()
    private val filteredContacts = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contact_list)

        listView = findViewById(R.id.listView)
        searchView = findViewById(R.id.searchView)

        // Vérifier si la permission est accordée
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Demander la permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                REQUEST_CODE_PERMISSION
            )
        } else {
            // Récupérer la liste des contacts
            getContacts()
        }

        // Créer un adaptateur pour la liste des contacts
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, filteredContacts)

        // Définir l'adaptateur pour la ListView
        listView.adapter = adapter

        // Définir un écouteur pour la sélection d'un contact
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedContact = filteredContacts[position]
            val intent = Intent(this, ContactOptionsActivity::class.java)
            intent.putExtra("selected_contact", selectedContact)
            startActivity(intent)
        }

        // Définir un écouteur pour la saisie dans la barre de recherche
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { filterContacts(it) }
                return true
            }
        })
    }

    private fun getContacts() {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val nameIndex =
                    it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                /*val contactid = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val id = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.ph)*/
                if (nameIndex >= 0 && numberIndex >= 0) {
                    val name = it.getString(nameIndex)
                    val number = it.getString(numberIndex)
                    contacts.add("$name - $number")
                }
            }
        }
        filteredContacts.addAll(contacts)
    }

    private fun filterContacts(query: String) {
        val filtered = mutableListOf<String>()
        for (contact in contacts) {
            if (contact.contains(query, ignoreCase = true)) {
                filtered.add(contact)
            }
        }
        // Mettre à jour l'adaptateur avec la nouvelle liste filtrée
        val adapter = listView.adapter as ArrayAdapter<String>
        adapter.clear()
        adapter.addAll(filtered)
        adapter.notifyDataSetChanged()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Récupérer la liste des contacts
                getContacts()
            }
        }
    }
}



