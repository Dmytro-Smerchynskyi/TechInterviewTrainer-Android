package com.oniolla.interviewer.data

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import retrofit2.HttpException
import java.io.IOException
import java.lang.reflect.Type

suspend fun <T> apiCall(
    call: suspend () -> T
): T {
    try {
        return call()

    } catch (e: HttpException) {
        val customError = e.getCustomError()

        throw when (e.code()) {
            401 -> ApiException.Unauthorized(customError)
            403 -> ApiException.Forbidden(customError)
            404 -> ApiException.NotFound(customError)

            in 400..499 ->
                ApiException.BadRequest(customError)

            in 500..599 ->
                ApiException.ServerError(customError)

            else ->
                ApiException.Unknown(customError)
        }

    } catch (e: IOException) {
        throw ApiException.NetworkError(e)
    }
}

class CustomError(
    @SerializedName("code") val code: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("subscriptionRequired") val subscriptionRequired: Boolean? = null
)

fun HttpException.getCustomError(): CustomError = response()?.errorBody()?.use {
    try {
        Gson().newBuilder()
            .registerTypeAdapter(CustomError::class.java, CustomErrorDeserializer())
            .create()
            .fromJson(it.charStream(), CustomError::class.java) ?: CustomError()
    } catch (_: JsonParseException) {
        CustomError()
    } catch (_: IllegalStateException) {
        CustomError()
    }
} ?: CustomError()

class CustomErrorDeserializer : JsonDeserializer<CustomError> {

    override fun deserialize(
        json: JsonElement?,
        typeOf: Type?,
        context: JsonDeserializationContext?
    ): CustomError {
        val error = json?.takeIf { it.isJsonObject }?.asJsonObject?.get("error")
            ?.takeIf { it.isJsonObject }?.asJsonObject ?: return CustomError()
        fun string(name: String): String? = error.get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        val subscriptionRequired = error.get("subscriptionRequired")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
        return CustomError(code = string("code"), message = string("message"),
            subscriptionRequired = subscriptionRequired)
    }
}

sealed class ApiException(message: String?) : Exception(message) {
    class BadRequest(val customError: CustomError) : ApiException(customError.message)
    class CurrentPasswordRejected(val customError: CustomError) : ApiException(customError.message)
    class Unauthorized(val customError: CustomError) : ApiException(customError.message)
    class ReauthenticationRequired(val customError: CustomError) : ApiException(customError.message)
    class Forbidden(val customError: CustomError) : ApiException(customError.message)
    class NotFound(val customError: CustomError) : ApiException(customError.message)
    class ServerError(val customError: CustomError) : ApiException(customError.message)
    class NetworkError(exception: Exception) : ApiException(exception.message)
    class Unknown(val customError: CustomError) : ApiException(customError.message)
}

fun Throwable.toApiException() = when (this) {
    is ApiException -> this
    is HttpException -> {
        val customError = getCustomError()

        when (code()) {
            401 -> ApiException.Unauthorized(customError)
            403 -> ApiException.Forbidden(customError)
            404 -> ApiException.NotFound(customError)

            in 400..499 ->
                ApiException.BadRequest(customError)

            in 500..599 ->
                ApiException.ServerError(customError)

            else ->
                ApiException.Unknown(customError)
        }
    }

    is IOException -> ApiException.NetworkError(exception = this)
    else -> ApiException.Unknown(CustomError(message = message))
}