// SwapnoPay Developer Portal Core Controller
// Features: Navigation controls, interactive code generators, webhooks simulators, and live console logs.

let activeTab = 'dashboard';
let activeSdkLang = 'node';
let environment = 'sandbox';

// Default templates for API Explorer
const explorerTemplates = {
  '/functions/v1/create-order': {
    tran_id: "ORD-9912",
    amount: 1500.00,
    cus_phone: "01712345678",
    cus_email: "customer@gmail.com",
    callback_url: "https://mystore.com/webhook",
    payment_method: "bKash"
  },
  '/functions/v1/resolve-appeal': {
    appeal_id: "550e8400-e29b-41d4-a716-446655440000",
    action: "APPROVED"
  }
};

// Initialization
document.addEventListener('DOMContentLoaded', () => {
  loadExplorerTemplate();
  updateSdkCodes();
});

// View switcher
function switchView(viewId, element) {
  // Remove active from all items
  document.querySelectorAll('.menu-item').forEach(item => item.classList.remove('active'));
  document.querySelectorAll('.panel-view').forEach(view => view.classList.remove('active'));

  // Set active
  element.classList.add('active');
  const targetView = document.getElementById(`view-${viewId}`);
  if (targetView) targetView.classList.add('active');
  
  activeTab = viewId;
  logToConsole('INFO', `Switched workspace viewport to: ${viewId.toUpperCase()}`);
}

// Environment Switcher
function setEnv(env) {
  environment = env;
  document.querySelectorAll('.env-btn').forEach(btn => btn.classList.remove('active'));
  
  if (env === 'sandbox') {
    document.querySelector('.env-btn.sandbox').classList.add('active');
    logToConsole('WARN', 'Console workspace shifted to Sandbox mode. Payment updates will be simulated.');
  } else {
    document.querySelector('.env-btn.live').classList.add('active');
    logToConsole('OK', 'Console workspace shifted to LIVE mode. Authenticated endpoint routes are enabled.');
  }
}

// Logger terminal helper
function logToConsole(type, message) {
  const consoleEl = document.getElementById('terminal-console');
  if (!consoleEl) return;

  const timestamp = new Date().toTimeString().split(' ')[0];
  
  let typeSpan = '';
  if (type === 'OK') typeSpan = `<span class="log-ok">[OK]</span>`;
  else if (type === 'WARN') typeSpan = `<span class="log-warn">[WARN]</span>`;
  else if (type === 'ERROR') typeSpan = `<span class="log-err">[ERROR]</span>`;
  else typeSpan = `<span class="log-time">[INFO]</span>`;

  const logLine = document.createElement('div');
  logLine.className = 'terminal-line';
  logLine.innerHTML = `<span class="log-time">[${timestamp}]</span> ${typeSpan} <span>${message}</span>`;
  
  consoleEl.appendChild(logLine);
  consoleEl.scrollTop = consoleEl.scrollHeight;
}

// API Explorer Template loader
function loadExplorerTemplate() {
  const path = document.getElementById('exp-path').value;
  const bodyEl = document.getElementById('exp-body');
  if (bodyEl && explorerTemplates[path]) {
    bodyEl.value = JSON.stringify(explorerTemplates[path], null, 2);
  }
}

// Execute API requests from API Explorer
function executeApiRequest() {
  const path = document.getElementById('exp-path').value;
  const bodyVal = document.getElementById('exp-body').value;
  const authVal = document.getElementById('exp-auth').value;
  const responseEl = document.getElementById('exp-response');

  logToConsole('INFO', `Executing API Call POST: ${path}...`);
  responseEl.innerText = "Processing request on SwapnoPay edge gateways...";

  const startTime = Date.now();

  setTimeout(() => {
    try {
      const parsedBody = JSON.parse(bodyVal);
      const elapsed = Date.now() - startTime;

      if (path === '/functions/v1/create-order') {
        if (!parsedBody.tran_id || !parsedBody.amount || !parsedBody.cus_phone) {
          responseEl.innerHTML = JSON.stringify({ error: "Missing required fields" }, null, 2);
          logToConsole('ERROR', `Request rejected by gateway: Missing parameters. Response time: ${elapsed}ms`);
          return;
        }
        
        const successResponse = {
          status: "SUCCESS",
          order_id: "77a8b6f3-118e-4a6f-998c-ec8844d18bb2",
          merchantNumber: "01784992118",
          expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString()
        };
        responseEl.innerHTML = JSON.stringify(successResponse, null, 2);
        logToConsole('OK', `POST ${path} completed successfully in ${elapsed}ms.`);
        
        // Update wizard step progress
        document.getElementById('wiz-step2').classList.add('completed');
        document.getElementById('wiz-step2-status').innerText = 'Step complete: Order registered successfully';
      } else {
        // resolve-appeal path
        const successResponse = {
          status: "SUCCESS",
          message: "Appeal dispute successfully updated to APPROVED"
        };
        responseEl.innerHTML = JSON.stringify(successResponse, null, 2);
        logToConsole('OK', `POST ${path} completed successfully in ${elapsed}ms.`);
      }
    } catch (e) {
      responseEl.innerText = `Invalid JSON Body format: ${e.message}`;
      logToConsole('ERROR', `Execution syntax error: ${e.message}`);
    }
  }, 600);
}

// Dynamic SDK code updates
function updateSdkCodes() {
  const amount = document.getElementById('sdk-amount').value.replace(/,/g, '');
  const orderId = document.getElementById('sdk-order-id').value;
  const phone = document.getElementById('sdk-phone').value;
  const callback = document.getElementById('sdk-callback').value;

  const codeBlocks = {
    node: `const { SwapnoPayClient } = require('@swapnopay/sdk');

const client = new SwapnoPayClient({
  secretKey: 'sk_live_swapnopay_secret_key_here',
  supabaseUrl: 'https://swapnopay-enterprise.supabase.co'
});

// Register order
client.createOrder({
  tran_id: "${orderId}",
  amount: ${parseFloat(amount) || 0.0},
  cus_phone: "${phone}",
  callback_url: "${callback}"
})
.then(res => console.log('Checkout URL params:', res))
.catch(err => console.error('Verification failed:', err.message));`,

    php: `<?php
require_once 'vendor/autoload.php';

use SwapnoPay\\SwapnoPayClient;

$client = new SwapnoPayClient([
    'secretKey'   => 'sk_live_swapnopay_secret_key_here',
    'supabaseUrl' => 'https://swapnopay-enterprise.supabase.co'
]);

$response = $client->createOrder([
    'tran_id'      => '${orderId}',
    'amount'       => ${parseFloat(amount) || 0.0},
    'cus_phone'    => '${phone}',
    'callback_url' => '${callback}'
]);

print_r($response);`,

    python: `from swapnopay import SwapnoPayClient

client = SwapnoPayClient(
    secret_key="sk_live_swapnopay_secret_key_here",
    supabase_url="https://swapnopay-enterprise.supabase.co"
)

response = client.create_order(
    tran_id="${orderId}",
    amount=${parseFloat(amount) || 0.0},
    cus_phone="${phone}",
    callback_url="${callback}"
)

print(response)`,

    go: `package main

import (
	"fmt"
	"time"
	"github.com/swapnopay/sdk-go"
)

func main() {
	client, _ := swapnopay.NewClient(swapnopay.Config{
		SecretKey:   "sk_live_swapnopay_secret_key_here",
		SupabaseURL: "https://swapnopay-enterprise.supabase.co",
	})

	res, _ := client.CreateOrder(swapnopay.CreateOrderRequest{
		TranID:      "${orderId}",
		Amount:      ${parseFloat(amount) || 0.0},
		CusPhone:    "${phone}",
		CallbackURL: "${callback}",
	})

	fmt.Printf("Registered Checkouts ID: %s\\n", res.OrderID)
}`,

    curl: `curl -X POST https://swapnopay-enterprise.supabase.co/functions/v1/create-order \\
  -H "Content-Type: application/json" \\
  -H "apikey: sk_live_swapnopay_secret_key_here" \\
  -H "Authorization: Bearer sk_live_swapnopay_secret_key_here" \\
  -d '{
    "merchantSecret": "sk_live_swapnopay_secret_key_here",
    "tran_id": "${orderId}",
    "amount": ${parseFloat(amount) || 0.0},
    "cus_phone": "${phone}",
    "callback_url": "${callback}"
  }'`
  };

  const block = document.getElementById('sdk-code-block');
  if (block) {
    block.innerText = codeBlocks[activeSdkLang];
  }
}

// SDK switch
function switchSdkLang(lang, element) {
  document.querySelectorAll('.code-tabs .tab-btn').forEach(btn => btn.classList.remove('active'));
  element.classList.add('active');
  activeSdkLang = lang;
  updateSdkCodes();
}

// Clipboard copy helper
function copySdkCode() {
  const block = document.getElementById('sdk-code-block');
  if (block) {
    navigator.clipboard.writeText(block.innerText);
    logToConsole('INFO', `Copied ${activeSdkLang.toUpperCase()} code integration script to clipboard.`);
  }
}

// Webhook simulation handler
function triggerMfsSim(mfs) {
  logToConsole('INFO', `Initializing simulation trigger event: ${mfs.toUpperCase()} Payment...`);
  
  // Transition Timeline step 2
  const timelineStep2 = document.getElementById('time-step2');
  const timelineStep3 = document.getElementById('time-step3');
  const timelineStep4 = document.getElementById('time-step4');
  
  if (timelineStep2) {
    timelineStep2.classList.add('active');
    timelineStep2.querySelector('.timeline-time').innerText = `Intercepted SMS at ${new Date().toLocaleTimeString()}`;
  }

  // Choose fake params based on provider
  let mockSmsBody = '';
  let mockTrxId = 'TXN' + Math.floor(Math.random() * 900000 + 100000);
  
  if (mfs === 'bKash') {
    mockSmsBody = `You have received Tk 1500.00 from 01712345678. Fee Tk 0.00. Balance Tk 4500.00. TrxID ${mockTrxId} at 17/07/2026 21:00`;
  } else if (mfs === 'bKash-cashin') {
    mockSmsBody = `Cash In Tk 1500.00 from 01712345678 successful. Fee Tk 0.00. Balance Tk 4500.00. TrxID ${mockTrxId} at 17/07/2026 21:00`;
  } else if (mfs === 'Nagad') {
    mockSmsBody = `Money Received. Amount: Tk 1500.00 Sender: 01712345678 Ref: N/A TxnID: ${mockTrxId} Balance: Tk 4500.00 17/07/2026 21:00`;
  } else if (mfs === 'Rocket') {
    mockSmsBody = `Cash-In from A/C: ***1234 Tk1500.00 Fee: Tk.00, Your A/C Balance: Tk4500.00.TxnId:${mockTrxId} Date:17-JUL-26 09:00:00 pm.`;
  } else if (mfs === 'fail') {
    logToConsole('WARN', 'Order expiration reached: ORD-7845 cancelled. Timeout occurred.');
    return;
  } else if (mfs === 'appeal') {
    logToConsole('INFO', 'User submitted Manual dispute appeal form for TrxID BK9912A34.');
    return;
  }

  setTimeout(() => {
    logToConsole('OK', `Parsed raw SMS matching successfully. Amount: 1,500 BDT, Payer: 01712345678, TrxID: ${mockTrxId}.`);
    if (timelineStep3) {
      timelineStep3.classList.add('active');
      timelineStep3.querySelector('.timeline-time').innerText = `Matched at ${new Date().toLocaleTimeString()}`;
    }
  }, 1000);

  setTimeout(() => {
    logToConsole('OK', `Webhook signature verification completed successfully. Dispatched callback.`);
    
    if (timelineStep4) {
      timelineStep4.classList.add('completed');
      timelineStep4.querySelector('.timeline-time').innerText = `Delivered Status: HTTP 200 OK`;
    }

    // Append to Webhooks ledger table
    const tableEl = document.getElementById('webhook-logs-table');
    if (tableEl) {
      const timestamp = new Date().toLocaleTimeString();
      const newRow = document.createElement('tr');
      newRow.innerHTML = `
        <td>${timestamp}</td>
        <td>https://mystore.com/webhook</td>
        <td><code>payment.success</code></td>
        <td><span class="status-badge ok">200 OK</span></td>
        <td>84ms</td>
      `;
      // Clear empty placeholder if exists
      if (tableEl.innerHTML.includes('No webhook events')) {
        tableEl.innerHTML = '';
      }
      tableEl.insertBefore(newRow, tableEl.firstChild);
    }

    // Mark Onboarding Wizard Step 3 & 4
    document.getElementById('wiz-step3').classList.add('completed');
    document.getElementById('wiz-step3-status').innerText = 'Step complete: Simulated MFS event processed';
    
    document.getElementById('wiz-step4').classList.add('completed');
    document.getElementById('wiz-step4-status').innerText = 'Step complete: Webhook signature verified & dispatched';
  }, 2000);
}
