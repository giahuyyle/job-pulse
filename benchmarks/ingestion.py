import argparse
import json
import sys
import time
import urllib.error
import urllib.request


def request(method, url, payload=None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    call = urllib.request.Request(
        url,
        data=body,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(call, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} returned {error.code}: {detail}") from error


def create_targets(base_url, boards):
    existing = request("GET", f"{base_url}/api/v1/ingestion-targets")
    by_account = {
        target["sourceAccount"]: target
        for target in existing
        if target["source"] == "GREENHOUSE"
    }
    targets = []
    for number in range(boards):
        account = f"benchmark-{number}"
        target = by_account.get(account)
        if target is None:
            target = request(
                "POST",
                f"{base_url}/api/v1/ingestion-targets",
                {
                    "source": "GREENHOUSE",
                    "sourceAccount": account,
                    "company": f"Benchmark Company {number}",
                    "careersUrl": f"https://example.com/{account}",
                    "intervalMinutes": 1440,
                },
            )
        target = request(
            "PATCH",
            f"{base_url}/api/v1/admin/targets/{target['id']}",
            {"enabled": True, "intervalMinutes": 1440},
        )
        targets.append(target)
    return targets


def run_and_wait(base_url, targets, poll_seconds, timeout_seconds):
    started_at = time.time()
    requests = [
        request("POST", f"{base_url}/api/v1/admin/targets/{target['id']}/run")
        for target in targets
    ]
    pending = {item["id"] for item in requests}
    completed = {}
    deadline = time.monotonic() + timeout_seconds
    while pending and time.monotonic() < deadline:
        for request_id in list(pending):
            state = request(
                "GET", f"{base_url}/api/v1/ingestion-requests/{request_id}"
            )
            if state["status"] in {"SUCCEEDED", "FAILED"}:
                pending.remove(request_id)
                completed[request_id] = state
        if pending:
            print(f"Waiting: {len(pending)} request(s) remain", file=sys.stderr)
            time.sleep(poll_seconds)

    if pending:
        raise TimeoutError(f"Timed out with {len(pending)} unfinished request(s)")
    elapsed = time.time() - started_at
    succeeded = sum(state["status"] == "SUCCEEDED" for state in completed.values())
    failed = len(completed) - succeeded
    return elapsed, succeeded, failed


def main():
    parser = argparse.ArgumentParser(description="Run the Jobpulse ingestion benchmark")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--boards", type=int, default=50)
    parser.add_argument("--poll-seconds", type=float, default=2)
    parser.add_argument("--timeout-seconds", type=int, default=1800)
    parser.add_argument(
        "--prepare-only",
        action="store_true",
        help="Create/reschedule targets without submitting ingestion requests",
    )
    args = parser.parse_args()

    targets = create_targets(args.base_url.rstrip("/"), args.boards)
    print(f"Created or reused {len(targets)} benchmark targets", file=sys.stderr)
    if args.prepare_only:
        return
    elapsed, succeeded, failed = run_and_wait(
        args.base_url.rstrip("/"),
        targets,
        args.poll_seconds,
        args.timeout_seconds,
    )
    postings = succeeded * 1000
    print(json.dumps({
        "boards": len(targets),
        "successfulRequests": succeeded,
        "failedRequests": failed,
        "expectedPostingsProcessed": postings,
        "elapsedSeconds": round(elapsed, 3),
        "expectedPostingsPerSecond": round(postings / elapsed, 3),
    }, indent=2))


if __name__ == "__main__":
    main()
