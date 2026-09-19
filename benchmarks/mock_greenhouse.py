import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

JOBS_PER_BOARD = 1000
BOARD_PATH = re.compile(r"^/v1/boards/([A-Za-z0-9_-]+)$")
JOBS_PATH = re.compile(r"^/v1/boards/([A-Za-z0-9_-]+)/jobs$")


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = urlsplit(self.path).path
        jobs_match = JOBS_PATH.fullmatch(path)
        if jobs_match:
            self._write_jobs(jobs_match.group(1))
            return

        board_match = BOARD_PATH.fullmatch(path)
        if board_match:
            self._write_json({"name": f"Benchmark {board_match.group(1)}"})
            return

        self.send_error(404)

    def _write_jobs(self, board):
        jobs = [
            {
                "id": number + 1,
                "absolute_url": f"https://example.com/{board}/jobs/{number + 1}",
                "title": f"Backend Engineer {number + 1}",
                "location": {"name": "Remote, Canada"},
                "content": (
                    "Build distributed backend systems using Java, Spring Boot, "
                    "PostgreSQL, RabbitMQ, and Kafka."
                ),
            }
            for number in range(JOBS_PER_BOARD)
        ]
        self._write_json({"jobs": jobs})

    def _write_json(self, payload):
        body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        return


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8090), Handler).serve_forever()
