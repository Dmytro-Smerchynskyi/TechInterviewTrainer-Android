package com.oniolla.interviewer.data.payload

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

@JsonAdapter(GoogleSignInPayloadAdapter::class)
data class GoogleSignInPayload(val idToken: String, val registrationToken: String? = null)

/** Send an explicit null for registrationToken without changing serialization of other API payloads. */
class GoogleSignInPayloadAdapter : TypeAdapter<GoogleSignInPayload>() {
    override fun write(out: JsonWriter, value: GoogleSignInPayload?) {
        if (value == null) { out.nullValue(); return }
        val serializeNulls = out.serializeNulls
        try {
            out.serializeNulls = true
            out.beginObject()
            out.name("idToken").value(value.idToken)
            out.name("registrationToken").value(value.registrationToken)
            out.endObject()
        } finally { out.serializeNulls = serializeNulls }
    }

    override fun read(input: JsonReader): GoogleSignInPayload {
        var idToken = ""
        var registrationToken: String? = null
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "idToken" -> idToken = input.nextString()
                "registrationToken" -> if (input.peek() == JsonToken.NULL) input.nextNull()
                    else registrationToken = input.nextString()
                else -> input.skipValue()
            }
        }
        input.endObject()
        return GoogleSignInPayload(idToken, registrationToken)
    }
}
