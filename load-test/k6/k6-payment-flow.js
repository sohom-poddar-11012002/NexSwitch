import http from 'k6/http';
import { check, sleep } from 'k6';

// LEARN: k6 virtual users simulate concurrent ISO 8583 clients; ramping profile
//        matches real POS traffic — burst at 09:00 IST, trough at 03:00 IST.
//        p(95)<500 means 95th percentile latency must be under 500ms — Visa's SLA target.
export const options = {
  stages: [
    { duration: '1m', target: 50 },   // ramp up to 50 VUs over 1 minute
    { duration: '3m', target: 500 },  // ramp up to 500 VUs (peak morning traffic)
    { duration: '1m', target: 0 },    // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // Visa SLA: 95th percentile under 500ms
    http_req_failed:   ['rate<0.01'],  // Error rate under 1%
  },
};

export default function () {
  const payload = JSON.stringify({
    terminalId: `TERM${Math.floor(Math.random() * 10000).toString().padStart(4, '0')}`,
    merchantId: `MERCH${Math.floor(Math.random() * 500).toString().padStart(7, '0')}`,
    amount: (Math.random() * 10000 + 100).toFixed(2),
    currency: 'INR',
    pan: '4111111111111111',
    stan: Math.floor(Math.random() * 999999).toString().padStart(6, '0'),
  });

  const res = http.post('http://localhost:8080/api/v1/payments/authorize', payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': 'dev-api-key-change-me',
    },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response has result': (r) => r.json('responseCode') !== undefined,
  });

  // LEARN: 100ms think time between requests simulates a cashier processing time.
  //        Without sleep, k6 hammers the server with no pause, which is unrealistic.
  sleep(0.1);
}
