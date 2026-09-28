import test from 'node:test'
import assert from 'node:assert/strict'
import { getDefaultProjectDbPassword, saveDefaultProjectCredentials } from '../services/provisionService.js'
import { inMemoryMerchantGatewaySettings } from '../services/adminSupabase.js'
import { ShopService } from '../services/shopService.js'

test('getDefaultProjectDbPassword generates strong deterministic default password per projectRef and preserves saved password', async () => {
  const refA = 'testprojrefalpha'
  const refB = 'testprojrefbravo'

  const passA1 = getDefaultProjectDbPassword(refA)
  const passA2 = getDefaultProjectDbPassword(refA)
  const passB = getDefaultProjectDbPassword(refB)

  assert.equal(passA1, passA2)
  assert.notEqual(passA1, passB)
  assert.match(passA1, /^SpDb_[0-9a-f]{16}!1Aa$/)

  const customPass = getDefaultProjectDbPassword(refA, 'CustomStrongPass99!')
  assert.equal(customPass, 'CustomStrongPass99!')
})

test('saveDefaultProjectCredentials persists per-merchant anon_key, db_password, and database_url automatically', async () => {
  const merchantId = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
  const projectRef = 'merchantautoproj'
  const anonKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiJ9.sig'

  const saved = await saveDefaultProjectCredentials({
    userId: merchantId,
    merchantId,
    projectRef,
    anonKey,
  })

  assert.equal(saved.projectRef, projectRef)
  assert.equal(saved.projectUrl, `https://${projectRef}.supabase.co`)
  assert.equal(saved.anonKey, anonKey)
  assert.match(saved.dbPassword, /^SpDb_[0-9a-f]{16}!1Aa$/)
  assert.ok(saved.databaseUrl.includes(`postgres.${projectRef}`))
  assert.ok(saved.databaseUrl.includes(encodeURIComponent(saved.dbPassword)))

  const mem = inMemoryMerchantGatewaySettings.get(merchantId)
  assert.ok(mem)
  assert.equal(mem.supabase_url, `https://${projectRef}.supabase.co`)
  assert.equal(mem.supabase_anon_key, anonKey)
  assert.equal(mem.db_password, saved.dbPassword)
  assert.equal(mem.database_url, saved.databaseUrl)
})

test('ShopService.resolveMerchantDbConfig dynamically resolves each merchant database without hardcoded SHOP_DATABASE_URL', async () => {
  const merchant1 = '11111111-2222-4333-8444-555555555555'
  const merchant2 = '66666666-7777-4888-8999-000000000000'

  const credsMap = new Map([
    [merchant1, {
      supabase_url: 'https://m1proj.supabase.co',
      supabase_anon_key: 'anon_key_m1',
      project_ref: 'm1proj',
      db_password: 'PassM1_Secret!',
      database_url: 'postgresql://postgres.m1proj:PassM1_Secret!@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres',
    }],
    [merchant2, {
      supabase_url: 'https://m2proj.supabase.co',
      supabase_anon_key: 'anon_key_m2',
      project_ref: 'm2proj',
      db_password: 'PassM2_Secret!',
      database_url: 'postgresql://postgres.m2proj:PassM2_Secret!@aws-0-us-east-1.pooler.supabase.com:5432/postgres',
    }],
  ])

  const config = {
    connectionString: '',
    useEmbedded: false,
    key: 'ab'.repeat(32),
    addresses: ['127.0.0.1'],
    baseDomain: 'shop.swapnopay.top',
    runtime: './data/shop-runtime',
    sites: './data/shop-sites',
    template: '../shop',
    dbHost: '127.0.0.1',
    dbPort: 5432,
    dbName: 'swapnopay_shop',
    dbUser: '',
    dbPass: '',
    sslmode: 'require',
    backendUrl: 'https://api.swapnopay.top',
  }

  const shop = new ShopService(config, {
    getMerchantCredentials: async id => credsMap.get(id) || null,
  })

  const db1 = await shop.resolveMerchantDbConfig(merchant1)
  assert.equal(db1.host, 'aws-0-ap-southeast-1.pooler.supabase.com')
  assert.equal(db1.user, 'postgres.m1proj')
  assert.equal(db1.password, 'PassM1_Secret!')
  assert.equal(db1.supabaseUrl, 'https://m1proj.supabase.co')
  assert.equal(db1.supabaseAnonKey, 'anon_key_m1')

  const db2 = await shop.resolveMerchantDbConfig(merchant2)
  assert.equal(db2.host, 'aws-0-us-east-1.pooler.supabase.com')
  assert.equal(db2.user, 'postgres.m2proj')
  assert.equal(db2.password, 'PassM2_Secret!')
  assert.equal(db2.supabaseUrl, 'https://m2proj.supabase.co')
  assert.equal(db2.supabaseAnonKey, 'anon_key_m2')
})

test('supportService persists and retrieves merchant chat messages, support tickets, and feature requests for admin helpdesk', async () => {
  const {
    saveChatMessage,
    getChatMessages,
    saveSupportTicket,
    getSupportTickets,
    saveFeatureRequest,
    getFeatureRequests,
  } = await import('../services/supportService.js')

  const testMerchantId = 'd4f197d0-4cef-4468-adf6-4fa7c4e5de77'
  const chat = await saveChatMessage({
    merchant_id: testMerchantId,
    sender: 'MERCHANT',
    message: 'Support message test for admin',
  })
  assert.ok(chat.id)
  const chats = await getChatMessages(testMerchantId)
  assert.ok(chats.some(m => m.id === chat.id))

  const ticket = await saveSupportTicket({
    merchant_id: testMerchantId,
    business_name: 'Joy Official Store',
    subject: 'Support Ticket Test',
    description: 'Testing ticket visibility in admin helpdesk',
  })
  assert.ok(ticket.id)
  const tickets = await getSupportTickets()
  assert.ok(tickets.some(t => t.id === ticket.id))

  const feature = await saveFeatureRequest({
    merchant_id: testMerchantId,
    business_name: 'Joy Official Store',
    title: 'Auto Save DB Password on OAuth',
    category: 'Integration',
    priority: 'HIGH',
    description: 'Save default DB password and anon key on Supabase OAuth.',
  })
  assert.ok(feature.id)
  const features = await getFeatureRequests()
  assert.ok(features.some(f => f.id === feature.id))

  const {
    updateSupportTicket,
    updateFeatureRequest,
    listSupportTickets,
    listFeatureRequests,
    listChatMessages,
  } = await import('../services/supportService.js')

  // Verify list all without merchant filter
  const allTickets = await listSupportTickets(null)
  assert.ok(allTickets.some(t => t.id === ticket.id))

  const allFeatures = await listFeatureRequests(null)
  assert.ok(allFeatures.some(f => f.id === feature.id))

  const allChats = await listChatMessages(null)
  assert.ok(allChats.some(m => m.id === chat.id))

  // Verify admin update on ticket
  const updatedTicket = await updateSupportTicket(ticket.id, {
    status: 'RESOLVED',
    admin_reply: 'Issue resolved by SwapnoPay engineering',
  })
  assert.equal(updatedTicket.status, 'RESOLVED')
  assert.equal(updatedTicket.admin_reply, 'Issue resolved by SwapnoPay engineering')

  // Verify admin update on feature request
  const updatedFeature = await updateFeatureRequest(feature.id, {
    status: 'PLANNED',
    admin_notes: 'Scheduled for next release milestone',
  })
  assert.equal(updatedFeature.status, 'PLANNED')
  assert.equal(updatedFeature.admin_notes, 'Scheduled for next release milestone')
})

test('getOAuthCredentials provides valid non-empty clientId, clientSecret, and redirectUri even when env vars are unset', async () => {
  const { getOAuthCredentials } = await import('./oauth.js')
  const prevId = process.env.SUPABASE_OAUTH_CLIENT_ID
  const prevSecret = process.env.SUPABASE_OAUTH_CLIENT_SECRET
  delete process.env.SUPABASE_OAUTH_CLIENT_ID
  delete process.env.SUPABASE_OAUTH_CLIENT_SECRET

  const creds = getOAuthCredentials()
  assert.equal(creds.clientId, '5d3dcd9b-1acf-4e31-96d2-d673af42a18b')
  assert.ok(creds.clientSecret.startsWith('sba_'))
  assert.equal(creds.redirectUri, 'https://api.swapnopay.top/v1/oauth/callback')

  if (prevId !== undefined) process.env.SUPABASE_OAUTH_CLIENT_ID = prevId
  if (prevSecret !== undefined) process.env.SUPABASE_OAUTH_CLIENT_SECRET = prevSecret
})

