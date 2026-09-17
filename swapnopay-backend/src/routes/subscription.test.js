import { test } from 'node:test'
import assert from 'node:assert/strict'
import express from 'express'
import { subscriptionRouter } from './subscription.js'
import {
  getSubscriptionConfig,
  updateSubscriptionConfig,
  getMerchantSubscriptionStatus,
  createSubscriptionOrder,
  verifyAndActivateSubscription,
  setMerchantMemorySubscription,
} from '../services/adminSupabase.js'

test('Subscription, Pricing, Anti-Piracy Billing & NID Enforcement Suite', async (t) => {
  const app = express()
  app.use(express.json())
  app.use('/v1/subscription', subscriptionRouter)

  await t.test('1. GET /v1/subscription/config returns default dynamic pricing and 3-month trial', async () => {
    const config = await getSubscriptionConfig()
    assert.equal(config.monthly_fee, 100, 'Monthly fee must be ৳100')
    assert.equal(config.quarterly_fee, 250, 'Quarterly fee must be ৳250')
    assert.equal(config.yearly_fee, 650, 'Yearly fee must be ৳650')
    assert.equal(config.trial_days, 90, 'Default trial period must be 90 days (3 months)')
    assert.equal(config.enforce_nid_verification, true, 'NID verification must be strictly enforced')
  })

  await t.test('2. Admin can dynamically update subscription fee and trial period', async () => {
    const updated = await updateSubscriptionConfig({
      monthly_fee: 120,
      quarterly_fee: 300,
      yearly_fee: 750,
      trial_days: 100
    })
    assert.equal(updated.monthly_fee, 120)
    assert.equal(updated.quarterly_fee, 300)
    assert.equal(updated.yearly_fee, 750)
    assert.equal(updated.trial_days, 100)

    // Reset back to user required defaults: 100, 250, 650, 90
    const restored = await updateSubscriptionConfig({
      monthly_fee: 100,
      quarterly_fee: 250,
      yearly_fee: 650,
      trial_days: 90
    })
    assert.equal(restored.monthly_fee, 100)
    assert.equal(restored.quarterly_fee, 250)
    assert.equal(restored.yearly_fee, 650)
    assert.equal(restored.trial_days, 90)
  })

  await t.test('3. Checkout rejects account without verified/associated NID', async () => {
    const testMerchantWithoutNid = 'test_no_nid_' + Date.now()
    await assert.rejects(
      async () => {
        await createSubscriptionOrder({
          merchantId: testMerchantWithoutNid,
          planType: 'MONTHLY',
          method: 'bKash'
        })
      },
      /NID/i
    )
  })

  await t.test('4. Subscription checkout and verification flow for account with NID', async () => {
    const testMerchantWithNid = 'merchant_verified_' + Date.now()

    // Register simulated merchant with NID
    setMerchantMemorySubscription(testMerchantWithNid, {
      nid_number: '1234567890123',
      kyc_status: 'VERIFIED'
    })

    const statusBefore = await getMerchantSubscriptionStatus(testMerchantWithNid)
    assert.equal(statusBefore.has_nid, true)

    // Checkout quarterly plan (৳250, 90 days)
    const checkoutRes = await createSubscriptionOrder({
      merchantId: testMerchantWithNid,
      planType: 'QUARTERLY',
      method: 'bKash'
    })

    assert.equal(checkoutRes.ok, true)
    assert.equal(checkoutRes.amount, 250)
    assert.equal(checkoutRes.days, 90)
    assert.ok(checkoutRes.order_id)
    assert.ok(checkoutRes.receiving_account)

    // Verify payment with TrxID
    const trxId = 'TRX99887766'
    const verifyRes = await verifyAndActivateSubscription({
      merchantId: testMerchantWithNid,
      orderId: checkoutRes.order_id,
      trxId,
      method: 'bKash',
      planType: 'QUARTERLY'
    })

    assert.equal(verifyRes.ok, true)
    assert.equal(verifyRes.subscription_status, 'ACTIVE')
    assert.equal(verifyRes.subscription_plan, 'QUARTERLY')
    assert.ok(verifyRes.subscription_expires_at)

    // Check updated status reflects active subscription
    const statusAfter = await getMerchantSubscriptionStatus(testMerchantWithNid)
    assert.equal(statusAfter.is_subscription_active, true)
    assert.equal(statusAfter.can_access_service, true)
    assert.equal(statusAfter.subscription_plan, 'QUARTERLY')

    // Restore enforce_nid_verification
    await updateSubscriptionConfig({ enforce_nid_verification: true })
  })

  await t.test('5. Rejects invalid or short TrxID on verification', async () => {
    await assert.rejects(
      async () => {
        await verifyAndActivateSubscription({
          merchantId: 'any_merchant',
          trxId: '12',
          method: 'bKash'
        })
      },
      /Transaction ID/i
    )
  })
})
