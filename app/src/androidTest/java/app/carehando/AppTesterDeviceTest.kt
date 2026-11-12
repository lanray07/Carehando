package app.carehando

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import app.carehando.billing.AmazonPremium
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class AppTesterDeviceTest {
    @Test fun sandboxReturnsProductAndPurchaseUpdates() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("carehandoAppTester") == "true")
        val sandbox = instrument.uiAutomation.executeShellCommand("getprop debug.amazon.sandboxmode")
        val mode = ParcelFileDescriptor.AutoCloseInputStream(sandbox).bufferedReader().use { it.readText().trim() }
        assertTrue(BuildConfig.DEBUG)
        assertEquals("Run only against Amazon App Tester", "debug", mode)
        val context = instrument.targetContext
        val receiptFile = File(context.cacheDir, "carehando-sandbox-receipt.json")
        val products = ArrayBlockingQueue<ProductDataResponse>(1)
        val updates = ArrayBlockingQueue<PurchaseUpdatesResponse>(1)
        val purchases = ArrayBlockingQueue<PurchaseResponse>(1)
        instrument.runOnMainSync {
            PurchasingService.registerListener(context, object : PurchasingListener {
                override fun onUserDataResponse(response: UserDataResponse) = Unit
                override fun onProductDataResponse(response: ProductDataResponse) { products.offer(response) }
                override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) { updates.offer(response) }
                override fun onPurchaseResponse(response: PurchaseResponse) { purchases.offer(response) }
            })
        }
        if (arguments.getString("carehandoSandboxFulfill") == "true") {
            val verified = JSONObject(receiptFile.readText())
            instrument.runOnMainSync {
                PurchasingService.notifyFulfillment(verified.getString("receiptId"), FulfillmentResult.FULFILLED)
            }
            return
        }
        instrument.runOnMainSync { PurchasingService.getProductData(setOf(AmazonPremium.SKU)) }
        val product = requireNotNull(products.poll(25, TimeUnit.SECONDS)) { "App Tester did not return product data" }
        assertEquals(ProductDataResponse.RequestStatus.SUCCESSFUL, product.requestStatus)
        val item = requireNotNull(product.productData[AmazonPremium.SKU])
        assertEquals(ProductType.ENTITLED, item.productType)
        assertFalse(item.price.isNullOrBlank())
        fun purchaseUpdates(): PurchaseUpdatesResponse {
            instrument.runOnMainSync { PurchasingService.getPurchaseUpdates(true) }
            val response = requireNotNull(updates.poll(25, TimeUnit.SECONDS)) { "App Tester did not return purchase updates" }
            assertEquals(PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL, response.requestStatus)
            return response
        }
        var response = purchaseUpdates()
        if (arguments.getString("carehandoSandboxRevoked") == "true") {
            val receipts = response.receipts.orEmpty().filter { it.sku == AmazonPremium.SKU }
            assertTrue("App Tester did not return a cancelled Carehando receipt", receipts.any { it.isCanceled })
            assertTrue("App Tester still returned an active Carehando receipt", receipts.all { it.isCanceled })
            return
        }
        val purchase = arguments.getString("carehandoSandboxPurchase") == "true"
        if (purchase && response.receipts.orEmpty().none { it.sku == AmazonPremium.SKU && !it.isCanceled }) {
            instrument.runOnMainSync { PurchasingService.purchase(AmazonPremium.SKU) }
            val result = requireNotNull(purchases.poll(55, TimeUnit.SECONDS)) { "No sandbox purchase response" }
            assertTrue("Sandbox purchase returned ${result.requestStatus}", result.requestStatus == PurchaseResponse.RequestStatus.SUCCESSFUL ||
                result.requestStatus == PurchaseResponse.RequestStatus.ALREADY_PURCHASED)
            response = purchaseUpdates()
        }
        if (purchase) {
            val receipt = requireNotNull(response.receipts.firstOrNull { it.sku == AmazonPremium.SKU && !it.isCanceled })
            assertEquals(ProductType.ENTITLED, receipt.productType)
            receiptFile.writeText(JSONObject()
                .put("userId", response.userData.userId).put("receiptId", receipt.receiptId).toString())
        }
    }
}
