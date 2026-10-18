package com.oniolla.interviewer.data

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.oniolla.interviewer.R
import com.oniolla.interviewer.data.billing.*
import com.oniolla.interviewer.data.model.*
import com.oniolla.interviewer.domain.*
import com.oniolla.interviewer.ui.home.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { viewModels.clear(); Dispatchers.resetMain() }

    private class Tokens : SessionTokenStorage {
        override var sessionVersion = 0L
        override var accessToken: String? = "access"
        override var refreshToken: String? = "refresh"
    }
    private class Queue : PendingPurchaseStore {
        val records = mutableListOf<PendingVerification>()
        override suspend fun all(accountId: String) = records.filter { it.accountId == accountId }
        override suspend fun put(purchase: PendingVerification) {
            records.removeAll { it.purchaseToken == purchase.purchaseToken }
            records += purchase
        }
        override suspend fun remove(accountId: String, token: String) {
            records.removeAll { it.accountId == accountId && it.purchaseToken == token }
        }
    }
    private class Store(private val trace: MutableList<String>) : BillingStore {
        override val updates = MutableSharedFlow<StoreUpdate>(extraBufferCapacity = 10)
        var owned = emptyList<StorePurchase>()
        var queryFails = false
        var acknowledgeFails = false
        var launched = 0
        var launchedPlan: ProPlan? = null
        var launchedAccount: String? = null
        var replacedToken: String? = null
        var closed = false
        override suspend fun connect() = Unit
        override suspend fun plans() = listOf(StorePlan(ProPlan.WEEKLY, "CZK 99.00", "weekly-offer"),
            StorePlan(ProPlan.MONTHLY, "CZK 299.00", "monthly-offer"))
        override suspend fun purchases(): List<StorePurchase> {
            if (queryFails) error("Play temporarily unavailable")
            return owned
        }
        override fun launch(activity: Activity, plan: ProPlan, accountId: String, oldPurchaseToken: String?): StoreUpdate.Failed? {
            launched++; launchedPlan = plan; launchedAccount = accountId; replacedToken = oldPurchaseToken
            return null
        }
        override suspend fun acknowledge(token: String) {
            trace += "ack"
            if (acknowledgeFails) error("Play temporarily unavailable")
        }
        override fun close() { closed = true }
    }
    private inner class Fixture {
        val tokens = Tokens()
        val trace = CopyOnWriteArrayList<String>()
        val requests = CopyOnWriteArrayList<Request>()
        val queue = Queue()
        val store = Store(trace)
        var plan = "FREE"
        var questions: QuestionsUsage? = null
        var basePlan: String? = null
        var verifyStatus = 200
        var backendAccount = "billing-user"
        var beforeVerifyResponse: (() -> Unit)? = null
        var subscriptionFailure: java.io.IOException? = null
        val usage: SessionUsageRepository
        init {
            val client = OkHttpClient.Builder()
                .addInterceptor(SessionAuthorizationInterceptor(tokens, "https://api.example.test/"))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val path = request.url.encodedPath
                    val code = if (path == "/billing/google/verify") verifyStatus else 200
                    val body = when (path) {
                        "/me/subscription" -> {
                            subscriptionFailure?.let { throw it }
                            response()
                        }
                        "/billing/google/verify" -> {
                            trace += "verify"
                            if (code == 200) {
                                plan = "PRO"
                                val payload = body(request)
                                basePlan = payload.get("basePlanId")?.asString ?: basePlan
                            }
                            beforeVerifyResponse?.invoke()
                            if (code < 400) response()
                            else """{"error":{"code":"PURCHASE_FAILED","message":"Verification failed"}}"""
                        }
                        else -> error("Unexpected endpoint $path")
                    }
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(code).message("Test").body(body.toResponseBody("application/json".toMediaType())).build()
                }.build()
            val api = Retrofit.Builder().baseUrl("https://api.example.test/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
            usage = SessionUsageRepository(api, SessionRefreshCoordinator(tokens, api), tokens)
        }
        private fun response() = Gson().toJson(SubscriptionResponse(
            id = "subscription", provider = "GOOGLE_PLAY", productId = PRO_PRODUCT_ID, basePlanId = basePlan,
            status = "ACTIVE", plan = plan, autoRenew = true, billingAccountId = backendAccount,
            currentPeriodEnd = "2026-11-18T08:00:00Z",
            aiEvaluations = AiEvaluationsUsage(10, 0, if (plan == "PRO") 1000 else 20,
                if (plan == "PRO") 990 else 10, null, null),
            answerReveals = AnswerRevealsUsage(5, if (plan == "PRO") 1000 else 20,
                if (plan == "PRO") 995 else 15), questions = questions))
        fun manage() = ManageSubscriptionUseCase(usage, store, queue, tokens)
        suspend fun owner(manage: ManageSubscriptionUseCase) = manage.refresh().let { manage.owner() }
        fun calls(path: String) = requests.count { it.url.encodedPath == path }
        fun body(request: Request) = Gson().fromJson(
            Buffer().also { request.body!!.writeTo(it) }.readUtf8(), JsonObject::class.java)
        fun purchase(state: PurchaseState = PurchaseState.PURCHASED, ack: Boolean = false,
            account: String? = "billing-user", token: String = "purchase-token") =
            StorePurchase(token, listOf(PRO_PRODUCT_ID), state, ack, account)
        fun vm(): SubscriptionViewModel = SubscriptionViewModel(manage(), SavedStateHandle()).also {
            viewModels.put("billing", it)
        }
        suspend fun ready(vm: SubscriptionViewModel) {
            vm.state.first { !it.isBusy && it.plans.isNotEmpty() }
        }
    }
    private suspend fun failure(block: suspend () -> Unit): Exception {
        try { block() } catch (e: Exception) { return e }
        throw AssertionError("Expected failure")
    }

    @Test fun networkFailureShowsFriendlyMessageWithoutHostOrExceptionDetails() = runTest(dispatcher) {
        val f = Fixture()
        f.subscriptionFailure = java.net.UnknownHostException("Unable to resolve host private.example.test: no address associated")
        val vm = f.vm()
        vm.setVisible(true)
        val state = vm.state.first { !it.isBusy && it.error != null }
        assertEquals(R.string.billing_error_network, state.error)
        assertFalse(state.sessionExpired)
    }

    @Test fun storeFailureDoesNotExposeDiagnosticMessage() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        vm.setVisible(true); f.ready(vm)
        f.store.updates.emit(StoreUpdate.Failed("SERVICE_DISCONNECTED: internal diagnostic"))
        val state = vm.state.first { it.error != null }
        assertEquals(R.string.billing_error_generic, state.error)
    }

    @Test fun verificationFailureDoesNotExposeBackendMessage() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        vm.setVisible(true); f.ready(vm)
        f.verifyStatus = 403
        f.store.updates.emit(StoreUpdate.Purchases(listOf(f.purchase())))
        val state = vm.state.first { !it.isBusy && it.error != null }
        assertEquals(R.string.billing_error_verification, state.error)
        assertFalse(state.subscription!!.isPro)
    }

    @Test fun purchasedIsVerifiedBeforeAcknowledgementAndUpdatesSharedCredits() = runTest(dispatcher) {
        val f = Fixture()
        val manage = f.manage()
        val owner = f.owner(manage)
        val result = manage.process(owner, listOf(f.purchase()), selectedPlan = ProPlan.MONTHLY)
        assertEquals(1, result.verified)
        assertEquals(listOf("verify", "ack"), f.trace)
        val request = f.requests.first { it.url.encodedPath == "/billing/google/verify" }
        assertEquals("POST", request.method)
        assertEquals("Bearer access", request.header("Authorization"))
        assertEquals("purchase-token", f.body(request)["purchaseToken"].asString)
        assertEquals(PRO_PRODUCT_ID, f.body(request)["productId"].asString)
        assertEquals("monthly", f.body(request)["basePlanId"].asString)
        assertEquals("PRO", f.usage.current()!!.plan)
        assertEquals(990, f.usage.current()!!.aiEvaluations!!.remaining)
        assertEquals(995, f.usage.current()!!.answerReveals!!.remaining)
        assertTrue(f.queue.records.isEmpty())
        assertEquals(1, f.calls("/me/subscription"))
        assertEquals(0, f.calls("/me/usage"))
    }

    @Test fun subscriptionQuestionLimitsReplaceDashboardCacheAndSurviveLaterDashboard() = runTest(dispatcher) {
        val f = Fixture()
        val stale = DashboardUsage("FREE", null, null, QuestionsUsage(false, 5))
        f.usage.cacheDashboard(f.usage.revision(), f.tokens.snapshot(), stale)
        f.questions = QuestionsUsage(true, 0)
        f.usage.subscription(forceRefresh = true)
        assertEquals(QuestionsUsage(true, 0), f.usage.current()!!.questions)
        f.usage.cacheDashboard(f.usage.revision(), f.tokens.snapshot(), stale)
        assertEquals(QuestionsUsage(true, 0), f.usage.current()!!.questions)
        assertEquals(QuestionsUsage(true, 0), f.usage.currentSubscription()!!.questions)

        f.questions = QuestionsUsage(false, 10)
        f.usage.refresh()
        assertEquals(QuestionsUsage(false, 10), f.usage.current()!!.questions)
        assertEquals(2, f.calls("/me/subscription"))
        assertEquals(0, f.calls("/me/usage"))
    }

    @Test fun subscriptionWithoutQuestionsDoesNotReuseOldDashboardLimits() = runTest(dispatcher) {
        val f = Fixture()
        f.usage.cacheDashboard(f.usage.revision(), f.tokens.snapshot(),
            DashboardUsage("FREE", null, null, QuestionsUsage(false, 5)))
        f.usage.refresh()
        assertNull(f.usage.current()!!.questions)
    }

    @Test fun pendingNeverVerifiesAcknowledgesOrGrantsPro() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        val report = m.process(owner, listOf(f.purchase(PurchaseState.PENDING)))
        assertEquals(1, report.pending)
        assertTrue(f.trace.isEmpty())
        assertEquals("FREE", f.usage.current()!!.plan)
        assertTrue(f.queue.records.isEmpty())
    }

    @Test fun alreadyAcknowledgedPurchaseStillVerifiesButDoesNotAcknowledgeAgain() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        m.process(owner, listOf(f.purchase(ack = true)))
        assertEquals(listOf("verify"), f.trace)
    }

    @Test fun duplicateCallbacksAreSerializedAndVerifiedOnce() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        val calls = List(3) { async { m.process(owner, listOf(f.purchase())) } }
        calls.awaitAll()
        assertEquals(1, f.calls("/billing/google/verify"))
        assertEquals(listOf("verify", "ack"), f.trace)
    }

    @Test fun networkErrorKeepsPurchaseAndNewCoordinatorRecoversIt() = runTest(dispatcher) {
        val f = Fixture(); val first = f.manage(); val owner = f.owner(first)
        f.verifyStatus = 503
        val failed = first.process(owner, listOf(f.purchase()), selectedPlan = ProPlan.WEEKLY)
        assertTrue(failed.retryNeeded)
        assertEquals("FREE", f.usage.current()!!.plan)
        assertEquals("weekly", f.queue.records.single().basePlanId)
        assertEquals(listOf("verify"), f.trace)
        f.verifyStatus = 200
        val restarted = f.manage()
        val recovered = restarted.recover(owner)
        assertEquals(1, recovered.verified)
        assertEquals("weekly", f.basePlan)
        assertTrue(f.queue.records.isEmpty())
        assertEquals(listOf("verify", "verify", "ack"), f.trace)
    }

    @Test fun pendingQueueCanVerifyWhilePlayQueryIsUnavailable() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        f.queue.put(PendingVerification(owner.accountId, "queued", PRO_PRODUCT_ID, "monthly", true))
        f.store.queryFails = true
        val report = m.recover(owner)
        assertEquals(1, report.verified)
        assertTrue(report.retryNeeded)
        assertEquals(1, f.calls("/billing/google/verify"))
    }

    @Test fun acknowledgeFailureRetainsQueueForRetryAfterServerSuccess() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        f.store.acknowledgeFails = true
        assertTrue(m.process(owner, listOf(f.purchase())).retryNeeded)
        assertEquals("PRO", f.usage.current()!!.plan)
        assertEquals(1, f.queue.records.size)
        f.store.acknowledgeFails = false
        assertFalse(m.recover(owner).retryNeeded)
        assertTrue(f.queue.records.isEmpty())
        assertEquals(listOf("verify", "ack", "verify", "ack"), f.trace)
    }

    @Test fun permanentBackendRejectionDoesNotGrantProOrAcknowledge() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        f.verifyStatus = 403
        val report = m.process(owner, listOf(f.purchase()))
        assertNotNull(report.error)
        assertFalse(report.retryNeeded)
        assertTrue(f.queue.records.isEmpty())
        assertEquals("FREE", f.usage.current()!!.plan)
        assertNotNull(m.process(owner, listOf(f.purchase())).error)
        assertEquals(listOf("verify"), f.trace)
    }

    @Test fun rejectedPurchaseStaysVisibleOnRefreshAndRestoreCanRetryIt() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        vm.setVisible(true); f.ready(vm)
        f.verifyStatus = 403
        f.store.owned = listOf(f.purchase())
        f.store.updates.emit(StoreUpdate.Purchases(f.store.owned))
        vm.state.first { !it.isBusy && it.error != null }

        vm.refresh()
        runCurrent()
        val refreshed = vm.state.first { !it.isBusy && it.error != null }
        assertEquals(R.string.billing_error_verification, refreshed.error)
        assertEquals(1, f.calls("/billing/google/verify"))
        assertFalse(refreshed.subscription!!.isPro)

        f.verifyStatus = 200
        vm.restore()
        runCurrent()
        val restored = vm.state.first { !it.isBusy && it.entitlementRevision > 0 }
        assertNull(restored.error)
        assertTrue(restored.subscription!!.isPro)
        assertEquals(2, f.calls("/billing/google/verify"))
        assertEquals(listOf("verify", "verify", "ack"), f.trace)
    }

    @Test fun automaticRecoveryDoesNotLinkAnotherAppAccountPurchase() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        f.store.owned = listOf(f.purchase(account = "other-user"), f.purchase(account = null, token = "legacy"))
        assertEquals(0, m.recover(owner).verified)
        assertTrue(f.trace.isEmpty())
    }

    @Test fun explicitRestoreLinksThroughBackendAndOmitsUnknownBasePlan() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        f.store.owned = listOf(f.purchase(account = null, ack = true))
        assertEquals(1, m.recover(owner, explicitRestore = true).verified)
        val payload = f.body(f.requests.first { it.url.encodedPath == "/billing/google/verify" })
        assertFalse(payload.has("basePlanId"))
        assertEquals(listOf("verify"), f.trace)
    }

    @Test fun explicitRestoreRechecksPreviouslyVerifiedPurchase() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        f.store.owned = listOf(f.purchase(ack = true))
        m.recover(owner)
        m.recover(owner, explicitRestore = true)
        assertEquals(2, f.calls("/billing/google/verify"))
    }

    @Test fun checkoutCanProcessPurchaseWithoutAccountIdentifierUsingItsCapturedOwner() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        assertEquals(1, m.process(owner, listOf(f.purchase(account = null)),
            selectedPlan = ProPlan.WEEKLY).verified)
    }

    @Test fun queuedPurchasesAreScopedToBillingAccount() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        f.queue.put(PendingVerification("other", "other-token", PRO_PRODUCT_ID, "weekly", false))
        assertEquals(0, m.recover(owner).verified)
        assertEquals(1, f.queue.records.size)
        assertTrue(f.trace.isEmpty())
    }

    @Test fun unrelatedProductsAreIgnored() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        m.process(owner, listOf(StorePurchase("other", listOf("unrelated"), PurchaseState.PURCHASED, false, owner.accountId)))
        assertTrue(f.trace.isEmpty())
    }

    @Test fun switchingReusesExistingTokenAndFreshBillingAccount() = runTest(dispatcher) {
        val f = Fixture().apply { plan = "PRO"; basePlan = "weekly" }
        val m = f.manage()
        f.store.owned = listOf(f.purchase(ack = true, token = "old-token"))
        val prepared = m.prepare(ProPlan.MONTHLY)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try { m.launch(controller.get(), prepared) } finally { controller.pause().stop().destroy() }
        assertEquals("billing-user", f.store.launchedAccount)
        assertEquals(ProPlan.MONTHLY, f.store.launchedPlan)
        assertEquals("old-token", f.store.replacedToken)
    }

    @Test fun currentPlanAndPendingPurchaseBlockSecondCheckout() = runTest(dispatcher) {
        val f = Fixture().apply { plan = "PRO"; basePlan = "monthly" }
        val m = f.manage()
        assertTrue(failure { m.prepare(ProPlan.MONTHLY) }.message!!.contains("already"))
        f.plan = "FREE"
        f.store.owned = listOf(f.purchase(PurchaseState.PENDING))
        assertTrue(failure { m.prepare(ProPlan.WEEKLY) }.message!!.contains("pending"))
    }

    @Test fun changedSessionCannotPublishPurchaseOrAcknowledgeIt() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        f.beforeVerifyResponse = { f.tokens.clear(); f.tokens.saveSession("other-access", "other-refresh") }
        assertTrue(failure { m.process(owner, listOf(f.purchase())) } is CancellationException)
        assertNull(f.usage.current())
        assertEquals(listOf("verify"), f.trace)
    }

    @Test fun wrongBillingAccountResponseCannotGrantPro() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        f.backendAccount = "other-user"
        assertTrue(m.process(owner, listOf(f.purchase())).retryNeeded)
        assertEquals("FREE", f.usage.current()!!.plan)
        assertEquals(listOf("verify"), f.trace)
    }

    @Test fun staleDashboardCannotOverwriteVerifiedCreditsOrFreeQuestionLimitsLeakIntoPro() = runTest(dispatcher) {
        val f = Fixture(); val m = f.manage(); val owner = f.owner(m)
        val free = f.usage.get().copy(questions = QuestionsUsage(false, 5))
        f.usage.cacheDashboard(f.usage.revision(), f.tokens.snapshot(), free)
        val dashboardStartedAt = f.usage.revision()
        m.process(owner, listOf(f.purchase()))
        f.usage.cacheDashboard(dashboardStartedAt, f.tokens.snapshot(), free)
        assertEquals("PRO", f.usage.current()!!.plan)
        assertEquals(990, f.usage.current()!!.aiEvaluations!!.remaining)
        assertNull(f.usage.current()!!.questions)
    }

    @Test fun viewModelLocksCheckoutLaunchesOnceAndCancellationKeepsFree() = runTest(dispatcher) {
        val f = Fixture(); val vm = f.vm()
        vm.setVisible(true); f.ready(vm)
        vm.select(ProPlan.WEEKLY)
        vm.buy(); vm.buy()
        vm.state.first { it.launchRequestId != null }
        val requestId = vm.state.value.launchRequestId!!
        assertTrue(vm.state.value.isBusy)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            vm.launch(controller.get(), requestId)
            vm.launch(controller.get(), requestId)
        } finally { controller.pause().stop().destroy() }
        assertEquals(1, f.store.launched)
        assertEquals(ProPlan.WEEKLY, f.store.launchedPlan)
        f.store.updates.emit(StoreUpdate.Cancelled)
        vm.state.first { !it.isBusy }
        assertEquals("FREE", vm.state.value.subscription!!.plan)
        assertNull(vm.state.value.launchRequestId)
        assertTrue(f.trace.isEmpty())
    }

    @Test fun restoreUpdatesViewModelAndCreditsWithoutLocalGrant() = runTest(dispatcher) {
        val f = Fixture(); val vm = f.vm()
        vm.setVisible(true); f.ready(vm)
        f.store.owned = listOf(f.purchase(ack = true))
        vm.restore()
        vm.state.first { !it.isBusy && it.entitlementRevision > 0 }
        assertTrue(vm.state.value.subscription!!.isPro)
        assertEquals(990, vm.state.value.subscription!!.aiEvaluations!!.remaining)
        assertTrue(vm.state.value.notice!!.contains("restored"))
        assertEquals(listOf("verify"), f.trace)
    }

    @Test fun resumeRecoversCheckoutIfPurchaseCallbackWasMissed() = runTest(dispatcher) {
        val f = Fixture(); val vm = f.vm()
        vm.setVisible(true); f.ready(vm)
        vm.select(ProPlan.WEEKLY)
        vm.buy()
        vm.state.first { it.launchRequestId != null }
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try { vm.launch(controller.get(), vm.state.value.launchRequestId!!) }
        finally { controller.pause().stop().destroy() }
        f.store.owned = listOf(f.purchase())
        vm.resume()
        vm.state.first { !it.isBusy && it.entitlementRevision > 0 }
        assertEquals("weekly", f.basePlan)
        assertTrue(vm.state.value.subscription!!.isPro)
        assertEquals(listOf("verify", "ack"), f.trace)
    }

    @Test fun foregroundRetriesPersistedVerificationAfterTemporaryFailure() = runTest(dispatcher) {
        val f = Fixture(); val vm = f.vm()
        vm.resume()
        vm.state.first { !it.isBusy && it.subscription != null }
        f.verifyStatus = 503
        f.store.updates.emit(StoreUpdate.Purchases(listOf(f.purchase())))
        vm.state.first { !it.isBusy && it.error != null }
        assertFalse(vm.state.value.subscription!!.isPro)
        assertEquals(1, f.queue.records.size)
        f.verifyStatus = 200
        advanceTimeBy(10_000)
        runCurrent()
        vm.state.first { !it.isBusy && it.entitlementRevision > 0 }
        assertTrue(vm.state.value.subscription!!.isPro)
        assertTrue(f.queue.records.isEmpty())
        vm.pause()
    }

    @Test fun pendingPurchaseDisablesCheckoutUntilPaymentCompletes() = runTest(dispatcher) {
        val f = Fixture(); val vm = f.vm()
        vm.setVisible(true); f.ready(vm)
        f.store.owned = listOf(f.purchase(PurchaseState.PENDING))
        f.store.updates.emit(StoreUpdate.Purchases(f.store.owned))
        vm.state.first { !it.isBusy && it.hasPendingPurchase }
        assertFalse(vm.state.value.canBuy)
        vm.buy()
        runCurrent()
        assertNull(vm.state.value.launchRequestId)
        assertTrue(f.trace.isEmpty())
        f.store.owned = listOf(f.purchase())
        vm.refresh()
        vm.state.first { !it.isBusy && it.entitlementRevision > 0 }
        assertFalse(vm.state.value.hasPendingPurchase)
        assertTrue(vm.state.value.subscription!!.isPro)
    }

    @Test fun selectedBasePlanSurvivesRecreationWithoutPersistingPurchaseToken() = runTest(dispatcher) {
        val f = Fixture()
        val saved = SavedStateHandle()
        val vm = SubscriptionViewModel(f.manage(), saved).also { viewModels.put("selection", it) }
        vm.select(ProPlan.WEEKLY)
        val restored = SubscriptionViewModel(f.manage(), saved).also { viewModels.put("restored", it) }
        assertEquals(ProPlan.WEEKLY, restored.state.value.selected)
        assertEquals(setOf("billingPlan"), saved.keys())
    }
}
