import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const rate = Number(__ENV.RATE || 25);
const duration = __ENV.DURATION || "2m";

export const options = {
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
  scenarios: {
    job_search: {
      executor: "constant-arrival-rate",
      rate,
      timeUnit: "1s",
      duration,
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<250"],
    dropped_iterations: ["count==0"],
  },
};

const searches = [
  "?query=backend%20engineer&remotePolicy=REMOTE&size=20&sort=relevance",
  "?query=data%20engineer&location=Toronto&size=20&sort=relevance",
  "?query=software%20engineer&source=GREENHOUSE&size=20&sort=relevance",
  "?query=distributed%20services&remotePolicy=HYBRID&size=20&sort=relevance",
  "?size=20&sort=newest",
];

export default function () {
  const index = exec.scenario.iterationInTest % searches.length;
  const response = http.get(`${baseUrl}/api/v1/jobs${searches[index]}`, {
    tags: { name: "GET /api/v1/jobs" },
  });

  check(response, {
    "status is 200": (result) => result.status === 200,
    "response has content": (result) => {
      try {
        return Array.isArray(result.json("content"));
      } catch {
        return false;
      }
    },
  });
}
