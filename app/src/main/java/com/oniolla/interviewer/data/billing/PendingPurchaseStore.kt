package com.oniolla.interviewer.data.billing

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.oniolla.interviewer.data.Encryptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

class PendingVerification(
    @SerializedName("accountId") val accountId: String,
    @SerializedName("purchaseToken") val purchaseToken: String,
    @SerializedName("productId") val productId: String,
    @SerializedName("basePlanId") val basePlanId: String?,
    @SerializedName("acknowledged") val acknowledged: Boolean
)

interface PendingPurchaseStore {
    suspend fun all(accountId: String): List<PendingVerification>
    suspend fun put(purchase: PendingVerification)
    suspend fun remove(accountId: String, token: String)
}

/** Uses the same encrypted, backup-excluded preferences as the session. Logout clears it too. */
@Singleton
class EncryptedPendingPurchaseStore @Inject constructor(
    private val preferences: SharedPreferences, private val encryptor: Encryptor
) : PendingPurchaseStore {
    private val mutex = Mutex()
    private val gson = Gson()

    private fun read(): List<PendingVerification> {
        val data = preferences.getString(DATA, null) ?: return emptyList()
        val iv = preferences.getString(IV, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(encryptor.decrypt(Encryptor.EncryptedValue(data, iv)),
                Array<PendingVerification>::class.java).toList()
        }.getOrElse { emptyList() }
    }

    private fun write(values: List<PendingVerification>) {
        val edit = preferences.edit()
        if (values.isEmpty()) edit.remove(DATA).remove(IV)
        else {
            val encrypted = encryptor.encrypt(gson.toJson(values))
            edit.putString(DATA, encrypted.data).putString(IV, encrypted.iv)
        }
        check(edit.commit()) { "Unable to save purchase for verification. Please restore purchases." }
    }

    override suspend fun all(accountId: String): List<PendingVerification> = withContext(Dispatchers.IO) {
        mutex.withLock { read().filter { it.accountId == accountId } }
    }
    override suspend fun put(purchase: PendingVerification) = withContext(Dispatchers.IO) {
        mutex.withLock { write(read().filterNot { it.purchaseToken == purchase.purchaseToken } + purchase) }
    }
    override suspend fun remove(accountId: String, token: String) = withContext(Dispatchers.IO) {
        mutex.withLock { write(read().filterNot { it.accountId == accountId && it.purchaseToken == token }) }
    }

    private companion object {
        const val DATA = "billing_pending_data"
        const val IV = "billing_pending_iv"
    }
}
