#!/usr/bin/env node

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const projectRef = process.env.MERCHANT_PROJECT_REF || process.env.PROJECT_REF || process.argv[2]
const accessToken = process.env.SUPABASE_ACCESS_TOKEN || process.env.SUPABASE_MANAGEMENT_TOKEN || process.env.ACCESS_TOKEN

if (!projectRef) {
  console.error('Usage: MERCHANT_PROJECT_REF=<ref> SUPABASE_ACCESS_TOKEN=<token> node scripts/reapply-merchant-self-provisioning.js')
  process.exit(1)
}

if (!accessToken) {
  console.error('Missing Supabase management access token. Set SUPABASE_ACCESS_TOKEN (or SUPABASE_MANAGEMENT_TOKEN).')
  process.exit(1)
}

const sqlCandidates = [
  process.env.MERCHANT_SQL_FILE,
  path.resolve(__dirname, '../sql/31_merchant_self_provisioning_policies.sql'),
  path.resolve(__dirname, '../supabase/migrations/31_merchant_self_provisioning_policies.sql'),
  path.resolve(process.cwd(), 'sql/31_merchant_self_provisioning_policies.sql'),
  path.resolve(process.cwd(), 'supabase/migrations/31_merchant_self_provisioning_policies.sql'),
]

const sqlPath = sqlCandidates.find((candidate) => candidate && fs.existsSync(candidate))
if (!sqlPath) {
  console.error('Could not find migration file: 31_merchant_self_provisioning_policies.sql')
  process.exit(1)
}

const sqlQuery = process.env.SQL_QUERY || fs.readFileSync(sqlPath, 'utf8')

console.log(`[merchant-migration] Applying self-provisioning SQL to project ${projectRef}...`)
console.log(`[merchant-migration] SQL source: ${sqlPath}`)

const response = await fetch(`https://api.supabase.com/v1/projects/${projectRef}/database/query`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({ query: sqlQuery }),
})

const rawText = await response.text()
let parsed = null
try {
  parsed = rawText ? JSON.parse(rawText) : null
} catch {
  parsed = null
}

if (!response.ok) {
  const errMessage = parsed?.message || parsed?.error || rawText || `HTTP ${response.status}`
  console.error(`[merchant-migration] SQL execution failed: ${errMessage}`)
  process.exit(1)
}

console.log('[merchant-migration] SUCCESS: merchant self-provisioning migration applied to live project.')
console.log(parsed ? JSON.stringify(parsed, null, 2) : 'No JSON payload returned.')
