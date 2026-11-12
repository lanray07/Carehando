package app.carehando.billing

import android.content.Context
import androidx.datastore.preferences.core.edit
import app.carehando.BuildConfig
import app.carehando.R
import app.carehando.data.Preferences
import app.carehando.data.carePreferences
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.URI
import javax.net.ssl.HttpsURLConnection

class AmazonPremium(context: Context, private val scope: CoroutineScope) : PurchasingListener {
    private val app = context.applicationContext
    private val endpoint = runCatching { URI(BuildConfig.RECEIPT_VERIFIER_URL) }.getOrNull()
        ?.takeIf { it.scheme == "https" && it.host != null && it.userInfo == null && it.fragment == null }
    private val prices = MutableStateFlow<String?>(null)
    val price = prices.asStateFlow()
    private val notices = MutableStateFlow<Int?>(null)
    val notice = notices.asStateFlow()
    val configured: Boolean get() = endpoint != null
    private var registered = false

    fun connect() {
        if (!configured || registered) return
        try {
            PurchasingService.registerListener(app, this)
            registered = true
        } catch (_: RuntimeException) {
            notices.value = R.string.billing_retry
        }
    }

    fun buy() {
        if (registered && price.value != null) {
            notices.value = null
            runCatching { PurchasingService.purchase(SKU) }.onFailure { notices.value = R.string.billing_retry }
        }
    }

    fun restore() {
        if (!registered) return
        notices.value = null
        runCatching {
            PurchasingService.getProductData(setOf(SKU))
            PurchasingService.getPurchaseUpdates(true)
        }.onFailure { notices.value = R.string.billing_retry }
    }

    override fun onUserDataResponse(response: UserDataResponse) = Unit

    override fun onProductDataResponse(response: ProductDataResponse) {
        prices.value = response.productData?.get(SKU)?.price
        if (prices.value == null) notices.value = R.string.billing_retry
    }

    override fun onPurchaseResponse(response: PurchaseResponse) {
        val receipt = response.receipt
        val user = response.userData
        when {
            response.requestStatus == PurchaseResponse.RequestStatus.SUCCESSFUL && receipt != null && user != null ->
                scope.launch { process(user.userId, receipt) }
            response.requestStatus == PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> restore()
            else -> notices.value = R.string.billing_cancelled
        }
    }

    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL || response.userData == null) {
            notices.value = R.string.billing_retry
            return
        }
        val user = response.userData.userId
        scope.launch {
            val receipts = response.receipts.orEmpty().filter { it.sku == SKU && it.productType == ProductType.ENTITLED }
            for (receipt in receipts.sortedBy { it.purchaseDate?.time ?: 0L }) process(user, receipt)
            if (response.hasMore()) PurchasingService.getPurchaseUpdates(false)
        }
    }

    private suspend fun process(user: String, receipt: Receipt) {
        if (receipt.sku != SKU || receipt.productType != ProductType.ENTITLED) return
        try {
            val valid = !receipt.isCanceled && verify(user, receipt.receiptId)
            app.carePreferences.edit {
                if (valid) {
                    it[Preferences.premium] = true
                    it[Preferences.receiptId] = receipt.receiptId
                } else if (it[Preferences.receiptId] == receipt.receiptId) {
                    it.remove(Preferences.premium)
                    it.remove(Preferences.receiptId)
                }
            }
            if (valid) PurchasingService.notifyFulfillment(receipt.receiptId, FulfillmentResult.FULFILLED)
            notices.value = if (valid) R.string.premium_active else R.string.billing_retry
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            notices.value = R.string.billing_retry
        }
    }

    private suspend fun verify(user: String, receipt: String): Boolean = withContext(Dispatchers.IO) {
        val connection = requireNotNull(endpoint).toURL().openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("userId", user).put("receiptId", receipt).toString().toByteArray()
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            check(connection.responseCode == 200)
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(129)
                var size = 0
                while (size < buffer.size) {
                    val read = reader.read(buffer, size, buffer.size - size)
                    if (read < 0) break
                    size += read
                }
                check(size in 1..128)
                val response = JSONObject(String(buffer, 0, size))
                response.getBoolean("entitled")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val SKU = "carehando_premium"
    }
}
