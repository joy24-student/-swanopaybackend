import net from 'node:net'
import dns from 'node:dns/promises'
import http from 'node:http'
import https from 'node:https'

/**
 * Strict Outbound URL & SSRF Validator
 * Blocks loopback, private RFC1918, link-local (cloud metadata 169.254.169.254),
 * carrier-grade NAT, and local hostnames.
 */
export function isSafeOutboundWebhookUrl(urlString) {
  if (!urlString || typeof urlString !== 'string') {
    return { safe: false, reason: 'Invalid or empty URL' }
  }

  let parsed
  try {
    parsed = new URL(urlString.trim())
  } catch (e) {
    return { safe: false, reason: 'Malformed URL' }
  }

  // Only allow standard http/https
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    return { safe: false, reason: 'Disallowed protocol: only HTTP and HTTPS are permitted' }
  }

  // Disallow user credentials in URLs (e.g. http://user:pass@host)
  if (parsed.username || parsed.password) {
    return { safe: false, reason: 'Userinfo credentials in URL are prohibited' }
  }

  let hostname = parsed.hostname.toLowerCase().replace(/^\[|\]$/g, '')

  // Remove trailing dot for FQDN
  if (hostname.endsWith('.')) {
    hostname = hostname.slice(0, -1)
  }

  // Block obvious localhost / internal hostnames
  if (
    hostname === 'localhost' ||
    hostname.endsWith('.localhost') ||
    hostname.endsWith('.local') ||
    hostname.endsWith('.internal') ||
    hostname.endsWith('.lan') ||
    hostname === 'router'
  ) {
    return { safe: false, reason: 'Local and internal hostnames are prohibited' }
  }

  // Detect numeric hex / octal IP representations
  if (/^0[xX][0-9a-fA-F]+$/.test(hostname) || /^\d+$/.test(hostname)) {
    return { safe: false, reason: 'Integer/hex encoded IP addresses are prohibited' }
  }

  // If hostname is directly an IP address
  const ipType = net.isIP(hostname)
  if (ipType === 4) {
    if (isPrivateOrReservedIPv4(hostname)) {
      return { safe: false, reason: `Private/reserved IPv4 address (${hostname}) is prohibited` }
    }
  } else if (ipType === 6) {
    if (isPrivateOrReservedIPv6(hostname)) {
      return { safe: false, reason: `Private/reserved IPv6 address (${hostname}) is prohibited` }
    }
  }

  return { safe: true, url: parsed.href }
}

/** Resolve, validate, pin, and POST to a merchant callback without following redirects. */
export async function postSafeWebhook(urlString, payload, timeoutMs = 5000, extraHeaders = {}) {
  const validation = isSafeOutboundWebhookUrl(urlString)
  if (!validation.safe) throw new Error(validation.reason)

  const target = new URL(validation.url)
  if (target.protocol !== 'https:') throw new Error('Webhook callbacks must use HTTPS')
  const records = net.isIP(target.hostname)
    ? [{ address: target.hostname, family: net.isIP(target.hostname) }]
    : await dns.lookup(target.hostname, { all: true, verbatim: true })
  if (!records.length || records.some(record =>
    record.family === 4 ? isPrivateOrReservedIPv4(record.address) : isPrivateOrReservedIPv6(record.address)
  )) {
    throw new Error('Webhook host resolves to a private or reserved network address')
  }

  const selected = records[0]
  const lookup = (_hostname, options, callback) => {
    if (options?.all) callback(null, records.map(record => ({ address: record.address, family: record.family })))
    else callback(null, selected.address, selected.family)
  }
  const transport = target.protocol === 'https:' ? https : http
  const agent = target.protocol === 'https:' ? new https.Agent({ keepAlive: false, lookup }) : new http.Agent({ keepAlive: false, lookup })
  const body = Buffer.from(JSON.stringify(payload))

  try {
    return await new Promise((resolve, reject) => {
      const request = transport.request(target, {
        method: 'POST',
        agent,
        headers: { 'content-type': 'application/json', 'content-length': body.length, ...extraHeaders },
        timeout: timeoutMs,
      }, response => {
        response.resume()
        response.on('end', () => {
          if (response.statusCode >= 300 && response.statusCode < 400) return reject(new Error('Webhook redirects are not allowed'))
          if (response.statusCode < 200 || response.statusCode >= 300) return reject(new Error(`Webhook returned HTTP ${response.statusCode}`))
          resolve({ status: response.statusCode })
        })
      })
      request.on('timeout', () => request.destroy(new Error('Webhook request timed out')))
      request.on('error', reject)
      request.end(body)
    })
  } finally {
    agent.destroy()
  }
}

function isPrivateOrReservedIPv4(ip) {
  const parts = ip.split('.').map(Number)
  if (parts.length !== 4 || parts.some(p => isNaN(p) || p < 0 || p > 255)) {
    return true
  }

  const [a, b] = parts

  // 127.0.0.0/8 (Loopback)
  if (a === 127) return true

  // 0.0.0.0/8 (Current network)
  if (a === 0) return true

  // 10.0.0.0/8 (Private RFC1918)
  if (a === 10) return true

  // 172.16.0.0/12 (Private RFC1918: 172.16.x.x - 172.31.x.x)
  if (a === 172 && b >= 16 && b <= 31) return true

  // 192.168.0.0/16 (Private RFC1918)
  if (a === 192 && b === 168) return true

  // 169.254.0.0/16 (Link-local & Cloud Metadata: 169.254.169.254)
  if (a === 169 && b === 254) return true

  // 100.64.0.0/10 (Carrier Grade NAT)
  if (a === 100 && b >= 64 && b <= 127) return true

  // 192.0.2.0/24 (TEST-NET-1)
  if (a === 192 && b === 0 && parts[2] === 2) return true
  if (a === 192 && b === 0 && parts[2] === 0) return true
  if (a === 192 && b === 88 && parts[2] === 99) return true

  // 198.51.100.0/24 (TEST-NET-2)
  if (a === 198 && b === 51 && parts[2] === 100) return true
  if (a === 198 && (b === 18 || b === 19)) return true

  // 203.0.113.0/24 (TEST-NET-3)
  if (a === 203 && b === 0 && parts[2] === 113) return true

  // 224.0.0.0/4 (Multicast)
  if (a >= 224 && a <= 239) return true

  // 240.0.0.0/4 (Reserved)
  if (a >= 240) return true

  return false
}

function isPrivateOrReservedIPv6(ip) {
  const normalized = ip.toLowerCase()

  // Loopback (::1)
  if (normalized === '::1' || normalized === '0:0:0:0:0:0:0:1') return true

  // Unspecified (::)
  if (normalized === '::' || normalized === '0:0:0:0:0:0:0:0') return true
  if (normalized.startsWith('ff') || normalized.startsWith('2001:db8:') || normalized.startsWith('2001:10:')) return true

  // Unique Local Address (fc00::/7)
  if (normalized.startsWith('fc') || normalized.startsWith('fd')) return true

  // Link-Local (fe80::/10)
  if (normalized.startsWith('fe8') || normalized.startsWith('fe9') || normalized.startsWith('fea') || normalized.startsWith('feb')) return true

  // IPv4-mapped IPv6 (::ffff:127.0.0.1 or ::ffff:7f00:1)
  if (normalized.includes('::ffff:')) {
    const after = normalized.split('::ffff:')[1]
    if (net.isIPv4(after)) {
      return isPrivateOrReservedIPv4(after)
    }
    return true
  }

  return false
}
