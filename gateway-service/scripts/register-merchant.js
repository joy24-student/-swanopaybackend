import pg from 'pg'
import { apiKeyDigest, generateApiKey, isProjectRef, isUuid } from '../src/security.js'

const projectRef = process.argv[2] || ''
const merchantId = process.argv[3] || ''
const databaseUrl = process.env.DATABASE_URL || ''
const pepper = process.env.API_KEY_PEPPER || ''
if (!isProjectRef(projectRef) || !isUuid(merchantId)) {
  throw new Error('Usage: npm run register-merchant -- PROJECT_REF MERCHANT_UUID')
}
if (!databaseUrl || pepper.length < 32) throw new Error('DATABASE_URL and API_KEY_PEPPER are required')

const key = generateApiKey()
const pool = new pg.Pool({ connectionString: databaseUrl, ssl: process.env.DATABASE_SSL === 'true' ? { rejectUnauthorized: true } : false })
await pool.query(
  `insert into merchant_gateway_connections(project_ref,merchant_id,api_key_digest)
   values($1,$2,$3)
   on conflict(project_ref,merchant_id) do update set api_key_digest=excluded.api_key_digest,enabled=true,updated_at=now()`,
  [projectRef, merchantId, apiKeyDigest(key, pepper)]
)
await pool.end()
process.stdout.write(`${key}\n`)
