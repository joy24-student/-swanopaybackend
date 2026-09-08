package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.LedgerTransactionEntity
import com.example.data.local.ProductItemEntity
import com.example.data.local.StockTransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseIsolationTest {

    @Test
    fun `tenant queries isolate records and reassignment is transactional`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.appDao()
            dao.insertCustomer(customer("customer-a", "merchant-a"))
            dao.insertCustomer(customer("customer-b", "merchant-b"))

            assertEquals(listOf("customer-a"), dao.observeCustomers("merchant-a").first().map { it.id })
            assertEquals(listOf("customer-b"), dao.observeCustomers("merchant-b").first().map { it.id })

            dao.reassignMerchantData("merchant-a", "merchant-c")

            assertEquals(emptyList<CustomerEntity>(), dao.observeCustomers("merchant-a").first())
            assertEquals(listOf("customer-a"), dao.observeCustomers("merchant-c").first().map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun `cross tenant ledger mutation rolls back the entire transaction`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.appDao()
            dao.insertCustomer(customer("customer-a", "merchant-a"))

            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    dao.recordLedgerTransactionAtomic(
                        LedgerTransactionEntity(
                            id = "ledger-b",
                            merchantId = "merchant-b",
                            customerId = "customer-a",
                            type = "credit",
                            amount = 100.0
                        )
                    )
                }
            }

            assertEquals(emptyList<LedgerTransactionEntity>(), dao.observeLedgerTransactions("merchant-b").first())
            assertEquals(0.0, dao.getCustomerById("customer-a", "merchant-a")?.currentBalance ?: -1.0, 0.0)
        } finally {
            database.close()
        }
    }

    @Test
    fun `stock transaction cannot create negative inventory`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.appDao()
            dao.insertProduct(
                ProductItemEntity(
                    id = "product-a",
                    merchantId = "merchant-a",
                    name = "Product",
                    stockQuantity = 5.0
                )
            )

            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    dao.recordStockTransactionsAtomic(
                        listOf(
                            StockTransactionEntity(
                                id = "stock-out",
                                merchantId = "merchant-a",
                                productId = "product-a",
                                type = "out",
                                quantity = 6.0
                            )
                        )
                    )
                }
            }

            assertEquals(5.0, dao.getProductById("product-a", "merchant-a")?.stockQuantity ?: -1.0, 0.0)
            assertEquals(emptyList<StockTransactionEntity>(), dao.observeStockTransactions("merchant-a").first())
        } finally {
            database.close()
        }
    }

    @Test
    fun `customer credit transaction correctly updates balance atomically`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.appDao()
            val merchantId = "merchant-test"
            val customerId = "cust-101"
            dao.insertCustomer(customer(customerId, merchantId))

            dao.recordLedgerTransactionAtomic(
                LedgerTransactionEntity(
                    id = "tx-1",
                    merchantId = merchantId,
                    customerId = customerId,
                    type = "credit",
                    amount = 500.0,
                    note = "Test credit"
                )
            )

            val updatedCustomer = dao.getCustomerById(customerId, merchantId)
            assertEquals(500.0, updatedCustomer?.currentBalance ?: 0.0, 0.0)
            val transactions = dao.observeLedgerTransactions(merchantId).first()
            assertEquals(1, transactions.size)
            assertEquals("tx-1", transactions[0].id)
        } finally {
            database.close()
        }
    }

    private fun customer(id: String, merchantId: String) = CustomerEntity(
        id = id,
        merchantId = merchantId,
        name = id,
        phone = "01700000000",
        email = null,
        address = null,
        openingBalance = 0.0,
        currentBalance = 0.0
    )
}
