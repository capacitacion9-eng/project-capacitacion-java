// =============================================================================
// TICKETERO - K6 Spike Test
// =============================================================================
// Usage: k6 run --vus 50 --duration 10s k6/spike-test.js
// =============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const ticketsCreated = new Counter('tickets_created');
const ticketErrors = new Rate('ticket_errors');
const createLatency = new Trend('create_latency', true);

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const QUEUES = ['CAJA', 'PERSONAL', 'EMPRESAS', 'GERENCIA'];

export const options = {
    vus: 50,
    duration: '10s',
    thresholds: {
        http_req_duration: ['p(95)<3000'],  // p95 < 3s during spike
        ticket_errors: ['rate<0.05'],       // < 5% errors acceptable during spike
        tickets_created: ['count>40'],      // > 40 tickets in 10s
    },
};

function generateNationalId() {
    return Math.floor(10000000 + Math.random() * 90000000).toString();
}

export default function () {
    const queue = QUEUES[Math.floor(Math.random() * QUEUES.length)];
    
    const payload = JSON.stringify({
        nationalId: generateNationalId(),
        telefono: '+569' + Math.floor(10000000 + Math.random() * 90000000),
        branchOffice: 'Spike Test Branch',
        queueType: queue,
    });

    const params = {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'SpikeTest' },
    };

    const startTime = Date.now();
    const response = http.post(`${BASE_URL}/api/tickets`, payload, params);
    const duration = Date.now() - startTime;

    createLatency.add(duration);

    const success = check(response, {
        'status is 201 or 500': (r) => r.status === 201 || r.status === 500,
        'response time < 5s': (r) => r.timings.duration < 5000,
    });

    if (response.status === 201) {
        ticketsCreated.add(1);
    } else {
        ticketErrors.add(1);
    }

    // No sleep during spike test - maximum load
}