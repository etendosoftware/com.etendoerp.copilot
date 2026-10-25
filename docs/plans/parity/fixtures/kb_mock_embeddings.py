import hashlib
import json
import math
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIMENSIONS_FILE = os.environ.get("KB_MOCK_DIMENSIONS_FILE", "/tmp/kb-mock-dimensions")


def embed(text, dimensions):
    digest = hashlib.sha256(text.encode("utf-8")).digest()
    values = []
    counter = 0
    while len(values) < dimensions:
        block = hashlib.sha256(digest + counter.to_bytes(8, "big")).digest()
        values.extend((byte / 127.5) - 1.0 for byte in block)
        counter += 1
    values = values[:dimensions]
    norm = math.sqrt(sum(value * value for value in values)) or 1.0
    return [value / norm for value in values]


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path.rstrip("/") != "/v1/embeddings":
            self.send_error(404)
            return
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))))
            with open(DIMENSIONS_FILE, encoding="ascii") as stream:
                dimensions = int(stream.read().strip())
            inputs = body.get("input", [])
            if isinstance(inputs, str):
                inputs = [inputs]
            data = [
                {"object": "embedding", "index": index, "embedding": embed(str(text), dimensions)}
                for index, text in enumerate(inputs)
            ]
            response = json.dumps(
                {"object": "list", "data": data, "model": body.get("model", "mock")}
            ).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(response)))
            self.end_headers()
            self.wfile.write(response)
        except Exception as error:
            payload = json.dumps({"error": str(error)}).encode()
            self.send_response(500)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

    def log_message(self, format, *args):
        print(format % args, flush=True)


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(os.environ.get("PORT", "8765"))), Handler).serve_forever()
