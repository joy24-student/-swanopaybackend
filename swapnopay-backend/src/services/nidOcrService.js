// SwapnoPay Backend — Bangladeshi NID & Identity Document OCR Service
// Integrates with the standalone Python Bangla & English OCR engine (HTTP service or CLI execution)

import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { randomUUID } from 'node:crypto'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'

const execFileAsync = promisify(execFile)
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const OCR_SCRIPT_PATH = path.resolve(__dirname, '../../../web/Bangla-and-English-Image-to-text-APP/src/bangla_english_ocr.py')
const OCR_SERVICE_URL = (process.env.OCR_SERVICE_URL || 'http://127.0.0.1:5055').replace(/\/$/, '')

/**
 * Attempt extraction via HTTP microservice (FastAPI server.py)
 */
async function extractViaHttp(base64Data) {
  const cleanB64 = base64Data.replace(/^data:image\/\w+;base64,/, '')
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 8000)

  try {
    const res = await fetch(`${OCR_SERVICE_URL}/api/extract`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ image_base64: cleanB64 }),
      signal: controller.signal
    })
    if (!res.ok) throw new Error(`OCR service responded with status ${res.status}`)
    const json = await res.json()
    return json
  } finally {
    clearTimeout(timeoutId)
  }
}

/**
 * Attempt extraction via direct Python CLI execution
 */
async function extractViaPythonCli(base64Data) {
  if (!fs.existsSync(OCR_SCRIPT_PATH)) {
    throw new Error(`Python OCR script not found at ${OCR_SCRIPT_PATH}`)
  }

  const cleanB64 = base64Data.replace(/^data:image\/\w+;base64,/, '')
  const buffer = Buffer.from(cleanB64, 'base64')
  if (buffer.length === 0) throw new Error('Empty image buffer')

  const tempFile = path.join(os.tmpdir(), `nid_ocr_${randomUUID()}.jpg`)
  await fs.promises.writeFile(tempFile, buffer)

  try {
    const pythonCmd = process.platform === 'win32' ? 'python' : 'python3'
    const { stdout } = await execFileAsync(pythonCmd, [OCR_SCRIPT_PATH, tempFile], {
      timeout: 25000,
      maxBuffer: 10 * 1024 * 1024
    })
    const jsonMatch = stdout.match(/\{[\s\S]*\}/)
    if (!jsonMatch) throw new Error(`No JSON found in python output: ${stdout.slice(0, 200)}`)
    const parsed = JSON.parse(jsonMatch[0])
    const fields = parsed.extracted_fields || {}
    const docInfo = parsed.document_info || {}
    return {
      ok: true,
      nid_number: fields.nid_number || null,
      name_bangla: fields.name_bangla || null,
      name_english: fields.name_english || null,
      father_name: fields.father_name || null,
      mother_name: fields.mother_name || null,
      date_of_birth: fields.date_of_birth || null,
      blood_group: fields.blood_group || null,
      doc_type: docInfo.type || 'UNKNOWN',
      confidence: docInfo.overall_confidence || 0.8,
      raw_text: parsed.metadata?.raw_text || ''
    }
  } finally {
    try {
      if (fs.existsSync(tempFile)) await fs.promises.unlink(tempFile)
    } catch {}
  }
}

/**
 * Unified Bangladeshi NID OCR Extraction
 * Tries HTTP microservice first, then Python CLI, then falls back safely.
 */
export async function extractBangladeshiNid({ frontBase64, backBase64 }) {
  let frontResult = null
  let backResult = null
  let extractionMode = 'none'

  // Process front side
  if (frontBase64) {
    try {
      frontResult = await extractViaHttp(frontBase64)
      extractionMode = 'http_microservice'
    } catch (httpErr) {
      try {
        frontResult = await extractViaPythonCli(frontBase64)
        extractionMode = 'python_cli'
      } catch (cliErr) {
        console.warn('[nid-ocr-service] Front extraction notice:', cliErr.message)
      }
    }
  }

  // Process back side (if provided, for blood group / address)
  if (backBase64) {
    try {
      backResult = await extractViaHttp(backBase64)
      if (extractionMode === 'none') extractionMode = 'http_microservice'
    } catch (httpErr) {
      try {
        backResult = await extractViaPythonCli(backBase64)
        if (extractionMode === 'none') extractionMode = 'python_cli'
      } catch (cliErr) {
        console.warn('[nid-ocr-service] Back extraction notice:', cliErr.message)
      }
    }
  }

  // Merge results
  const f = frontResult || {}
  const b = backResult || {}

  const merged = {
    nid_number: f.nid_number || b.nid_number || null,
    name_bangla: f.name_bangla || b.name_bangla || null,
    name_english: f.name_english || b.name_english || null,
    father_name: f.father_name || b.father_name || null,
    mother_name: f.mother_name || b.mother_name || null,
    date_of_birth: f.date_of_birth || b.date_of_birth || null,
    blood_group: f.blood_group || b.blood_group || null,
    doc_type: f.doc_type || b.doc_type || 'UNKNOWN',
    confidence: f.confidence || b.confidence || 0.0,
    extraction_mode: extractionMode,
    is_valid: Boolean(f.nid_number || b.nid_number)
  }

  return merged
}
