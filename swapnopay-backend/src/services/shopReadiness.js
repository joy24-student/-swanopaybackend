import dns from 'node:dns/promises'
import https from 'node:https'
import net from 'node:net'

// Connect only to an operator-configured address, never to an arbitrary merchant URL.
export async function checkShopDns(host, expectedAddresses, lookup = dns.lookup) {
  let records
  try { records = await lookup(host, { all: true }) } catch { return false }
  const ipv4 = records.filter(r => r.family === 4 || (r.address && net.isIP(r.address) === 4))
  const pool = ipv4.length > 0 ? ipv4 : records
  return pool.length > 0 && pool.every(record => expectedAddresses.includes(record.address))
}
export function probeStore(host, address, merchant) {
  return new Promise(resolve => {
    const request = https.get({
      hostname: host, port: 443, path: '/health.php', servername: host,
      lookup: (_host, options, cb) => options?.all ? cb(null, [{ address, family: net.isIP(address) }]) : cb(null, address, net.isIP(address)),
      timeout: 15000, rejectUnauthorized: true,
      headers: { Accept: 'application/json', 'User-Agent': 'SwapnoPay-Launch-Check/1.0' },
    }, response => {
      let body = ''
      response.on('data', chunk => { body += chunk; if (body.length > 8192) response.destroy() })
      response.on('error', () => resolve({ ready: false, message: 'The storefront health response could not be read.' }))
      response.on('end', () => {
        try {
          const data = JSON.parse(body)
          resolve({ ready: response.statusCode === 200 && data.ready === true && data.merchant_id === merchant, message: 'The storefront is not ready yet.' })
        } catch { resolve({ ready: false, message: 'The domain is not serving the expected storefront.' }) }
      })
    })
    request.on('timeout', () => request.destroy(new Error('timeout')))
    request.on('error', () => resolve({ ready: false, message: 'Waiting for HTTPS and a valid storefront certificate.' }))
  })
}
