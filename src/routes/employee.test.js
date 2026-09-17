// SwapnoPay Backend — Employee App & Live Staff Monitor Tests
import { test, describe, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import express from 'express'
import employeeRouter, { resetEmployeeMemoryState, seedEmployeeMemory } from './employee.js'

describe('Employee App, Staff Portal & Merchant Live Monitor Suite', () => {
  let app

  beforeEach(() => {
    resetEmployeeMemoryState()
    app = express()
    app.use(express.json())
    app.use('/v1/employee', employeeRouter)
  })

  // Helper to make mock requests
  async function makeRequest(path, method = 'GET', body = null) {
    const server = app.listen(0)
    const port = server.address().port
    const url = `http://127.0.0.1:${port}${path}`

    try {
      const options = {
        method,
        headers: { 'Content-Type': 'application/json' }
      }
      if (body) {
        options.body = JSON.stringify(body)
      }
      const res = await fetch(url, options)
      const data = await res.json()
      return { status: res.status, data }
    } finally {
      server.close()
    }
  }

  test('1. POST /v1/employee/pair — Pairs via QR code and returns session and merchant config', async () => {
    const qrPayload = {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_001',
      employee_name: 'আরিফ আহমেদ (Arif Ahmed)',
      employee_role: 'Sales Executive'
    }

    const res = await makeRequest('/v1/employee/pair', 'POST', qrPayload)
    assert.equal(res.status, 200)
    assert.equal(res.data.ok, true)
    assert.ok(res.data.session_token.startsWith('emp_sess_'))
    assert.equal(res.data.employee.id, 'emp_001')
    assert.equal(res.data.employee.status, 'Active')
    assert.ok(res.data.merchant.gateway_methods.bKash)
    assert.ok(res.data.merchant.gateway_methods.Nagad)
  })

  test('2. GET /v1/employee/status — Returns active status or 403 when revoked', async () => {
    // Seed active employee
    seedEmployeeMemory('merch_test_999', {
      id: 'emp_001',
      name: 'Arif Ahmed',
      status: 'Active'
    })

    const activeRes = await makeRequest('/v1/employee/status?merchant_id=merch_test_999&employee_id=emp_001')
    assert.equal(activeRes.status, 200)
    assert.equal(activeRes.data.active, true)
    assert.equal(activeRes.data.status, 'Active')

    // Revoke employee
    await makeRequest('/v1/employee/revoke', 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_001'
    })

    // Now status should return 403 Forbidden
    const revokedRes = await makeRequest('/v1/employee/status?merchant_id=merch_test_999&employee_id=emp_001')
    assert.equal(revokedRes.status, 403)
    assert.equal(revokedRes.data.active, false)
    assert.equal(revokedRes.data.status, 'REVOKED')
  })

  test('3. POST /v1/employee/sales/create — Creates Cash sale with PENDING_CASH_CONFIRMATION', async () => {
    seedEmployeeMemory('merch_test_999', {
      id: 'emp_001',
      name: 'Arif Ahmed',
      status: 'Active'
    })

    const salePayload = {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_001',
      employee_name: 'Arif Ahmed',
      customer_name: 'কবির হোসেন (Kabir Hossain)',
      customer_phone: '01711223344',
      items: [
        { product_id: 'prod_1', name: 'Cotton Shirt', quantity: 2, unit_price: 600, line_total: 1200 }
      ],
      subtotal: 1200,
      discount: 100,
      net_total: 1100,
      payment_type: 'Cash'
    }

    const res = await makeRequest('/v1/employee/sales/create', 'POST', salePayload)
    assert.equal(res.status, 200)
    assert.equal(res.data.ok, true)
    assert.equal(res.data.sale.status, 'PENDING_CASH_CONFIRMATION')
    assert.equal(res.data.sale.net_total, 1100)
    assert.ok(res.data.sale.invoice_no.startsWith('INV-'))
  })

  test('4. POST /v1/employee/monitor/finalize-cash — Merchant 1-Tap finalizes cash payment', async () => {
    seedEmployeeMemory('merch_test_999', {
      id: 'emp_001',
      name: 'Arif Ahmed',
      status: 'Active'
    })

    // Create cash sale
    const saleRes = await makeRequest('/v1/employee/sales/create', 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_001',
      net_total: 850,
      payment_type: 'Cash'
    })
    const saleId = saleRes.data.sale.id

    // Check monitor feed before finalizing
    const feedBefore = await makeRequest('/v1/employee/monitor/feed?merchant_id=merch_test_999')
    assert.equal(feedBefore.status, 200)
    assert.equal(feedBefore.data.stats.pending_cash_sales_count, 1)
    assert.equal(feedBefore.data.pending_cash_sales[0].id, saleId)

    // Merchant finalizes cash payment
    const finalizeRes = await makeRequest('/v1/employee/monitor/finalize-cash', 'POST', {
      merchant_id: 'merch_test_999',
      sale_id: saleId
    })
    assert.equal(finalizeRes.status, 200)
    assert.equal(finalizeRes.data.ok, true)
    assert.equal(finalizeRes.data.sale.status, 'PAID')
    assert.equal(finalizeRes.data.sale.finalized_by, 'MERCHANT_CASH_APPROVAL')

    // Feed after finalizing should have 0 pending
    const feedAfter = await makeRequest('/v1/employee/monitor/feed?merchant_id=merch_test_999')
    assert.equal(feedAfter.data.stats.pending_cash_sales_count, 0)
    assert.equal(feedAfter.data.stats.total_staff_sales_today, 850)
  })

  test('5. POST /v1/employee/sales/:id/verify-mfs — Instant MFS (bKash/Nagad/Rocket/Upay) payment match', async () => {
    seedEmployeeMemory('merch_test_999', {
      id: 'emp_002',
      name: 'ফারজানা (Farzana)',
      status: 'Active'
    })

    // Create MFS sale
    const saleRes = await makeRequest('/v1/employee/sales/create', 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_002',
      net_total: 1500,
      payment_type: 'bKash'
    })
    const saleId = saleRes.data.sale.id
    assert.equal(saleRes.data.sale.status, 'MFS_MATCHING')

    // Verify MFS TrxID
    const verifyRes = await makeRequest(`/v1/employee/sales/${saleId}/verify-mfs`, 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_002',
      trx_id: 'BK7890XYZ',
      payment_method: 'bKash'
    })
    assert.equal(verifyRes.status, 200)
    assert.equal(verifyRes.data.ok, true)
    assert.equal(verifyRes.data.sale.status, 'PAID')
    assert.equal(verifyRes.data.sale.trx_id, 'BK7890XYZ')
  })

  test('6. Revoked employee cannot create new sales', async () => {
    seedEmployeeMemory('merch_test_999', {
      id: 'emp_003',
      name: 'হাসান (Hasan)',
      status: 'Active'
    })

    // Revoke access
    await makeRequest('/v1/employee/revoke', 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_003'
    })

    // Attempt to create sale
    const res = await makeRequest('/v1/employee/sales/create', 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_003',
      net_total: 500,
      payment_type: 'Cash'
    })
    assert.equal(res.status, 403)
    assert.equal(res.data.active, false)
    assert.equal(res.data.status, 'REVOKED')
  })

  test('7. POST /v1/employee/token/generate — Generates encrypted pairing token with staff PIN', async () => {
    seedEmployeeMemory('merch_test_999', {
      id: 'emp_sec_101',
      name: 'Tanvir Hossain',
      role: 'Cashier',
      status: 'Active'
    })

    const genRes = await makeRequest('/v1/employee/token/generate', 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_sec_101',
      staff_pin: '4321',
      expires_in_hours: 12
    })

    assert.equal(genRes.status, 200)
    assert.equal(genRes.data.ok, true)
    assert.ok(genRes.data.encrypted_token.startsWith('SWAPNO_SEC1.'))
    assert.equal(genRes.data.requires_pin, true)
    assert.ok(genRes.data.expires_at)
  })

  test('8. POST /v1/employee/pair — Pairs successfully with encrypted token + valid PIN', async () => {
    seedEmployeeMemory('merch_test_999', {
      id: 'emp_sec_102',
      name: 'Sadia Islam',
      role: 'Sales Staff',
      status: 'Active'
    })

    const genRes = await makeRequest('/v1/employee/token/generate', 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_sec_102',
      staff_pin: '9876'
    })
    const encryptedToken = genRes.data.encrypted_token

    // Attempt pair with correct PIN
    const pairRes = await makeRequest('/v1/employee/pair', 'POST', {
      encrypted_token: encryptedToken,
      staff_pin: '9876'
    })
    assert.equal(pairRes.status, 200)
    assert.equal(pairRes.data.ok, true)
    assert.equal(pairRes.data.employee.id, 'emp_sec_102')
    assert.ok(pairRes.data.session_token.startsWith('emp_sess_'))
  })

  test('9. POST /v1/employee/pair — Rejects pairing with wrong staff PIN or tampered token', async () => {
    seedEmployeeMemory('merch_test_999', {
      id: 'emp_sec_103',
      name: 'Nadim',
      status: 'Active'
    })

    const genRes = await makeRequest('/v1/employee/token/generate', 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_sec_103',
      staff_pin: '5555'
    })
    const token = genRes.data.encrypted_token

    // Wrong PIN
    const wrongPinRes = await makeRequest('/v1/employee/pair', 'POST', {
      encrypted_token: token,
      staff_pin: '0000'
    })
    assert.equal(wrongPinRes.status, 401)
    assert.match(wrongPinRes.data.error, /পিন/i)

    // Tampered token string
    const tamperedToken = token.slice(0, -4) + 'abcd'
    const tamperedRes = await makeRequest('/v1/employee/pair', 'POST', {
      encrypted_token: tamperedToken,
      staff_pin: '5555'
    })
    assert.equal(tamperedRes.status, 401)
    assert.match(tamperedRes.data.error, /সিগনেচার|ফরম্যাট/i)
  })

  test('10. POST /v1/employee/pair — Prevents replay attack using one-time nonce', async () => {
    seedEmployeeMemory('merch_test_999', {
      id: 'emp_sec_104',
      name: 'Replay Target',
      status: 'Active'
    })

    const genRes = await makeRequest('/v1/employee/token/generate', 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_sec_104'
    })
    const token = genRes.data.encrypted_token

    // First pairing should succeed
    const firstPair = await makeRequest('/v1/employee/pair', 'POST', { encrypted_token: token })
    assert.equal(firstPair.status, 200)

    // Second pairing with exact same token must fail (nonce replay prevention)
    const secondPair = await makeRequest('/v1/employee/pair', 'POST', { encrypted_token: token })
    assert.equal(secondPair.status, 401)
    assert.match(secondPair.data.error, /ইতিমধ্যে ব্যবহৃত/i)
  })

  test('11. Instant Revocation purges sessions & Restore restores employee access', async () => {
    seedEmployeeMemory('merch_test_999', {
      id: 'emp_sec_105',
      name: 'Active Staff',
      status: 'Active'
    })

    // 1. Employee pairs and receives session
    const pairRes = await makeRequest('/v1/employee/pair', 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_sec_105'
    })
    assert.equal(pairRes.status, 200)
    assert.ok(pairRes.data.session_token)

    // 2. Merchant revokes employee access with reason
    const revokeRes = await makeRequest('/v1/employee/revoke', 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_sec_105',
      reason: 'Suspicious activities noticed'
    })
    assert.equal(revokeRes.status, 200)
    assert.equal(revokeRes.data.status, 'REVOKED')
    assert.equal(revokeRes.data.purged_sessions, 1)

    // 3. Status check returns 403 Forbidden
    const statusRes = await makeRequest('/v1/employee/status?merchant_id=merch_test_999&employee_id=emp_sec_105')
    assert.equal(statusRes.status, 403)
    assert.equal(statusRes.data.status, 'REVOKED')

    // 4. Merchant restores / unlocks employee
    const restoreRes = await makeRequest('/v1/employee/restore', 'POST', {
      merchant_id: 'merch_test_999',
      employee_id: 'emp_sec_105'
    })
    assert.equal(restoreRes.status, 200)
    assert.equal(restoreRes.data.status, 'Active')

    // 5. Status check is now active again
    const activeAgain = await makeRequest('/v1/employee/status?merchant_id=merch_test_999&employee_id=emp_sec_105')
    assert.equal(activeAgain.status, 200)
    assert.equal(activeAgain.data.active, true)
    assert.equal(activeAgain.data.status, 'Active')
  })
})


