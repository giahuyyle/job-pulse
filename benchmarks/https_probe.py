"""Low-rate public HTTPS timings; includes DNS, TLS, network and response body.

Only GET requests. This measures one client's experience, not server capacity.
"""
import argparse
import json
import platform
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

from metrics import quantiles


def probe(base_url, path):
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(base_url + path, timeout=10) as response:
            body = response.read()
            payload = json.loads(body)
            if not isinstance(payload.get('content'), list):
                raise ValueError('Expected a paginated jobs response')
            return {'status': response.status, 'elapsed_ms': (time.perf_counter() - started) * 1000,
                    'bytes': len(body), 'total_elements': payload.get('totalElements'),
                    'returned_jobs': len(payload['content'])}
    except Exception as error:
        return {'status': getattr(error, 'code', None),
                'elapsed_ms': (time.perf_counter() - started) * 1000,
                'error': str(error)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='https://jobpulse.page')
    parser.add_argument('--samples', type=int, default=20)
    parser.add_argument('--interval', type=float, default=1)
    parser.add_argument('--output', type=Path, default=Path(__file__).parent / 'results/https-live.json')
    args = parser.parse_args()
    if not 5 <= args.samples <= 100 or args.interval < .5:
        parser.error('Use 5–100 samples and at least 0.5 seconds between requests')
    if urlsplit(args.base_url).scheme != 'https':
        parser.error('Use an HTTPS base URL')
    paths = ['/api/v1/jobs?size=20&sort=newest',
             '/api/v1/jobs?query=backend%20engineer&size=20&sort=relevance']
    result = {'started_at': datetime.now(timezone.utc).isoformat(), 'host': platform.platform(),
              'base_url': args.base_url, 'interval_seconds': args.interval,
              'definition': 'Sequential urllib requests including DNS, fresh TLS and body download; warm-ups excluded',
              'warmups': [], 'endpoints': {path: {'raw': []} for path in paths}}
    for path in paths:
        result['warmups'].append(probe(args.base_url.rstrip('/'), path))
        time.sleep(args.interval)
    # Interleave endpoints to reduce time-of-run bias.
    for _ in range(args.samples):
        for path in paths:
            result['endpoints'][path]['raw'].append(probe(args.base_url.rstrip('/'), path))
            time.sleep(args.interval)
    for endpoint in result['endpoints'].values():
        samples = endpoint['raw']
        successful = [sample['elapsed_ms'] for sample in samples if sample.get('status') == 200 and 'error' not in sample]
        endpoint['failures'] = len(samples) - len(successful)
        endpoint['success_latency_ms'] = quantiles(successful) if successful else None
    result['finished_at'] = datetime.now(timezone.utc).isoformat()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({path: {k: v for k, v in data.items() if k != 'raw'}
                      for path, data in result['endpoints'].items()}, indent=2))
    if any(data['failures'] for data in result['endpoints'].values()):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
