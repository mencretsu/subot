package com.example.ngontol

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import androidx.core.content.edit

object PersonaManager {
    private const val PREFS_NAME = "persona_prefs"
    private const val KEY_PERSONA = "persona_data"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(Persona::class.java)

    fun savePersona(context: Context, persona: Persona) {
        val json = adapter.toJson(persona)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_PERSONA, json) }
    }

    fun getPersona(context: Context): Persona? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PERSONA, null)
        return json?.let { adapter.fromJson(it) }
    }

}
